/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2015, 2024 B. Malinowsky

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

    Linking this library statically or dynamically with other modules is
    making a combined work based on this library. Thus, the terms and
    conditions of the GNU General Public License cover the whole
    combination.

    As a special exception, the copyright holders of this library give you
    permission to link this library with independent modules to produce an
    executable, regardless of the license terms of these independent
    modules, and to copy and distribute the resulting executable under terms
    of your choice, provided that you also meet, for each linked independent
    module, the terms and conditions of the license of that module. An
    independent module is a module which is not derived from or based on
    this library. If you modify this library, you may extend this exception
    to your version of the library, but you are not obligated to do so. If
    you do not wish to do so, delete this exception statement from your
    version.
*/

package tuwien.auto.calimero.gui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.DeviceDescriptor.DD0;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXIllegalArgumentException;
import tuwien.auto.calimero.internal.Executor;
import tuwien.auto.calimero.tools.ScanDevices;

/**
 * @author B. Malinowsky
 */
class ScanDevicesTab extends BaseTabLayout
{
	private Button cancel;
	private Thread worker;

	ScanDevicesTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "Scan subnet " + args.friendlyName(), "Scanning subnet", false, args);

		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(30);
		final TableColumn addr = new TableColumn(list, SWT.RIGHT);
		addr.setText("Existing device address");
		addr.setWidth(180);
		final TableColumn dd = new TableColumn(list, SWT.RIGHT);
		dd.setText("Device descriptor");
		dd.setWidth(130);

		list.addSelectionListener(defaultSelected(e -> {
				final TableItem row = list.getSelection()[0];
				final String device = row.getText(1);
				asyncAddLog("Read device information of KNX device " + device);
				connect.remoteKnxAddress = device;
				new DeviceInfoTab(tf, connect);
			}));

		scanDevices();
	}

	@Override
	protected void initWorkAreaTop() {
		super.initWorkAreaTop();
		addCancelButton();
	}

	private void addCancelButton() {
		((GridLayout) top.getLayout()).numColumns = 3;
		cancel = new Button(top, SWT.NONE);
		cancel.setFont(Main.font);
		cancel.setText("Cancel");
		cancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				if (worker != null) {
					worker.interrupt();
					asyncAddLog("Canceled device scan");
				}
			}
		});
	}

	@Override
	protected void onDispose(final DisposeEvent e) {
		if (worker != null)
			worker.interrupt();
	}

	private void scanDevices()
	{
		final List<String> args = new ArrayList<>(connect.getArgs(false));
		asyncAddLog("Using command line: " + String.join(" ", args));

		try {
			final ScanDevices config = new ScanDevices(args.toArray(new String[0])) {
				@Override
				protected void onDeviceFound(final IndividualAddress device)
				{
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;
						list.setToolTipText("Select an address to read KNX device information");
						final TableItem i = new TableItem(list, SWT.NONE);
						i.setText(new String[] { "" + list.getItemCount(), device.toString() });
					});
				}

				@Override
				protected void onDeviceFound(final IndividualAddress device, final DD0 dd0) {
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;
						for (final var item : list.getItems()) {
							if (device.toString().equals(item.getText(1))) {
								item.setText(2, dd0.toString());
								return;
							}
						}

						final TableItem i = new TableItem(list, SWT.NONE);
						i.setText(new String[] { "" + list.getItemCount(), device.toString(), dd0.toString() });
					});
				}

				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled) {
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;

						if (thrown != null) {
							final TableItem i = new TableItem(list, SWT.NONE);
							i.setText("Error: " + thrown.getMessage());

							asyncAddLog(thrown);
						}

						final String status = canceled ? "canceled" : "completed";
						setHeaderInfo(connectInfo("Device scan " + status + " for", false)
								+ " (select an address to read KNX device info)");
						cancel.setEnabled(false);
					});
				}
			};
			worker = Executor.execute(config, "Calimero device scan");
		}
		catch (final KNXIllegalArgumentException e) {
			asyncAddLog("error: " + e.getMessage());
		}
	}
}

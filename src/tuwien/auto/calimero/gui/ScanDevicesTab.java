/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2015, 2018 B. Malinowsky

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
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXIllegalArgumentException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.tools.ScanDevices;

/**
 * @author B. Malinowsky
 */
class ScanDevicesTab extends BaseTabLayout
{
	private final ConnectArguments connect;

	ScanDevicesTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "Scan subnet " + uniqueId(args), headerInfo(args, "Scanning subnet ..."));
		connect = args;

		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(30);
		final TableColumn pid = new TableColumn(list, SWT.LEFT);
		pid.setText("Existing KNX device address");
		pid.setWidth(180);

		list.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{}

			@Override
			public void widgetDefaultSelected(final SelectionEvent e)
			{
				final TableItem row = list.getSelection()[0];
				final String device = row.getText(1);
				asyncAddLog("Read device information of KNX device " + device);
				connect.knxAddress = device;
				new DeviceInfoTab(tf, connect);
			}
		});

		scanDevices();
	}

	private void scanDevices()
	{
		final List<String> args = new ArrayList<String>();
		args.add("--verbose");
		args.addAll(connect.getArgs(false));
		asyncAddLog("Using command line: " + String.join(" ", args));

		try {
			final ScanDevices config = new ScanDevices(args.toArray(new String[0])) {
				@Override
				protected void onDeviceFound(final IndividualAddress device)
				{
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;
						list.setToolTipText("Select an address to read the KNX device information");
						final TableItem i = new TableItem(list, SWT.NONE);
						i.setText(new String[] { "" + list.getItemCount(), device.toString() });
					});
				}

				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled)
				{
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;

						if (thrown != null) {
							final TableItem i = new TableItem(list, SWT.NONE);
							i.setText("Error: " + thrown.getMessage());

							asyncAddLog(thrown);
						}

						final String status = canceled ? "canceled" : "completed";
						setHeaderInfo(headerInfo(connect, "Device scan " + status + " for")
								+ " (select a found device for reading KNX device info)");
					});
				}
			};
			new Thread(config).start();
		}
		catch (final KNXIllegalArgumentException e) {
			asyncAddLog("error: " + e.getMessage());
		}
	}
}

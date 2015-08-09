/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2015 B. Malinowsky

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
import tuwien.auto.calimero.tools.ScanDevices;

/**
 * @author B. Malinowsky
 */
class ScanDevicesTab extends BaseTabLayout
{
	ScanDevicesTab(final CTabFolder tf, final String name, final String localhost, final String host,
		final String port, final boolean useNAT, final String knxAddr)
	{
		super(tf, "Scan devices of subnet " + (knxAddr.isEmpty() ? "" : knxAddr),
				"Scanning ... Using connection " + host + " port " + port
						+ (useNAT ? ", using NAT" : ""));
		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(30);
		final TableColumn pid = new TableColumn(list, SWT.LEFT);
		pid.setText("Existing KNX device address");
		pid.setWidth(180);

		list.addSelectionListener(new SelectionListener() {
			public void widgetSelected(final SelectionEvent e) {}

			public void widgetDefaultSelected(final SelectionEvent e)
			{
				final TableItem row = list.getSelection()[0];
				final String device = row.getText(1);
				asyncAddLog("Read device information of KNX device " + device);
				new DeviceInfoTab(tf, name, localhost, host, port, useNAT, device);
			}
		});

		scanDevices(localhost, host, port, useNAT, knxAddr);
	}

	private void scanDevices(final String localhost, final String host, final String port,
		final boolean useNAT, final String knxAddr)
	{
		list.removeAll();
		log.removeAll();
		final List<String> args = new ArrayList<String>();
		if (!host.isEmpty()) {
			if (!localhost.isEmpty()) {
				args.add("--localhost");
				args.add(localhost);
			}
			args.add(host);
			if (useNAT)
				args.add("--nat");
			if (!port.isEmpty()) {
				args.add("-p");
				args.add(port);
			}
		}
		else if (!port.isEmpty()) {
			args.add("-s");
			args.add(port);
		}

		args.add(knxAddr);
		try {
			final ScanDevices config = new ScanDevices(args.toArray(new String[0]))
			{
				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled,
					final IndividualAddress[] devices)
				{
					Main.asyncExec(new Runnable()
					{
						public void run()
						{
							if (list.isDisposed())
								return;
							list.setRedraw(false);
							list.setToolTipText("Select an address to read the "
									+ "KNX device information");
							long eventCounter = 0;
							for (final IndividualAddress d : devices) {
								final TableItem i = new TableItem(list, SWT.NONE);
								i.setText(new String[] { "" + ++eventCounter, d.toString() });
							}
							list.setRedraw(true);

							final String status = canceled ? "canceled" : "complete";
							if (thrown != null) {
								final TableItem i = new TableItem(list, SWT.NONE);
								i.setText("Error: " + thrown.getMessage());
							}

							setHeaderInfo("Scan devices " + status + ", connection "
									+ host + " port " + port + (useNAT ? ", using NAT" : ""));
						}
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

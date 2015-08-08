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
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.exception.KNXIllegalArgumentException;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.log.LogService;
import tuwien.auto.calimero.tools.DeviceInfo;

/**
 * @author B. Malinowsky
 */
class DeviceInfoTab extends BaseTabLayout
{
	DeviceInfoTab(final CTabFolder tf, final String name, final String localhost, final String host,
		final String port, final boolean useNAT, final String knxAddr)
	{
		super(tf, "Device information of " + (knxAddr.isEmpty() ? "" : knxAddr),
				"Read information using connection " + host + " port " + port
			+ (useNAT ? ", using NAT" : ""));
		final TableColumn pid = new TableColumn(list, SWT.LEFT);
		pid.setText("Setting");
		pid.setWidth(100);
		final TableColumn pidName = new TableColumn(list, SWT.LEFT);
		pidName.setText("Value");
		pidName.setWidth(200);
		enableColumnAdjusting();

		readDeviceInfo(localhost, host, port, useNAT, knxAddr);
	}

	private void readDeviceInfo(final String localhost, final String host, final String port,
		final boolean useNAT, final String knxAddr)
	{
		list.removeAll();
		log.removeAll();
		final List<String> args = new ArrayList<String>();
		if (!host.isEmpty()) {
			if (!localhost.isEmpty()) {
				args.add("-localhost");
				args.add(localhost);
			}
			args.add(host);
			if (useNAT)
				args.add("-nat");
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

		final LogService logService = LogManager.getManager().getLogService("tools");
		logService.removeWriter(logWriter);
		logService.addWriter(logWriter);
		try {
			final DeviceInfo config = new DeviceInfo(args.toArray(new String[0]))
			{
				@Override
				protected void onDeviceInformation(final IndividualAddress device, final String info)
				{
					Main.asyncExec(new Runnable()
					{
						public void run()
						{
							if (list.isDisposed())
								return;
							list.setRedraw(false);
							final List<String> items = Arrays.asList(info.split("\n"));
							for (final String s : items) {
								final TableItem i = new TableItem(list, SWT.NONE);
								// try to divide string in setting and value
								int div = s.lastIndexOf(':');
								if (div == -1)
									div = s.lastIndexOf(' ');
								if (div == -1)
									i.setText(new String[]{s});
								else {
									final String param = s.substring(0, div);
									final String value = s.substring(div + 1).trim();
									i.setText(new String[]{param, value});
								}
							}
							list.setRedraw(true);

							setHeaderInfo("Device information received from " + device + " over "
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

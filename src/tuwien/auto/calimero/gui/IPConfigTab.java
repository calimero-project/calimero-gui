/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006-2012 B. Malinowsky

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
*/

package tuwien.auto.calimero.gui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.exception.KNXIllegalArgumentException;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.log.LogService;
import tuwien.auto.calimero.tools.IPConfig;

/**
 * @author B. Malinowsky
 */
class IPConfigTab extends BaseTabLayout
{
	IPConfigTab(final CTabFolder tf, final String name, final String host,
		final String port, final boolean useNAT, final String knxAddr)
	{
		super(tf, "KNXnet/IP configuration for " + name, "Query configuration from "
			+ (knxAddr.isEmpty() ? "" : knxAddr + " over ") + host + " port " + port
			+ (useNAT ? ", using NAT" : ""));
		final TableColumn pid = new TableColumn(list, SWT.LEFT);
		pid.setText("Property ID");
		pid.setWidth(100);
		final TableColumn pidName = new TableColumn(list, SWT.LEFT);
		pidName.setText("Name");
		pidName.setWidth(200);
		final TableColumn value = new TableColumn(list, SWT.LEFT);
		value.setText("Value");
		value.setWidth(200);
		enableColumnAdjusting();
		
		readConfig(host, port, useNAT, knxAddr);
	}

	private void readConfig(final String host, final String port, final boolean useNAT,
		final String knxAddr)
	{
		list.removeAll();
		log.removeAll();
		final List<String> args = new ArrayList<String>();
		if (!host.isEmpty()) {
			if (!Main.getLocalHost().isEmpty()) {
				args.add("-localhost");
				args.add(Main.getLocalHost());
			}
			args.add(host);
			if (knxAddr.isEmpty())
				args.add("-l");
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

		if (!knxAddr.isEmpty()) {
			args.add("-r");
			args.add(knxAddr);
		}
		
		final LogService logService = LogManager.getManager().getLogService("tools");
		logService.removeWriter(logWriter);
		logService.addWriter(logWriter);
		try {
			final IPConfig config = new IPConfig(args.toArray(new String[0]))
			{
				@Override
				protected void onConfigurationReceived(final List config)
				{
					Main.asyncExec(new Runnable()
					{
						public void run()
						{
							if (list.isDisposed())
								return;
							list.setRedraw(false);
							for (final String[] s : (List<String[]>) config) {
								final TableItem i = new TableItem(list, SWT.NONE);
								i.setText(s);
							}
							list.setRedraw(true);
							
							setHeaderInfo("Configuration received from "
									+ (knxAddr.isEmpty() ? "" : knxAddr + " over ") + host
									+ " port " + port + (useNAT ? ", using NAT" : ""));
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

/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2015, 2017 B. Malinowsky

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
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXIllegalArgumentException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.tools.DeviceInfo;

/**
 * @author B. Malinowsky
 */
class DeviceInfoTab extends BaseTabLayout
{
	private final ConnectArguments connect;

	DeviceInfoTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "Device information of " + args.knxAddress, "Read information using connection "
				+ args.remote + " port " + args.port + (args.useNat() ? ", using NAT" : ""));
		connect = args;

		final TableColumn pid = new TableColumn(list, SWT.LEFT);
		pid.setText("Setting");
		pid.setWidth(100);
		final TableColumn pidName = new TableColumn(list, SWT.LEFT);
		pidName.setText("Value");
		pidName.setWidth(200);
		final TableColumn raw = new TableColumn(list, SWT.LEFT);
		raw.setText("Unformatted");
		raw.setWidth(80);
		enableColumnAdjusting();

		readDeviceInfo();
	}

	private void readDeviceInfo()
	{
		list.removeAll();
		log.removeAll();
		final List<String> args = new ArrayList<String>();
		args.addAll(connect.getArgs(false));
		asyncAddLog("Using command line: " + String.join(" ", args));

		try {
			final DeviceInfo config = new DeviceInfo(args.toArray(new String[0])) {
				@Override
				protected void onDeviceInformation(final IndividualAddress device, final Result result)
				{
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;
						list.setRedraw(false);
						addItems(result, CommonParameter.values());
						addItems(result, KnxipParameter.values());
						list.setRedraw(true);
						setHeaderInfo("Device information received from " + device + " over " + connect.remote
								+ " port " + connect.port + (connect.useNat() ? ", using NAT" : ""));
					});
				}

				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled)
				{
					if (thrown != null)
						asyncAddLog(thrown.toString());
				}

				private void addItems(final Result result, final Parameter[] params)
				{
					for (final Parameter p : params) {
						final String s = result.formatted(p);
						if (s != null) {
							final TableItem i = new TableItem(list, SWT.NONE);
							final String param = p.name().replaceAll("([A-Z])", " $1").replace("I P", "IP").trim();
							final String raw = result.raw(p).map((v) -> v.toString()).orElse("n/a");
							i.setText(new String[] { param, result.formatted(p), raw });
						}
					}
				}
			};
			new Thread(config).start();
		}
		catch (final KNXIllegalArgumentException e) {
			asyncAddLog("error: " + e.getMessage());
		}
	}
}

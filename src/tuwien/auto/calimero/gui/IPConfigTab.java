/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2022 B. Malinowsky

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
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.KNXIllegalArgumentException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.internal.Executor;
import tuwien.auto.calimero.tools.IPConfig;

/**
 * @author B. Malinowsky
 */
class IPConfigTab extends BaseTabLayout
{
	private final ConnectArguments connect;

	IPConfigTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "KNX IP configuration of " + args.name, headerInfo(adjustPreferRoutingConfig(args), "Connecting to"));
		connect = args;

		final TableColumn pid = new TableColumn(list, SWT.LEFT);
		pid.setText("Property ID");
		pid.setWidth(50);
		final TableColumn pidName = new TableColumn(list, SWT.LEFT);
		pidName.setText("Name");
		pidName.setWidth(150);
		final TableColumn value = new TableColumn(list, SWT.LEFT);
		value.setText("Value");
		value.setWidth(200);
		enableColumnAdjusting();

		final String filter = args.remote == null ? args.port : args.remote;
		addLogIncludeFilter(".*" + Pattern.quote(filter) + ".*", ".*calimero\\.mgmt\\.PC.*", ".*calimero\\.tools.*");
		addLogExcludeFilter(".*Discoverer.*");

		readConfig();
	}

	private void readConfig()
	{
		final List<String> args = new ArrayList<String>();
		args.addAll(connect.getArgs(true));
		asyncAddLog("Using command line: " + String.join(" ", args));

		try {
			final IPConfig config = new IPConfig(args.toArray(new String[0])) {
				@Override
				public void run() {
					Main.asyncExec(() -> setHeaderInfo(headerInfo(connect, "Query configuration from")));
					super.run();
				}

				@Override
				protected void onConfigurationReceived(final List<String[]> config)
				{
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;
						list.setRedraw(false);
						for (final String[] s : config) {
							if (s[2].isEmpty())
								s[2] = "--";
							final TableItem i = new TableItem(list, SWT.NONE);
							i.setText(s);
						}
						list.setRedraw(true);

						setHeaderInfo(headerInfo(connect, "Configuration received from"));
					});
				}
			};
			Executor.execute(config);
		}
		catch (final KNXIllegalArgumentException e) {
			asyncAddLog("error: " + e.getMessage());
		}
	}
}

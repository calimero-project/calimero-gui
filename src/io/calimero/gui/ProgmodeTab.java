/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2021 B. Malinowsky

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

package io.calimero.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import io.calimero.IndividualAddress;
import io.calimero.KNXIllegalArgumentException;
import io.calimero.gui.ConnectDialog.ConnectArguments;
import io.calimero.tools.ProgMode;

/**
 * Lists the KNX devices currently in programming mode.
 */
class ProgmodeTab extends BaseTabLayout
{
	private final ConnectArguments connect;
	private Thread task;

	ProgmodeTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "In Programming Mode ", "Connect to " + connectionInfo(args));
		connect = args;

		final TableColumn device = new TableColumn(list, SWT.LEFT);
		device.setText("Device Address");
		device.setWidth(100);
		enableColumnAdjusting();

		final String filter = args.remote == null ? args.port : args.remote;
		addLogIncludeFilter(".*" + Pattern.quote(filter) + ".*", ".*calimero\\.mgmt\\.MgmtProc.*",
				".*calimero\\.mgmt\\.MC.*", ".*calimero\\.tools.*");
		addLogExcludeFilter(".*Discoverer.*");

		runProgmode();
	}

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (task != null)
			task.interrupt();
	}

	private void runProgmode()
	{
		final List<String> args = new ArrayList<String>();
		args.addAll(connect.getArgs(true));
		asyncAddLog("Using command line: " + String.join(" ", args));

		try {
			task = new Thread(new ProgMode(args.toArray(new String[0])) {
				@Override
				protected void devicesInProgMode(final IndividualAddress... devices) {
					Main.asyncExec(() -> updateDevices(devices));
				}
			});
			task.start();
		}
		catch (final KNXIllegalArgumentException e) {
			asyncAddLog("error: " + e.getMessage());
		}
	}

	private void updateDevices(final IndividualAddress... devices)
	{
		if (list.isDisposed())
			return;
		list.removeAll();

		for (final IndividualAddress addr : new TreeSet<>(Arrays.asList(devices)))
			new TableItem(list, SWT.NONE).setText(addr.toString());

		setHeaderInfo("Connected to " + connectionInfo(connect));
	}

	private static String connectionInfo(final ConnectArguments connect) {
		return connect.name + ", " + connect.remote + " port " + connect.port + (connect.useNat() ? ", using NAT" : "");
	}
}

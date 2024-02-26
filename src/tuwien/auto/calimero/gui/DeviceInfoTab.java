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
import java.util.HexFormat;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.KNXIllegalArgumentException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.internal.Executor;
import tuwien.auto.calimero.tools.DeviceInfo;

/**
 * @author B. Malinowsky
 */
class DeviceInfoTab extends BaseTabLayout
{
	private Thread worker;

	DeviceInfoTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "Device info of " + args.friendlyName(), "Read info of", true, args);

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

	@Override
	protected void onDispose(final DisposeEvent e) {
		if (worker != null)
			worker.interrupt();
	}

	private void readDeviceInfo()
	{
		list.removeAll();
		log.removeAll();
		final List<String> args = new ArrayList<>(connect.getArgs(false));
		// remove knx medium if we do local device info
		if (connect.remoteKnxAddress.isEmpty()) {
			for (int i = 0; i < args.size() - 1; i++) {
				if ("--medium".equals(args.get(i))) {
					args.remove(i); // --medium
					args.remove(i); // <medium>
					break;
				}
			}
		}
		asyncAddLog("Using command line: " + String.join(" ", args));

		try {
			final DeviceInfo config = new DeviceInfo(args.toArray(new String[0])) {
				@Override
				protected void onDeviceInformation(final Item item) {
					Main.asyncExec(() -> {
						if (!list.isDisposed())
							addItem(item);
					});
				}

				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled) {
					Main.asyncExec(() -> {
						if (list.isDisposed())
							return;
						final String status = canceled ? "canceled" : "completed";
						setHeaderInfoPhase("Device info " + status + " for");
					});
					if (thrown != null)
						asyncAddLog(thrown);
				}

				private String currentCategory = "";

				private void addItem(final Item item) {
					if (!currentCategory.equals(item.category())) {
						currentCategory = item.category();
						final TableItem i = new TableItem(list, SWT.NONE);
						i.setText(new String[] { currentCategory, "", "" });
					}
					final String param = item.parameter().friendlyName();
					final String rawString = HexFormat.of().formatHex(item.raw());
					final TableItem i = new TableItem(list, SWT.NONE);
					i.setText(new String[] { "\t" + param, item.value(), rawString });
				}
			};
			worker = Executor.execute(config, "Info reader " + connect.friendlyName());
		}
		catch (final KNXIllegalArgumentException e) {
			asyncAddLog("error: " + e.getMessage());
		}
	}
}

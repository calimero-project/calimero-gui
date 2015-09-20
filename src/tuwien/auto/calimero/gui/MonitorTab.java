/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2016 B. Malinowsky

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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.TableColumn;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.cemi.CEMIBusMon;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.link.MonitorFrameEvent;
import tuwien.auto.calimero.link.medium.RawFrame;
import tuwien.auto.calimero.link.medium.RawFrameBase;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.tools.NetworkMonitor;

/**
 * @author B. Malinowsky
 */
class MonitorTab extends BaseTabLayout
{
	private NetworkMonitor m;
	private long eventCounter;
	private long eventCounterFiltered = 1;
	private final ConnectArguments connect;

	MonitorTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "Monitor for " + args.name, "Open monitor"
				+ (args.remote == null ? "" : " on host " + args.remote) + " on port " + args.port
				+ (args.useNat() ? ", using NAT" : ""));
		connect = args;

		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(30);
		final TableColumn cntf = new TableColumn(list, SWT.RIGHT);
		cntf.setText("# (Filtered)");
		cntf.setWidth(40);
		final TableColumn timestamp = new TableColumn(list, SWT.RIGHT);
		timestamp.setText("Timestamp");
		timestamp.setWidth(80);
		final TableColumn status = new TableColumn(list, SWT.LEFT);
		status.setText("Status / Sequence");
		status.setWidth(150);
		final TableColumn raw = new TableColumn(list, SWT.LEFT);
		raw.setText("Raw frame");
		raw.setWidth(150);
		final TableColumn decoded = new TableColumn(list, SWT.LEFT);
		decoded.setText("Decoded frame");
		decoded.setWidth(200);
		final TableColumn asdu = new TableColumn(list, SWT.LEFT);
		asdu.setText("TPCI / APCI");
		asdu.setWidth(100);
		enableColumnAdjusting();

		initFilterMenu();

		startMonitor();
	}

	private void startMonitor()
	{
		final java.util.List<String> args = new ArrayList<String>();
		args.add("-verbose");
		args.addAll(connect.getArgs(true));
		asyncAddLog("Using command line: " + String.join(" ", args));

		final class Monitor extends NetworkMonitor
		{
			Monitor(final String[] args)
			{
				super(args);
				LogManager.getManager().addWriter("tools", logWriter);
			}

			@Override
			public void start() throws KNXException, InterruptedException
			{
				super.start();
				Main.asyncExec(new Runnable()
				{
					public void run()
					{
						setHeaderInfo("Monitoring" + (connect.remote.isEmpty() ? "" : " on host " + connect.remote)
								+ " on port " + connect.port + (connect.useNat() ? ", using NAT" : ""));
					}
				});
			}

			@Override
			protected void onCompletion(final Exception thrown, final boolean canceled)
			{
				if (thrown != null)
					asyncAddLog("error: " + thrown.getMessage());
				asyncAddLog("network monitor closed " + (canceled ? "(canceled)" : ""));
				LogManager.getManager().removeWriter("tools", logWriter);
			}

			@Override
			public void onIndication(final FrameEvent e)
			{
				final java.util.List<String> item = new ArrayList<String>();
				// monitor event counters
				item.add(Long.toString(++eventCounter));
				item.add(Long.toString(eventCounterFiltered));
				// timestamp
				item.add(Long.toString(((CEMIBusMon) e.getFrame()).getTimestamp()));
				final String s = e.getFrame().toString();
				// status / sequence
				final String status = "status ";
				final String rawFrame = "raw frame ";
				item.add(s.substring(s.indexOf(status), s.indexOf(rawFrame)));
				// raw frame
				item.add(s.substring(s.indexOf(rawFrame) + rawFrame.length()));
				final RawFrame raw = ((MonitorFrameEvent) e).getRawFrame();
				if (raw != null) {
					// decoded raw frame
					item.add(raw.toString());
					if (raw instanceof RawFrameBase) {
						final RawFrameBase f = (RawFrameBase) raw;
						// asdu
						item.add(DataUnitBuilder.decode(f.getTPDU(), f.getDestination()));
					}
				}
				final String[] sa = item.toArray(new String[0]);
				if (applyFilter(sa))
					return;
				// increment filtered counter after filter
				++eventCounterFiltered;
				asyncAddListItem(sa, null, null);
			}
		}

		try {
			m = new Monitor(args.toArray(new String[0]));
			new Thread(m).start();
		}
		catch (final RuntimeException e) {
			log.add("error: " + e.getMessage());
		}
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		addResetAndExport("_monitor.csv");
	}

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (m != null)
			m.quit();
	}
}

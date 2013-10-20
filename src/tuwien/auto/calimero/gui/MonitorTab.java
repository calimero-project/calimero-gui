/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006-2013 B. Malinowsky

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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.TableColumn;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.cemi.CEMIBusMon;
import tuwien.auto.calimero.exception.KNXException;
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

	MonitorTab(final CTabFolder tf, final String name, final String localhost, final String host,
		final String port, final boolean useNAT)
	{
		super(tf, "Monitor for " + name, "Open monitor"
			+ (host.isEmpty() ? "" : " on host " + host) + " on port " + port
			+ (useNAT ? ", using NAT" : ""));
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

		startMonitor(localhost, host, port, useNAT);
	}

	private void startMonitor(final String localhost, final String host, final String port,
		final boolean useNAT)
	{
		final java.util.List<String> args = new ArrayList<String>();
		if (!host.isEmpty()) {
			if (!localhost.isEmpty()) {
				args.add("-localhost");
				args.add(localhost);
			}
			args.add(host);
			if (useNAT)
				args.add("-nat");
			if (!port.isEmpty())
				args.add("-p");
		}
		else
			args.add("-s");
		args.add(port);

		list.removeAll();
		log.removeAll();

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
						setHeaderInfo("Monitoring" + (host.isEmpty() ? "" : " on host " + host)
								+ " on port " + port + (useNAT ? ", using NAT" : ""));
					}
				});
			}
			
			/* (non-Javadoc)
			 * @see tuwien.auto.calimero.tools.NetworkMonitor#onCompletion(
			 * java.lang.Exception, boolean)
			 */
			@Override
			protected void onCompletion(final Exception thrown, final boolean canceled)
			{
				if (thrown != null)
					asyncAddLog("error: " + thrown.getMessage());
				asyncAddLog("network monitor closed " + (canceled ? "(canceled)" : ""));
				LogManager.getManager().removeWriter("tools", logWriter);
			}

			/* (non-Javadoc)
			 * @see tuwien.auto.calimero.tools.NetworkMonitor#onIndication
			 * (tuwien.auto.calimero.FrameEvent)
			 */
			@Override
			public void onIndication(final FrameEvent e)
			{
				final java.util.List<String> item = new ArrayList<String>();
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
				asyncAddListItem(item.toArray(new String[0]), null, null);
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

	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#onDispose(
	 * org.eclipse.swt.events.DisposeEvent)
	 */
	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (m != null)
			m.quit();
	}
}

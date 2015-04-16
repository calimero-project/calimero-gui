/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2015 B. Malinowsky

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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.knxnetip.Discoverer;
import tuwien.auto.calimero.knxnetip.servicetype.SearchResponse;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.log.LogService;
import tuwien.auto.calimero.tools.Discover;

/**
 * @author B. Malinowsky
 */
class DiscoverTab extends BaseTabLayout
{
	private Button nat;

	DiscoverTab(final CTabFolder tf)
	{
		super(tf, "Endpoint discovery && description", null, false);
		new TableColumn(list, SWT.LEFT);

		list.setHeaderVisible(false);
		listItemMargin = 10;
		list.addListener(SWT.MeasureItem, new Listener()
		{
			public void handleEvent(final Event event)
			{
				final TableItem item = (TableItem) event.item;
				final String text = item.getText(event.index);
				final Point size = event.gc.textExtent(text);
				event.width = size.x + 2 * listItemMargin;
				event.height = Math.max(event.height, size.y + listItemMargin);
			}
		});
		list.addListener(SWT.EraseItem, new Listener()
		{
			public void handleEvent(final Event event)
			{
				event.detail &= ~SWT.FOREGROUND;
			}
		});
		list.addListener(SWT.PaintItem, new Listener()
		{
			public void handleEvent(final Event event)
			{
				final TableItem item = (TableItem) event.item;
				final String text = item.getText(event.index);
				event.gc.drawText(text, event.x + listItemMargin, event.y, true);
			}
		});
		setListBanner("\nFound endpoints of devices (KNXnet/IP routers only) will be "
			+ "listed here.\nSelect an endpoint to open the connection dialog.");
		enableColumnAdjusting();
	}

	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#createWorkAreaTop()
	 */
	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		((GridLayout) top.getLayout()).numColumns = 2;
		final Button start = new Button(top, SWT.PUSH);
		start.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		start.setText("Discover KNXnet/IP devices");
		start.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				discover();
			}
		});
		start.setFocus();

		nat = new Button(top, SWT.CHECK);
		nat.setText("Be aware of NAT (Network Address Translation) during search");
		nat.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		nat.setToolTipText("Some KNXnet/IP devices might not answer in this mode");
	}

	private void discover()
	{
		final java.util.List<String> args = new ArrayList<String>();
		args.add("-s");
		if (nat.getSelection())
			args.add("-nat");
		list.removeAll();
		log.removeAll();
		final LogService logService = LogManager.getManager().getLogService(Discoverer.LOG_SERVICE);
		logService.addWriter(logWriter);
		try {
			final Runnable r = new Discover(args.toArray(new String[0]))
			{
				@Override
				protected void onEndpointReceived(final SearchResponse r)
				{
					final String sep = "\n";
					final StringBuilder buf = new StringBuilder();
					buf.append("Control endpoint ");
					buf.append(r.getControlEndpoint().toString()).append(sep);
					buf.append(r.getDevice().toString()).append(sep);
					buf.append("Supported service families:").append(sep).append("    ");
					buf.append(r.getServiceFamilies().toString());
					for (int i = buf.indexOf(", "); i != -1; i = buf.indexOf(", "))
						buf.replace(i, i + 2, sep + "    ");
					String mcast = null;
					try {
						mcast = InetAddress.getByAddress(r.getDevice().getMulticastAddress())
								.getHostAddress();
					}
					catch (final UnknownHostException e) {}

					// only add the new item if it is different from any already shown in the list
					final String setMcast = mcast;
					Main.syncExec(new Runnable()
					{
						public void run()
						{
							final List<TableItem> items = new ArrayList<TableItem>();
							items.addAll(Arrays.asList(list.getItems()));
							for (final Iterator<TableItem> i = items.iterator(); i.hasNext();) {
								final TableItem item = i.next();
								if (item.getText().equals(buf.toString()))
									return;
							}
							addListItem(new String[] { buf.toString() }, new String[] { "name",
								"host", "port", "mcast" },
									new String[] { r.getDevice().getName(),
										r.getControlEndpoint().getAddress().getHostAddress(),
										Integer.toString(r.getControlEndpoint().getPort()),
										setMcast });
						}
					});
				}

				/* (non-Javadoc)
				 * @see tuwien.auto.calimero.tools.Discover
				 * #exceptionThrown(java.lang.Exception)
				 */
				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled)
				{
					if (thrown != null)
						asyncAddLog("error: " + thrown.getMessage());
					asyncAddLog("search finished");
					logService.removeWriter(logWriter);
				}
			};
			new Thread(r).start();
		}
		catch (final Exception e) {
			log.add("error: " + e.getMessage());
		}
	}

	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#listItemSelected(
	 * org.eclipse.swt.events.SelectionEvent)
	 */
	@Override
	protected void onListItemSelected(final SelectionEvent e)
	{
		final TableItem i = (TableItem) e.item;
		new ConnectDialog(getTabFolder(), (String) i.getData("name"), (String) i.getData("host"),
				(String) i.getData("port"), (String) i.getData("mcast"), nat.getSelection());
	}
}

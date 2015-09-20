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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;
import javax.usb.UsbDisconnectedException;
import javax.usb.UsbException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments.Protocol;
import tuwien.auto.calimero.knxnetip.Discoverer.Result;
import tuwien.auto.calimero.knxnetip.servicetype.SearchResponse;
import tuwien.auto.calimero.serial.usb.UsbConnection;
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
		list.addListener(SWT.MeasureItem, event -> {
			final TableItem item = (TableItem) event.item;
			final String text = item.getText(event.index);
			final Point size = event.gc.textExtent(text);
			event.width = size.x + 2 * listItemMargin;
			event.height = Math.max(event.height, size.y + listItemMargin);
		});
		list.addListener(SWT.EraseItem, event -> event.detail &= ~SWT.FOREGROUND);
		list.addListener(SWT.PaintItem, event -> {
			final TableItem item = (TableItem) event.item;
			final String text = item.getText(event.index);
			event.gc.drawText(text, event.x + listItemMargin, event.y, true);
		});
		setListBanner("\nFound device endpoints (KNXnet/IP routers and USB interfaces only) "
				+ "will be listed here.\nSelect an endpoint to open the connection dialog.");
		enableColumnAdjusting();
		setLogNamespace("calimero");
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		((GridLayout) top.getLayout()).numColumns = 2;
		final Button start = new Button(top, SWT.PUSH);
		start.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		start.setText("Discover devices");
		start.addSelectionListener(new SelectionAdapter() {
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
			args.add("--nat");
		asyncAddLog("Using command line: " + String.join(" ", args));
		list.removeAll();
		log.removeAll();
		try {
			final String sep = "\n";
			final Runnable r = new Discover(args.toArray(new String[0])) {
				@Override
				protected void onEndpointReceived(final Result<SearchResponse> result)
				{
					final StringBuilder buf = new StringBuilder();
					buf.append("Control endpoint ");
					final SearchResponse r = result.getResponse();
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
					Main.syncExec(new Runnable() {
						@Override
						public void run()
						{
							final List<TableItem> items = new ArrayList<TableItem>();
							items.addAll(Arrays.asList(list.getItems()));
							for (final Iterator<TableItem> i = items.iterator(); i.hasNext();) {
								final TableItem item = i.next();
								if (item.getText().equals(buf.toString()))
									return;
							}
							addListItem(new String[] { buf.toString() },
									new String[] { "protocol", "name", "host", "port", "mcast" },
									new Object[] { Protocol.Tunneling, r.getDevice().getName(),
										r.getControlEndpoint().getAddress().getHostAddress(),
										Integer.toString(r.getControlEndpoint().getPort()),
										setMcast });
						}
					});
				}

				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled)
				{
					if (thrown != null)
						asyncAddLog("error: " + thrown.getMessage());
					asyncAddLog("search finished");
				}
			};
			new Thread(r).start();

			final Runnable usb = new Runnable() {
				@Override
				public void run()
				{
					try {
						asyncAddLog("search for USB KNX interfaces");
						final List<UsbDevice> knxDevices = UsbConnection.getKnxDevices();

						final List<UsbDevice> vserialKnxDevices = UsbConnection
								.getVirtualSerialKnxDevices();

						Main.syncExec(new Runnable() {
							@Override
							public void run()
							{
								for (final UsbDevice d : knxDevices) {
									try {
										final String ind = "    ";
										final StringBuilder sb = new StringBuilder();
										sb.append("USB interface ").append(d).append(sep);
										sb.append(ind).append(d.getProductString()).append(sep);
										sb.append(ind).append(d.getManufacturerString());

										final UsbDeviceDescriptor dd = d.getUsbDeviceDescriptor();
										final int vendor = dd.idVendor() & 0xffff;
										final int prod = dd.idProduct() & 0xffff;
										final String vp = String.format("%04x:%04x", vendor, prod);
										addListItem(new String[] { sb.toString() },
												new String[] { "protocol", "name", "port", },
												new Object[] { Protocol.USB, d.getProductString(),
													vp });
									}
									catch (final UsbException | UnsupportedEncodingException
											| UsbDisconnectedException e) {
										asyncAddLog("error: " + e.getMessage());
									}
								}
								for (final UsbDevice d : vserialKnxDevices) {
									try {
										final String ind = "    ";
										final StringBuilder sb = new StringBuilder();
										sb.append("TP-UART interface ").append(d).append(sep);
										final String product = nullTerminate(d.getProductString());
										final String mf = nullTerminate(d.getManufacturerString());
										sb.append(ind).append(product).append(sep);
										sb.append(ind).append(mf);
										final String dev = "/dev/";

										addListItem(new String[] { sb.toString() },
												new String[] { "protocol", "name", "port", },
												new Object[] { Protocol.Tpuart,
													d.getProductString(), dev });
									}
									catch (final UsbException | UnsupportedEncodingException
											| UsbDisconnectedException e) {
										asyncAddLog("error: " + e.getMessage());
									}
								}
							}
						});
					}
					catch (final SecurityException | UsbDisconnectedException | UsbException e) {
						asyncAddLog("error: " + e.getMessage());
					}
				};

				// sometimes usb4java returns strings which exceed past the null terminator
				private String nullTerminate(final String s)
				{
					final int end = s.indexOf((char) 0);
					return end > -1 ? s.substring(0, end) : s;
				}
			};
			new Thread(usb).start();
		}
		catch (final Exception e) {
			log.add("error: " + e.getMessage());
		}
	}

	@Override
	protected void onListItemSelected(final SelectionEvent e)
	{
		final TableItem i = (TableItem) e.item;
		new ConnectDialog(getTabFolder(), (Protocol) i.getData("protocol"),
				(String) i.getData("name"), (String) i.getData("host"), (String) i.getData("port"),
				(String) i.getData("mcast"), nat.getSelection());
	}
}

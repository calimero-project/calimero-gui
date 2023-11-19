/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2023 B. Malinowsky

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

import static java.lang.System.Logger.Level.DEBUG;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.calimero.serial.usb.Device;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import io.calimero.IndividualAddress;
import io.calimero.KNXException;
import io.calimero.SerialNumber;
import io.calimero.gui.ConnectDialog.ConnectArguments;
import io.calimero.gui.ConnectDialog.ConnectArguments.Protocol;
import io.calimero.knxnetip.Discoverer.Result;
import io.calimero.knxnetip.servicetype.SearchResponse;
import io.calimero.knxnetip.util.DIB;
import io.calimero.knxnetip.util.ServiceFamiliesDIB;
import io.calimero.knxnetip.util.ServiceFamiliesDIB.ServiceFamily;
import io.calimero.link.medium.KNXMediumSettings;
import io.calimero.serial.SerialConnectionFactory;
import io.calimero.serial.usb.UsbConnection;
import io.calimero.serial.usb.UsbConnectionFactory;
import io.calimero.tools.Discover;

/**
 * @author B. Malinowsky
 */
class DiscoverTab extends BaseTabLayout
{
	private Button nat;
	Button preferRouting;
	Button preferTcp;

	DiscoverTab(final CTabFolder tf)
	{
		super(tf, "Endpoint discovery && description", null, false);
		final Composite parent = list.getParent();
		final int style = list.getStyle();
		final Sash bottom = (Sash) ((FormData) list.getLayoutData()).bottom.control;
		list.dispose();
		list = newTable(parent, style | SWT.CHECK, bottom);
		list.addSelectionListener(defaultSelected(e -> {
			if (e.item.getData("internal") == null)
				onListItemSelected(e);
		}));
		list.addSelectionListener(selected(e -> {
			if (e.detail == SWT.CHECK) {
				for (final TableItem ti : list.getItems())
					if (!ti.equals(e.item))
						ti.setChecked(false);
			}
		}));
		new TableColumn(list, SWT.LEFT);

		list.setHeaderVisible(false);
		listItemMargin = 5;
		list.addListener(SWT.MeasureItem, event -> {
			final TableItem item = (TableItem) event.item;
			final String text = item.getText(event.index);
			final Point size = event.gc.textExtent(text);
			event.width = size.x + 2 * listItemMargin;
			event.height = size.y + 2 * listItemMargin;
		});
		list.addListener(SWT.EraseItem, event -> event.detail &= ~SWT.FOREGROUND);
		list.addListener(SWT.PaintItem, event -> {
			final TableItem item = (TableItem) event.item;
			final String text = item.getText(event.index);
			event.gc.drawText(text, event.x + listItemMargin, event.y + listItemMargin, true);
		});
		setListBanner("KNXnet/IP servers and USB interfaces "
				+ "are listed here.\nSelect an interface to open the connection dialog.");
		enableColumnAdjusting();
		setLogLevel(DEBUG);
		discover();
	}

	Optional<ConnectArguments> defaultInterface()
	{
		final TableItem[] items = list.getItems();
		if (items.length == 0 || items[0].getData("internal") != null)
			return Optional.empty();

		final Optional<TableItem> item;
		if (items.length == 1)
			item = Optional.of(items[0]);
		else
			item = Arrays.stream(items).filter(TableItem::getChecked).findFirst();
		if (item.isEmpty())
			return Optional.empty();

		final TableItem defaultInterface = item.get();
		Protocol protocol = (Protocol) defaultInterface.getData("protocol");
		boolean tunneling = protocol == Protocol.Tunneling;
		if (tunneling && preferRouting.getSelection()) {
			final boolean routing = Optional.ofNullable((Boolean) defaultInterface.getData("supportsRouting")).orElse(false);
			if (routing) {
				tunneling = false;
				protocol = Protocol.Routing;
			}
		}

		final IndividualAddress host = (IndividualAddress) defaultInterface.getData("hostIA");
		final String remote = (String) (tunneling ? defaultInterface.getData("host") : defaultInterface.getData("mcast"));
		final ConnectArguments args = new ConnectArguments(protocol, (String) defaultInterface.getData("localEP"), remote,
				(String) defaultInterface.getData("port"), nat.getSelection(), preferTcp.getSelection(), host, "", "");
		args.serverIP = (String) defaultInterface.getData("host");
		args.name = (String) defaultInterface.getData("name");
		args.serverIA = Optional.ofNullable(defaultInterface.getData("hostIA")).map(Objects::toString).orElse("");
		args.knxMedium = Optional.ofNullable((Integer) defaultInterface.getData("medium")).orElse(KNXMediumSettings.MEDIUM_TP1);
		args.secureServices = secureServices(defaultInterface);
		args.serialNumber = (SerialNumber) defaultInterface.getData("SN");
		return Optional.of(args);
	}

	@SuppressWarnings("unchecked")
	private static Map<ServiceFamily, Integer> secureServices(final TableItem tableItem) {
		return (Map<ServiceFamily, Integer>) Optional.ofNullable(tableItem.getData("secure")).orElse(Map.of());
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		((GridLayout) top.getLayout()).numColumns = 4;
		final Button start = new Button(top, SWT.PUSH);
		start.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		start.setText("Discover devices");
		start.addSelectionListener(selected(e -> discover()));
		start.setFocus();

		nat = new Button(top, SWT.CHECK);
		nat.setText("Be aware of NAT (Network Address Translation) during search");
		nat.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		nat.setToolTipText("Some KNXnet/IP devices might not answer in this mode");

		preferRouting = new Button(top, SWT.CHECK);
		preferRouting.setText("Prefer KNX IP Routing");
		preferRouting.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		preferRouting.setToolTipText("If both Routing & Tunneling mode are supported, choose Routing");

		preferTcp = new Button(top, SWT.CHECK);
		preferTcp.setText("Prefer TCP");
		preferTcp.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		preferTcp.setToolTipText("If UDP and TCP is supported, choose TCP");
	}

	private void discover()
	{
		final java.util.List<String> args = new ArrayList<>();
		args.add("search");
		if (nat.getSelection())
			args.add("--nat");
		list.removeAll();
		list.redraw();
		log.removeAll();
		asyncAddLog("Discover KNXnet/IP servers, KNX USB interfaces, and USB serial KNX interfaces.");
		asyncAddLog("Selecting an interface opens the connection dialog, checking makes it the default interface.");
		asyncAddLog("KNXnet/IP discovery - using command line: " + String.join(" ", args));
		try {
			final String sep = "\n";
			final Runnable r = new Discover(args.toArray(new String[0])) {
				@Override
				protected void onEndpointReceived(final Result<SearchResponse> result)
				{
					final StringBuilder buf = new StringBuilder();
					final SearchResponse r = result.response();
					buf.append("\"").append(r.getDevice().getName()).append("\"");
					buf.append(" at ").append(r.getControlEndpoint());
					buf.append(" -- using local interface ").append(result.networkInterface().getName()).append(" ");
					buf.append(result.localEndpoint().getAddress());
					buf.append("      ").append(r.getDevice().toString().replaceAll("\".*\"", "")).append(sep);
					for (int i = buf.indexOf(", "); i != -1; i = buf.indexOf(", "))
						buf.replace(i, i + 2, sep + "      ");
					buf.append("      Supported services: ");
					buf.append(r.getServiceFamilies().toString());

					Main.syncExec(() -> addKnxnetipEndpoint(result, buf.toString()));
				}

				@Override
				protected void onCompletion(final Exception thrown, final boolean canceled)
				{
					if (thrown != null)
						asyncAddLog("error: " + thrown.getMessage());
					asyncAddLog("KNXnet/IP discovery finished");
				}
			};
			new Thread(r).start();

			final Runnable usb = new Runnable() {
				@Override
				public void run()
				{
					try {
						asyncAddLog("Search for KNX USB interfaces");
						final var knxUsbDevices = UsbConnectionFactory.attachedKnxUsbDevices();
						final var vserialKnxDevices = List.<Device>of();

						asyncAddLog("Found " + knxUsbDevices.size() + " KNX USB interfaces");
						asyncAddLog("Found " + vserialKnxDevices.size() + " USB serial KNX interfaces");

						Main.syncExec(() -> {
							for (final var d : knxUsbDevices) {
								try {
									// check knx medium, defaults to TP1
									int medium = KNXMediumSettings.MEDIUM_TP1;
									try (UsbConnection c = UsbConnectionFactory.open(d)) {
										medium = c.deviceDescriptor().medium().getMedium();
									}
									catch (KNXException | InterruptedException | RuntimeException e) {
										asyncAddLog("reading KNX device descriptor of " + d,  e);
									}

									final String vp = String.format("%04x:%04x", d.vendorId(), d.productId());
									addListItem(new String[] { "USB -- ID " + d },
											new String[] { "protocol", "name", "port", "medium", "SN" },
											new Object[] { Protocol.USB, d.product(), vp, medium, SerialNumber.Zero });
								}
								catch (final RuntimeException e) {
									asyncAddLog("error: " + e.getMessage());
								}
							}
							for (final var d : vserialKnxDevices) {
								try {
									addListItem(new String[] { "TP-UART -- ID " + d },
											new String[] { "protocol", "name", "port", "medium", "SN" },
											new Object[] { Protocol.Tpuart, d.product(), "/dev/",
													KNXMediumSettings.MEDIUM_TP1, SerialNumber.Zero });
								}
								catch (final RuntimeException e) {
									asyncAddLog("error: " + e.getMessage());
								}
							}
						});
						if (!vserialKnxDevices.isEmpty()) {
							final Set<String> ports = SerialConnectionFactory.portIdentifiers();
							asyncAddLog(ports.stream().collect(Collectors.joining(", ", "Available serial ports: ", "")));
						}
					}
					catch (final RuntimeException e) {
						asyncAddLog("error: " + e.getMessage());
					}
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
		final var secure = secureServices(i);
		new ConnectDialog(getTabFolder(), (Protocol) i.getData("protocol"), (String) i.getData("localEP"),
				(String) i.getData("name"), (String) i.getData("host"), (String) i.getData("port"),
				(String) i.getData("mcast"), (Integer) i.getData("medium"), nat.getSelection(), secure,
				preferRouting.getSelection(), preferTcp.getSelection(), (IndividualAddress) i.getData("hostIA"), (SerialNumber) i.getData("SN"));
	}

	private static final String secureSymbol = new String(Character.toChars(0x1F512));

	private final Set<InetSocketAddress> supportsSearchResponseV2 = new HashSet<>();

	private void addKnxnetipEndpoint(final Result<SearchResponse> result, final String newItem)
	{
		// only add the new item if it is different from any already shown in the list
		for (final TableItem item : list.getItems()) {
			if (item.getText().equals(newItem))
				return;
		}
		final SearchResponse r = result.response();

		if (r.v2())
			supportsSearchResponseV2.add(result.remoteEndpoint());
		else if (supportsSearchResponseV2.contains(result.remoteEndpoint()))
			return;

		String mcast = null;
		try {
			mcast = InetAddress.getByAddress(r.getDevice().getMulticastAddress()).getHostAddress();
		}
		catch (final UnknownHostException e) {}

		boolean tunneling = false;
		boolean routing = false;
		Map<ServiceFamily, Integer> secureServices = Map.of();

		String itemText = newItem;

		for (final var d : r.description()) {
			if (d instanceof final ServiceFamiliesDIB families) {
				if (families.getDescTypeCode() == DIB.SUPP_SVC_FAMILIES) {
					for (final var entry : families.families().entrySet()) {
						if (entry.getKey() == ServiceFamily.Core && entry.getValue() > 1)
							itemText = itemText.replaceFirst("UDP", "UDP & TCP");
						if (entry.getKey() == ServiceFamily.Tunneling)
							tunneling = true;
						if (entry.getKey() == ServiceFamily.Routing)
							routing = true;
					}
				}
				else if (families.getDescTypeCode() == DIB.SecureServiceFamilies) {
					secureServices = families.families();
					itemText += "\n                                        " + families;
					itemText = itemText.replaceFirst("--", secureSymbol + " --");
				}
			}
		}

		final Protocol protocol = tunneling ? Protocol.Tunneling : Protocol.Routing;

		addListItem(new String[] { itemText },
				new String[] { "protocol", "localEP", "name", "host", "port", "mcast", "medium", "secure", "supportsRouting", "hostIA", "SN" },
				new Object[] { protocol, result.localEndpoint().getAddress().getHostAddress(), r.getDevice().getName(),
					r.getControlEndpoint().getAddress().getHostAddress(), Integer.toString(r.getControlEndpoint().getPort()), mcast,
					r.getDevice().getKNXMedium(), secureServices, routing, result.response().getDevice().getAddress(),
					result.response().getDevice().serialNumber() });
	}
}

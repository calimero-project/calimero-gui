/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2024 B. Malinowsky

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
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.SerialNumber;
import tuwien.auto.calimero.gui.ConnectArguments.Protocol;
import tuwien.auto.calimero.internal.Executor;
import tuwien.auto.calimero.knxnetip.Discoverer.Result;
import tuwien.auto.calimero.knxnetip.KNXnetIPConnection;
import tuwien.auto.calimero.knxnetip.servicetype.SearchResponse;
import tuwien.auto.calimero.knxnetip.util.DIB;
import tuwien.auto.calimero.knxnetip.util.DeviceDIB;
import tuwien.auto.calimero.knxnetip.util.ServiceFamiliesDIB;
import tuwien.auto.calimero.knxnetip.util.ServiceFamiliesDIB.ServiceFamily;
import tuwien.auto.calimero.link.medium.KNXMediumSettings;
import tuwien.auto.calimero.log.LogService.LogLevel;
import tuwien.auto.calimero.serial.SerialConnectionFactory;
import tuwien.auto.calimero.serial.usb.Device;
import tuwien.auto.calimero.serial.usb.UsbConnection;
import tuwien.auto.calimero.serial.usb.UsbConnectionFactory;
import tuwien.auto.calimero.tools.Discover;

/**
 * @author B. Malinowsky
 */
class DiscoverTab extends BaseTabLayout
{
	sealed interface Access {
		ConnectArguments.Protocol protocol();

		String name();

		int medium();

		SerialNumber serialNumber();
	}

	record SerialAccess(Protocol protocol, String name, int medium, String port, SerialNumber serialNumber)
			implements Access {}

	record IpAccess(Protocol protocol, String name, int medium, InetSocketAddress localEP, InetSocketAddress remote,
			Optional<InetSocketAddress> multicast, Map<ServiceFamiliesDIB.ServiceFamily, Integer> securedServices,
			IndividualAddress hostIA, SerialNumber serialNumber) implements Access {}

	record UnknownAccess(Protocol protocol, String name, int medium, SerialNumber serialNumber) implements Access {}

	static final UnknownAccess UnknownAccess = new UnknownAccess(Protocol.Unknown, "unknown",
			KNXMediumSettings.MEDIUM_TP1, SerialNumber.Zero);

	Button nat;
	Button preferRouting;
	Button preferTcp;

	DiscoverTab(final CTabFolder tf)
	{
		super(tf, "Endpoint discovery && description", null, false, false, null);
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
		setLogLevel(LogLevel.DEBUG);
		addLogIncludeFilter(".*calimero\\.(knxnetip\\.Discoverer|usb).*");
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
		else {
			item = Arrays.stream(items).filter(TableItem::getChecked).findFirst();
			if (item.isEmpty())
				return Optional.empty();
		}

		final TableItem defaultInterface = item.get();
		final var access = (Access) defaultInterface.getData("access");

		if (access instanceof IpAccess ipAccess) {
			// switch to routing if preferred and available
			if (preferRouting.getSelection() && access.protocol() == Protocol.Tunneling && ipAccess.multicast().isPresent()) {
				ipAccess = new IpAccess(Protocol.Routing, ipAccess.name(), ipAccess.medium(),
						ipAccess.localEP, ipAccess.remote, ipAccess.multicast, ipAccess.securedServices, ipAccess.hostIA,
						ipAccess.serialNumber);
			}
			return Optional.of(new ConnectArguments(ipAccess, nat.getSelection(), preferTcp.getSelection(), "", ""));
		}

		return Optional.of(new ConnectArguments((SerialAccess) access, "", ""));
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
		nat.setText("Use NAT (Network Address Translation)");
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
		list.removeAll();
		list.redraw();
		log.removeAll();
		supportsSearchResponseV2.clear();

		final var args = new ArrayList<String>();
		args.add("search");
		if (nat.getSelection())
			args.add("--nat");

		asyncAddLog("Discover KNXnet/IP servers, KNX USB interfaces, and USB serial KNX interfaces.");
		asyncAddLog("Selecting an interface opens the connection dialog, checking makes it the default interface.");
		asyncAddLog("KNXnet/IP discovery - using command line: " + String.join(" ", args));

		final String sep = "\n";
		try {
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
			Executor.execute(r);

			final Runnable usb = () -> {
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
								addListItem("USB -- ID " + d,
										new SerialAccess(Protocol.USB, d.product(), medium, vp, SerialNumber.Zero));
							}
							catch (final RuntimeException e) {
								asyncAddLog("error: " + e.getMessage());
							}
						}
						for (final var d : vserialKnxDevices) {
							try {
								addListItem("TP-UART -- ID " + d,
										new SerialAccess(Protocol.Tpuart, d.product(),
												KNXMediumSettings.MEDIUM_TP1, "/dev/", SerialNumber.Zero));
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
			};
			Executor.execute(usb);
		}
		catch (final Exception e) {
			log.add("error: " + e.getMessage());
		}
	}

	void addListItem(final String itemText, final Access access) {
		addListItem(new String[]{ itemText }, new String[]{ "access" }, new Object[] { access });
	}

	@Override
	protected void onListItemSelected(final SelectionEvent e)
	{
		final TableItem i = (TableItem) e.item;
		new ConnectDialog(getTabFolder(), (Access) i.getData("access"), nat.getSelection(),
				preferRouting.getSelection(), preferTcp.getSelection());
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
		final DeviceDIB device = r.getDevice();

		if (r.v2())
			supportsSearchResponseV2.add(result.remoteEndpoint());
		else if (supportsSearchResponseV2.contains(result.remoteEndpoint()))
			return;

		var mcast = Optional.<InetSocketAddress>empty();
		try {
			mcast = Optional.of(new InetSocketAddress(InetAddress.getByAddress(device.getMulticastAddress()),
					KNXnetIPConnection.DEFAULT_PORT));
		}
		catch (final UnknownHostException e) {}

		Map<ServiceFamily, Integer> secureServices = Map.of();
		String itemText = newItem;

		Protocol protocol = Protocol.Routing;
		for (final var d : r.description()) {
			if (d instanceof final ServiceFamiliesDIB families) {
				if (families.getDescTypeCode() == DIB.SUPP_SVC_FAMILIES) {
					for (final var entry : families.families().entrySet()) {
						if (entry.getKey() == ServiceFamily.Core && entry.getValue() > 1)
							itemText = itemText.replaceFirst("UDP", "UDP & TCP");
						if (entry.getKey() == ServiceFamily.Tunneling)
							protocol = Protocol.Tunneling;
					}
				}
				else if (families.getDescTypeCode() == DIB.SecureServiceFamilies) {
					secureServices = families.families();
					itemText += "\n                                        " + families;
					itemText = itemText.replaceFirst("--", secureSymbol + " --");
				}
			}
		}

		// reset search port (3671) to use ephemeral port, otherwise we might run into already bound address
		final var localEP = new InetSocketAddress(result.localEndpoint().getAddress(), 0);
		final var access = new IpAccess(protocol, device.getName(), device.getKNXMedium(), localEP,
				r.getControlEndpoint().endpoint(), mcast, secureServices, device.getAddress(),
				device.serialNumber());
		addListItem(itemText, access);
	}
}

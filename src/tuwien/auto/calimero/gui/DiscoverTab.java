/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2019 B. Malinowsky

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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;
import javax.usb.UsbDisconnectedException;
import javax.usb.UsbException;

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
import org.usb4java.Device;
import org.usb4java.LibUsb;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments.Protocol;
import tuwien.auto.calimero.knxnetip.Discoverer.Result;
import tuwien.auto.calimero.knxnetip.servicetype.SearchResponse;
import tuwien.auto.calimero.knxnetip.util.ServiceFamiliesDIB;
import tuwien.auto.calimero.link.medium.KNXMediumSettings;
import tuwien.auto.calimero.log.LogService.LogLevel;
import tuwien.auto.calimero.serial.LibraryAdapter;
import tuwien.auto.calimero.serial.usb.UsbConnection;
import tuwien.auto.calimero.tools.Discover;

/**
 * @author B. Malinowsky
 */
class DiscoverTab extends BaseTabLayout
{
	private Button nat;
	Button preferRouting;

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
		else
			item = Arrays.asList(items).stream().filter(ti -> ti.getChecked()).findFirst();
		if (!item.isPresent())
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
				(String) defaultInterface.getData("port"), nat.getSelection(), host, "", "");
		args.name = (String) defaultInterface.getData("name");
		args.knxMedium = Optional.ofNullable((Integer) defaultInterface.getData("medium")).orElse(KNXMediumSettings.MEDIUM_TP1);
		args.secure = (boolean) Optional.ofNullable(defaultInterface.getData("secure")).orElse(Boolean.FALSE);
		return Optional.of(args);
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		((GridLayout) top.getLayout()).numColumns = 3;
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
		preferRouting.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		preferRouting.setToolTipText("If both Routing & Tunneling mode are supported, choose Routing");
	}

	private void discover()
	{
		final java.util.List<String> args = new ArrayList<String>();
		args.add("-s");
		if (nat.getSelection())
			args.add("--nat");
		list.removeAll();
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
					final SearchResponse r = result.getResponse();
					buf.append("\"").append(r.getDevice().getName()).append("\"");
					buf.append(" at ").append(r.getControlEndpoint());
					buf.append(" -- using local interface ").append(result.getNetworkInterface().getName()).append(" ");
					buf.append(result.getAddress());
					buf.append("    ").append(r.getDevice().toString().replaceAll("\".*\"", "")).append(sep);
					for (int i = buf.indexOf(", "); i != -1; i = buf.indexOf(", "))
						buf.replace(i, i + 2, sep + "    ");
					buf.append("    Supported services: ");
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
						final List<UsbDevice> knxDevices = UsbConnection.getKnxDevices();
						final List<UsbDevice> vserialKnxDevices = UsbConnection.getVirtualSerialKnxDevices();

						asyncAddLog("Found " + knxDevices.size() + " KNX USB interfaces");
						asyncAddLog("Found " + vserialKnxDevices.size() + " USB serial KNX interfaces");

						Main.syncExec(new Runnable() {
							@Override
							public void run()
							{
								for (final UsbDevice d : knxDevices) {
									try {
										final String ind = "    ";
										final StringBuilder sb = new StringBuilder();
										sb.append("USB interface ").append(d).append(sep);

										final UsbDeviceDescriptor dd = d.getUsbDeviceDescriptor();
										final int vendorId = dd.idVendor() & 0xffff;
										final int productId = dd.idProduct() & 0xffff;

										final String product = productName(d, vendorId, productId);
										sb.append(ind).append("Name: ").append(product).append(sep);
										sb.append(ind).append(manufacturer(d, vendorId, productId));

										final String vp = String.format("%04x:%04x", vendorId, productId);

										// check knx medium, defaults to TP1
										int medium = KNXMediumSettings.MEDIUM_TP1;
										try (UsbConnection c = new UsbConnection(vendorId, productId)) {
											medium = c.deviceDescriptor().medium().getMedium();
										}
										catch (KNXException | InterruptedException | RuntimeException e) {
											asyncAddLog("reading KNX device descriptor of " + d,  e);
										}

										addListItem(new String[] { sb.toString() },
												new String[] { "protocol", "name", "port", "medium" },
												new Object[] { Protocol.USB, product, vp, medium });
									}
									catch (final RuntimeException e) {
										asyncAddLog("error: " + e.getMessage());
									}
								}
								for (final UsbDevice d : vserialKnxDevices) {
									try {
										final String ind = "    ";
										final StringBuilder sb = new StringBuilder();
										sb.append("TP-UART interface ").append(d).append(sep);
										final UsbDeviceDescriptor dd = d.getUsbDeviceDescriptor();
										final int vendorId = dd.idVendor() & 0xffff;
										final int productId = dd.idProduct() & 0xffff;
										final String product = productName(d, vendorId, productId);

										sb.append(ind).append("Name: ").append(product).append(sep);
										sb.append(ind).append(manufacturer(d, vendorId, productId));
										final String dev = "/dev/";

										final int medium = KNXMediumSettings.MEDIUM_TP1;
										addListItem(new String[] { sb.toString() },
												new String[] { "protocol", "name", "port", "medium" },
												new Object[] { Protocol.Tpuart, product, dev, medium });
									}
									catch (final RuntimeException e) {
										asyncAddLog("error: " + e.getMessage());
									}
								}
							}
						});
						if (!vserialKnxDevices.isEmpty()) {
							final List<String> l = LibraryAdapter.getPortIdentifiers();
							asyncAddLog(l.stream().collect(Collectors.joining(", ", "Available serial ports: ", "")));
						}
					}
					catch (final RuntimeException e) {
						asyncAddLog("error: " + e.getMessage());
					}
				};

				private String productName(final UsbDevice d, final int vendorId, final int productId)
				{
					Optional<String> product;
					try {
						product = Optional.ofNullable(d.getProductString());
					}
					catch (UnsupportedEncodingException | UsbDisconnectedException | UsbException e) {
						final Device device = UsbConnection.findDeviceLowLevel(vendorId, productId);
						product = UsbConnection.getProductName(device);
						LibUsb.unrefDevice(device);
					}
					return nullTerminate(product.orElse("n/a"));
				}

				private String manufacturer(final UsbDevice d, final int vendorId, final int productId)
				{
					Optional<String> mf;
					try {
						mf = Optional.ofNullable(d.getManufacturerString());
					}
					catch (UnsupportedEncodingException | UsbDisconnectedException | UsbException e) {
						final Device device = UsbConnection.findDeviceLowLevel(vendorId, productId);
						mf = UsbConnection.getManufacturer(device);
						LibUsb.unrefDevice(device);
					}
					return "Manufacturer: " + nullTerminate(mf.orElse("n/a"));
				}

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
		final boolean secure = (boolean) Optional.ofNullable(i.getData("secure")).orElse(Boolean.FALSE);
		new ConnectDialog(getTabFolder(), (Protocol) i.getData("protocol"), (String) i.getData("localEP"),
				(String) i.getData("name"), (String) i.getData("host"), (String) i.getData("port"),
				(String) i.getData("mcast"), (Integer) i.getData("medium"), nat.getSelection(), secure, preferRouting.getSelection());
	}

	private void addKnxnetipEndpoint(final Result<SearchResponse> result, final String newItem)
	{
		// only add the new item if it is different from any already shown in the list
		for (final TableItem item : list.getItems()) {
			if (item.getText().equals(newItem))
				return;
		}
		final SearchResponse r = result.getResponse();
		String mcast = null;
		try {
			mcast = InetAddress.getByAddress(r.getDevice().getMulticastAddress()).getHostAddress();
		}
		catch (final UnknownHostException e) {}

		boolean tunneling = false;
		boolean routing = false;
		boolean secure = false;
		final int[] families = r.getServiceFamilies().getFamilyIds();
		for (final int id : families) {
			if (id == ServiceFamiliesDIB.TUNNELING)
				tunneling = true;
			if (id == ServiceFamiliesDIB.ROUTING)
				routing = true;
			if (id == 9)
				secure = true;
		}
		final Protocol protocol = tunneling ? Protocol.Tunneling : Protocol.Routing;

		addListItem(new String[] { newItem },
				new String[] { "protocol", "localEP", "name", "host", "port", "mcast", "medium", "secure", "supportsRouting", "hostIA" },
				new Object[] { protocol, result.getAddress().getHostAddress(), r.getDevice().getName(),
					r.getControlEndpoint().getAddress().getHostAddress(), Integer.toString(r.getControlEndpoint().getPort()), mcast,
					r.getDevice().getKNXMedium(), secure, routing, result.getResponse().getDevice().getAddress() });
	}
}

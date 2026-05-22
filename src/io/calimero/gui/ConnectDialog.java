/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2006, 2026 B. Malinowsky

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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolTip;

import io.calimero.IndividualAddress;
import io.calimero.gui.ConnectArguments.Protocol;
import io.calimero.gui.DiscoverTab.IpAccess;
import io.calimero.gui.DiscoverTab.SerialAccess;
import io.calimero.knxnetip.KNXnetIPConnection;
import io.calimero.knxnetip.KNXnetIPRouting;
import io.calimero.knxnetip.util.ServiceFamiliesDIB.ServiceFamily;

/**
 * @author B. Malinowsky
 */
class ConnectDialog {

	private static final InetAddress localhost;
	static {
		InetAddress ia;
		try {
			ia = InetAddress.getLocalHost();
		}
		catch (final UnknownHostException uhe) {
			ia = ipAddressOrError(new byte[] { 127, 0, 0, 1 });
		}
		localhost = ia;
	}
	private static final InetAddress anyLocalIPv4Address = ipAddressOrError(new byte[] { 0, 0, 0, 0 });

	private static InetAddress ipAddressOrError(final byte[] addr) {
		try {
			return InetAddress.getByAddress(addr);
		}
		catch (final UnknownHostException e) {
			throw new InternalError(e);
		}
	}

	private record TunnelingControls(Text localHost, Text localPort, Text remoteHost, Text remotePort, Button tcp, Button nat) {}
	private record RoutingControls(Combo netif, Text multicast) {}
	private record SerialControls(Text serialPort) {}

	private TunnelingControls tunnelingControls;
	private RoutingControls routingControls;
	private SerialControls usbControls;
	private SerialControls tpuartControls;
	private SerialControls ft12Controls;


	ConnectDialog(final CTabFolder tf, final DiscoverTab.Access access, final boolean useNAT, final boolean preferRouting,
			final boolean preferTcp) {
		final Shell shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setLayout(new GridLayout());

		final boolean confirm = access.protocol() != Protocol.Unknown;
		shell.setText(confirm ? access.name() : "Connection Settings");

		final var connTypes = new Accordion<Protocol>(shell);

		final var tunnelingSection = connTypes.addSection(shell, Protocol.Tunneling, "KNX IP Tunneling");
		tunnelingSection.setContent(tunnelingSettings(shell, access, preferTcp, useNAT));

		final var routingSection = connTypes.addSection(shell, Protocol.Routing, "KNX IP Routing");
		routingSection.setContent(routingSettings(shell, access));

		final var usbSection = connTypes.addSection(shell, Protocol.USB, "KNX USB");
		usbSection.setContent(usbSettings(shell, access));

		final var tpuartSection = connTypes.addSection(shell, Protocol.Tpuart, "TPUART");
		tpuartSection.setContent(serialSettings(shell, true, access));

		final var ft12Section = connTypes.addSection(shell, Protocol.FT12, "FT1.2");
		ft12Section.setContent(serialSettings(shell, false, access));

		switch (access.protocol()) {
			case Tunneling -> {
				if (preferRouting && access instanceof final IpAccess ipAccess && ipAccess.multicast().isPresent())
					routingSection.expand();
				else
					tunnelingSection.expand();
			}
			case Routing -> routingSection.expand();
			case USB -> usbSection.expand();
			case Tpuart -> tpuartSection.expand();
			case FT12 -> ft12Section.expand();
			default -> {
				if (preferRouting)
					routingSection.expand();
				else
					tunnelingSection.expand();
			}
		}

		final Composite c = new Composite(shell, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final VerifyListener onlyKnxAddress = e -> {
			final String text = ((Text) e.widget).getText();
			final String newText = text.substring(0, e.start).concat(e.text).concat(text.substring(e.end));
			if (!newText.matches("^\\d{0,2}(?:\\.\\d{0,2}(?:\\.\\d{0,3})?)?$"))
				e.doit = false;
		};

		new Label(c, SWT.NONE).setText("Local KNX address (optional): ");
		final var localKnxAddress = new Text(c, SWT.BORDER);
		final var localKnxAddressGD = new GridData();
		localKnxAddressGD.widthHint = (int) (1.5 * computeTextWidth(localKnxAddress, "15.15.255"));
		localKnxAddress.setLayoutData(localKnxAddressGD);
		localKnxAddress.setMessage("area.line.device");
		localKnxAddress.setToolTipText("Useful for routing, tunneling && TP-UART");
		localKnxAddress.addVerifyListener(onlyKnxAddress);

		new Label(c, SWT.NONE).setText("Remote KNX address (optional): ");
		final Text remoteKnxAddress = new Text(c, SWT.BORDER);
		final var remoteKnxAddressGD = new GridData();
		remoteKnxAddressGD.widthHint = (int) (1.5 * computeTextWidth(localKnxAddress, "15.15.255"));
		remoteKnxAddress.setLayoutData(remoteKnxAddressGD);
		remoteKnxAddress.setMessage("area.line.device");
		remoteKnxAddress.setToolTipText("""
				Specify device address for
				  • reading remote device info
				  • opening remote property/memory editor
				Scan devices:
				  • specify area for scanning an area
				  • specify area.line for scanning a line
				  • specify area.line.device to scan a single device""");
		remoteKnxAddress.addVerifyListener(onlyKnxAddress);

		// usb can't use local KNX address
		usbSection.setExpandCallback(() -> localKnxAddress.setEnabled(false));
		usbSection.setCollapseCallback(() -> localKnxAddress.setEnabled(true));

		final Composite mode = new Composite(shell, SWT.NONE);
		final RowLayout col = new RowLayout(SWT.VERTICAL);
		col.fill = true;
		col.wrap = false;
		mode.setLayout(col);

		final Button procComm = new Button(mode, SWT.RADIO);
		procComm.setText("Process communication / group monitor");
		procComm.setSelection(true);

		final Button monitor = new Button(mode, SWT.RADIO);
		monitor.setText("Network monitor");

		final Button config = new Button(mode, SWT.RADIO);
		config.setText("Configure KNXnet/IP");

		final Button scan = new Button(mode, SWT.RADIO);
		scan.setText("Scan for KNX devices");
		scan.setToolTipText("Requires a KNX area.line or KNX device address");

		final Button devinfo = new Button(mode, SWT.RADIO);
		devinfo.setText("Read KNX device information");
		devinfo.setToolTipText("Requires a KNX device address");

		final Button properties = new Button(mode, SWT.RADIO);
		properties.setText("KNX property editor");
		properties.setToolTipText("Uses Local Device Management or Remote Property Services");

		final Button memory = new Button(mode, SWT.RADIO);
		memory.setText("KNX device memory editor");
		memory.setToolTipText("Uses Remote Property Services");

		final Button progmode = new Button(mode, SWT.RADIO);
		progmode.setText("KNX devices in programming mode");
		progmode.setToolTipText("Uses Local Device Management or Remote Property Services");

		final Button baos = new Button(mode, SWT.RADIO);
		baos.setText("BAOS view");
		baos.setToolTipText("Connection to a BAOS device");


		final Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, true));
		final RowLayout row = new RowLayout(SWT.HORIZONTAL);
		row.fill = true;
		row.spacing = 10;
		row.wrap = false;
		buttons.setLayout(row);

		final Button connect;
		final Button cancel;
		final boolean mac = "cocoa".equals(SWT.getPlatform());
		if (mac) {
			cancel = new Button(buttons, SWT.NONE);
			connect = new Button(buttons, SWT.NONE);
		}
		else {
			connect = new Button(buttons, SWT.NONE);
			cancel = new Button(buttons, SWT.NONE);
		}
		connect.setText("Connect");
		cancel.setText("Cancel");

		connect.addListener(SWT.Selection, __ -> {
			ConnectArguments args = null;

			final Protocol protocol = connTypes.currentSection().id();
			switch (protocol) {
				case Protocol.Tunneling -> {
					final InetAddress local = parseIp(tunnelingControls.localHost);
					if (local == null)
						return;
					int localPort = 0;
					if (!tunnelingControls.remotePort.getText().isEmpty())
						localPort = Integer.parseUnsignedInt(tunnelingControls.remotePort.getText());
					final var localEndpoint = new InetSocketAddress(local, localPort);

					final String host = tunnelingControls.remoteHost.getText();
					if (host.isEmpty())
						return;
					final InetAddress addr = parseIp(tunnelingControls.remoteHost);
					if (addr == null)
						return;
					int remotePort = KNXnetIPConnection.DEFAULT_PORT;
					if (!tunnelingControls.remotePort.getText().isEmpty())
						remotePort = Integer.parseUnsignedInt(tunnelingControls.remotePort.getText());
					final var remoteEndpoint = new InetSocketAddress(addr, remotePort);

					final String name = confirm ? access.name() : host;

					Map<ServiceFamily, Integer> securedServices = Map.of();
					IndividualAddress hostIA = null;
					if (access instanceof final IpAccess ipAccess) {
						securedServices = ipAccess.securedServices();
						hostIA = ipAccess.hostIA();
					}

					final boolean nat = tunnelingControls.nat.getSelection();
					final boolean tcp = tunnelingControls.tcp.getSelection();
					final String lka = localKnxAddress.getText();
					final String rka = remoteKnxAddress.getText();

					args = new ConnectArguments(new IpAccess(protocol, name, access.medium(),
							localEndpoint, remoteEndpoint, Optional.empty(),
							securedServices, hostIA, access.serialNumber()), nat, tcp, lka, rka);
				}
				case Protocol.Routing -> {
					InetAddress ipv4 = anyLocalIPv4Address;
					try {
						ipv4 = Optional.ofNullable(NetworkInterface.getByName(routingControls.netif.getText()))
								.flatMap(nif -> nif.inetAddresses().filter(Inet4Address.class::isInstance).findFirst())
								.orElse(anyLocalIPv4Address);
					}
					catch (final SocketException ignore) {}
					final InetSocketAddress local = new InetSocketAddress(ipv4, 0);

					final InetAddress addr = parseIp(routingControls.multicast);
					if (addr == null)
						return;
					final var mcast = new InetSocketAddress(addr, KNXnetIPConnection.DEFAULT_PORT);

					Map<ServiceFamily, Integer> securedServices = Map.of();
					IndividualAddress hostIA = null;
					if (access instanceof final IpAccess ipAccess) {
						securedServices = ipAccess.securedServices();
						hostIA = ipAccess.hostIA();
					}

					final String name = confirm ? access.name() : addr.getHostName();
					final String lka = localKnxAddress.getText();
					final String rka = remoteKnxAddress.getText();
					args = new ConnectArguments(new IpAccess(protocol, name, access.medium(),
							local, mcast, Optional.of(mcast),
							securedServices, hostIA, access.serialNumber()), false, false, lka, rka);
				}
				case Protocol.USB -> {
					final String port = usbControls.serialPort.getText();
					if (port.isEmpty())
						return;
					final String name = confirm ? access.name() : port;
					final String rka = remoteKnxAddress.getText();
					args = new ConnectArguments(new SerialAccess(Protocol.USB, name, access.medium(), port,
							access.serialNumber()), "", rka);
				}
				case Protocol.Tpuart -> {
					final String port = tpuartControls.serialPort.getText();
					if (port.isEmpty())
						return;
					final String name = confirm ? access.name() : port;
					// process communication and bus monitoring don't require local knx address
					final String lka = procComm.getSelection() || monitor.getSelection() ? "" : localKnxAddress.getText();
					final String rka = remoteKnxAddress.getText();
					args = new ConnectArguments(new SerialAccess(Protocol.Tpuart, name, access.medium(), port,
							access.serialNumber()), lka, rka);
				}
				case Protocol.FT12 -> {
					final String port = ft12Controls.serialPort.getText();
					if (port.isEmpty())
						return;
					final String name = confirm ? access.name() : port;
					final String rka = remoteKnxAddress.getText();
					args = new ConnectArguments(new SerialAccess(Protocol.FT12, name, access.medium(), port,
							access.serialNumber()), "", rka);
				}
				case Protocol.DeviceManagement, Protocol.Unknown -> throw new IllegalStateException();
			}

			if (monitor.getSelection())
				new MonitorTab(tf, args);
			else if (config.getSelection())
				new IPConfigTab(tf, args);
			else if (scan.getSelection())
				new ScanDevicesTab(tf, args);
			else if (devinfo.getSelection())
				new DeviceInfoTab(tf, args);
			else if (properties.getSelection())
				new PropertyEditorTab(tf, args);
			else if (memory.getSelection()) {
				if (args.remoteKnxAddress.isEmpty()) {
					remoteKnxAddress.setFocus();
					remoteKnxAddress.setMessage("Enter address");
					return;
				}
				new MemoryEditor(tf, args);
			}
			else if (procComm.getSelection())
				new ProcCommTab(tf, args);
			else if (progmode.getSelection())
				new ProgmodeTab(tf, args);
			else if (baos.getSelection())
				new BaosTab(tf, args);

			shell.dispose();
		});

		cancel.addListener(SWT.Selection, __ -> shell.dispose());

		shell.setDefaultButton(connect);
		shell.pack();
		final Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		// on GTK, computeSize underestimates the preferred size, leading to clipping
		if ("gtk".equals(SWT.getPlatform())) {
			size.x = (int) (size.x * 1.2);
			size.y = (int) (size.y * 1.2);
		}
		shell.setMinimumSize(size);
		shell.setMaximumSize(size.x + size.x / 2, size.y);
		shell.setSize(size.x + size.x / 4, size.y);
		shell.open();
	}

	private Composite tunnelingSettings(final Composite parent, final DiscoverTab.Access access,
			final boolean preferTcp, final boolean useNat) {
		final Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(4, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final VerifyListener onlyFiveDigits = e -> {
			final String text = ((Text) e.widget).getText();
			final String newText = text.substring(0, e.start).concat(e.text).concat(text.substring(e.end));
			if (!newText.matches("\\d{0,5}") || Integer.parseUnsignedInt(newText) > 0xffff)
				e.doit = false;
		};

		final Text localHost = addHostInput(c, "Local endpoint:", null);
		new Label(c, SWT.NONE).setText(":");
		final Text localPort = new Text(c, SWT.BORDER);
		final GridData localPortLD = new GridData(SWT.LEFT, SWT.NONE, false, false);
		localPortLD.widthHint = computeTextWidth(localPort, "99999");
		localPort.setLayoutData(localPortLD);
		localPort.addVerifyListener(onlyFiveDigits);

		final Text remoteHost = addHostInput(c, "Remote endpoint:", localhost);
		new Label(c, SWT.NONE).setText(":");
		final Text remotePort = new Text(c, SWT.BORDER);
		final GridData remotePortGD = new GridData(SWT.LEFT, SWT.NONE, false, false);
		remotePortGD.widthHint = computeTextWidth(remotePort, "99999");
		remotePort.setLayoutData(remotePortGD);
		remotePort.addVerifyListener(onlyFiveDigits);

		if (access instanceof final IpAccess ipAccess) {
			localHost.setText(ipAccess.localEP().getAddress().getHostAddress());
			localPort.setText(String.valueOf(ipAccess.localEP().getPort()));
			remoteHost.setText(ipAccess.remote().getAddress().getHostAddress());
			remotePort.setText(String.valueOf(ipAccess.remote().getPort()));
		}

		final var checkBoxes = new Composite(c, SWT.NONE);
		checkBoxes.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		final var checkBoxesGL = new GridLayout(3, false);
		checkBoxesGL.marginWidth = 0;
		checkBoxes.setLayout(checkBoxesGL);

		new Label(checkBoxes, SWT.NONE).setText("Use");
		final Button tcp = new Button(checkBoxes, SWT.CHECK);
		tcp.setText("TCP");
		final Button nat = new Button(checkBoxes, SWT.CHECK);
		nat.setText("NAT");

		tcp.addListener(SWT.Selection, __ -> nat.setEnabled(!tcp.getSelection()));
		nat.addListener(SWT.Selection, __ -> tcp.setEnabled(!nat.getSelection()));

		if (preferTcp) {
			tcp.setSelection(true);
			// programmatically setting a selection does not invoke selection listeners :(
			tcp.notifyListeners(SWT.Selection, new Event());
		}
		else if (useNat) {
			nat.setSelection(true);
			// programmatically setting a selection does not invoke selection listeners :(
			nat.notifyListeners(SWT.Selection, new Event());
		}

		tunnelingControls = new TunnelingControls(localHost, localPort, remoteHost, remotePort, tcp, nat);
		return c;
	}

	private Composite routingSettings(final Composite parent, final DiscoverTab.Access access) {
		final Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		new Label(c, SWT.NONE).setText("Network interface:");
		final Combo netif = new Combo(c, SWT.BORDER | SWT.DROP_DOWN);
		netif.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		netif.setToolTipText("Select network interface");
		netifs().forEach(nif -> netif.add(nif.getName()));
		final Text mcast = addHostInput(c, "Multicast group:", KNXnetIPRouting.DefaultMulticast);
		if (access instanceof final IpAccess ipAccess && ipAccess.multicast().isPresent())
			mcast.setText(ipAccess.multicast().orElseThrow().getAddress().getHostAddress());

		routingControls = new RoutingControls(netif, mcast);
		return c;
	}

	private Composite usbSettings(final Composite parent, final DiscoverTab.Access access) {
		final Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		new Label(c, SWT.NONE).setText("USB device:");
		final Text usb = new Text(c, SWT.BORDER);
		usb.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		usb.setToolTipText("Specify either the USB vendor name or VendorId:ProductId");
		if (access instanceof final SerialAccess serialAccess)
			usb.setText(serialAccess.port());

		usbControls = new SerialControls(usb);
		return c;
	}

	private Composite serialSettings(final Composite parent, final boolean tpuart, final DiscoverTab.Access access) {
		final Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		new Label(c, SWT.NONE).setText("Serial port:");
		final var serialPort = new Text(c, SWT.BORDER);
		serialPort.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		serialPort.setToolTipText("Specify the serial port of the " + (tpuart ? "TP-UART" : "FT1.2") + " controller");
		if (access instanceof final SerialAccess serialAccess)
			serialPort.setText(serialAccess.port());

		if (tpuart)
			tpuartControls = new SerialControls(serialPort);
		else
			ft12Controls = new SerialControls(serialPort);
		return c;
	}

	private static Text addHostInput(final Composite c, final String description, final InetAddress addr) {
		new Label(c, SWT.NONE).setText(description);
		final Text data = new Text(c, SWT.BORDER);
		data.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		if (addr != null)
			data.setText(addr.getHostAddress());
		data.setToolTipText("IP address or host name");
		return data;
	}

	private static int computeTextWidth(final Text control, final String template) {
		final GC gc = new GC(control);
		final Point p = gc.textExtent(template);
		gc.dispose();
		return p.x + 10;
	}

	private static Stream<NetworkInterface> netifs() {
		try {
			return NetworkInterface.networkInterfaces().filter(nif -> {
				try {
					return nif.isUp() && nif.supportsMulticast() && !nif.isPointToPoint()
							&& nif.inetAddresses().anyMatch(Inet4Address.class::isInstance);
				}
				catch (final SocketException ignore) {}
				return false;
			});
		}
		catch (final SocketException e) {
			return Stream.empty();
		}
	}

	private static InetAddress parseIp(final Text text) {
		try {
			if (text.getText().isEmpty())
				return anyLocalIPv4Address;
			return InetAddress.getByName(text.getText());
		}
		catch (final UnknownHostException uhe) {
			text.setFocus();
			final var tooltip = new ToolTip(text.getShell(), SWT.ICON_WARNING | SWT.BALLOON);
			tooltip.setMessage("Error determining IP address (" + uhe.getMessage() + ")");
			final Point pt = text.getParent().toDisplay(text.getLocation());
			final var size = text.getSize();
			tooltip.setLocation(pt.x + size.x / 2, pt.y + size.y);
			tooltip.setVisible(true);
			return null;
		}
	}
}

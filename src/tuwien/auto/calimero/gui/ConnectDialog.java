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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolTip;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.KnxRuntimeException;
import tuwien.auto.calimero.SerialNumber;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments.Protocol;
import tuwien.auto.calimero.knxnetip.Discoverer;
import tuwien.auto.calimero.knxnetip.KNXnetIPConnection;
import tuwien.auto.calimero.knxnetip.SecureConnection;
import tuwien.auto.calimero.knxnetip.util.ServiceFamiliesDIB.ServiceFamily;
import tuwien.auto.calimero.link.medium.KNXMediumSettings;
import tuwien.auto.calimero.secure.Keyring;
import tuwien.auto.calimero.secure.Keyring.Interface;

/**
 * @author B. Malinowsky
 */
class ConnectDialog
{
	public static final class ConnectArguments
	{
		public enum Protocol {
			Unknown, DeviceManagement, Tunneling, Routing, FT12, USB, Tpuart
		}

		String name;
		String knxAddress;
		IndividualAddress serverIA;

		Protocol protocol;
		InetSocketAddress remote;
		final String port;
		int knxMedium;

		final InetAddress local;
		private final boolean nat;
		private final boolean tcp;
		private final IndividualAddress host;
		InetSocketAddress serverIP;

		String localKnxAddress;
		Map<ServiceFamily, Integer> secureServices = Map.of();

		SerialNumber serialNumber = SerialNumber.Zero;

		static ConnectArguments newKnxNetIP(final boolean routing, final InetAddress localHost, final InetSocketAddress remoteHost,
				final boolean nat, final boolean tcp, final String knxAddress, final IndividualAddress serverIA) {
			final var config = new ConnectArguments(routing ? Protocol.Routing : Protocol.Tunneling, localHost,
					remoteHost, null, nat, tcp, "", knxAddress);
			config.serverIA = serverIA;
			return config;
		}

		static ConnectArguments newUsb(final String port, final String knxAddress)
		{
			return new ConnectArguments(Protocol.USB, null, null, port, false, false, "", knxAddress);
		}

		static ConnectArguments newTpuart(final String port, final String localKnxAddress,
			final String remoteKnxAddress)
		{
			return new ConnectArguments(Protocol.Tpuart, null, null, port, false, false, localKnxAddress, remoteKnxAddress);
		}

		static ConnectArguments newFT12(final String port, final String knxAddress)
		{
			return new ConnectArguments(Protocol.FT12, null, null, port, false, false, "", knxAddress);
		}

		ConnectArguments(final Protocol p, final InetAddress local, final InetSocketAddress remote,
			final String port, final boolean nat, final boolean tcp, final String localKnxAddress,
			final String remoteKnxAddress)
		{
			protocol = p;
			this.local = local;
			this.remote = remote;
			this.port = port;
			this.nat = nat;
			this.tcp = tcp;
			this.host = KNXMediumSettings.BackboneRouter;
			this.localKnxAddress = localKnxAddress;
			this.knxAddress = remoteKnxAddress;
		}

		ConnectArguments(final Protocol p, final InetAddress local, final InetSocketAddress remote, final String port,
			final boolean nat, final boolean tcp, final IndividualAddress host, final String localKnxAddress,
			final String remoteKnxAddress) {
			protocol = p;
			this.local = local;
			this.remote = remote;
			this.port = port;
			this.nat = nat;
			this.tcp = tcp;
			this.host = host;
			this.localKnxAddress = localKnxAddress;
			this.knxAddress = remoteKnxAddress;
		}

		public boolean useNat()
		{
			return nat;
		}

		public void ignoreRoutingProtocol() {
			if (protocol == Protocol.Routing) {
				protocol = Protocol.Tunneling;
				remote = serverIP;
			}
		}

		// if prefer routing option is set but no knx address is given, we use the server knx address
		ConnectArguments adjustPreferRoutingConfig() {
			if (protocol == Protocol.Routing && knxAddress.isEmpty())
				knxAddress = serverIA.toString();
			return this;
		}

		public String friendlyName() {
			if (!knxAddress.isEmpty())
				return (knxAddress.split("\\.").length < 3 ? "line " : "device ") + knxAddress;

			if (remote != null)
				return "interface " + remote + (useNat() ? " (UDP/NAT)" : "") + (tcp ? " (TCP)" : "");
			return "interface " + port;
		}

		public String id() {
			if (knxAddress != null && !knxAddress.isEmpty())
				return knxAddress;
			if (remote != null)
				return remote.toString();
			return port;
		}

		public List<String> getArgs(final boolean useRemoteAddressOption)
		{
			// make sure keyring is decrypted in case remote device requires data secure
			if (knxAddress != null && !knxAddress.isEmpty()) {
				try {
					final var device = new IndividualAddress(knxAddress);
					KeyringTab.keyring().map(Keyring::devices).filter(devices -> devices.containsKey(device))
							.ifPresent(__ -> KeyringTab.keyringPassword());
				}
				catch (final KNXFormatException e) {
					e.printStackTrace();
				}
			}

			final List<String> args = new ArrayList<>();
			switch (protocol) {
				case Routing, Tunneling -> {
					if (local != null) {
						args.add("--localhost");
						args.add(local.getHostAddress());
					}
					final InetAddress addr = remote.getAddress();
					args.add(addr.getHostAddress());
					if (protocol == Protocol.Tunneling) {
						if (useNat()) args.add("--nat");
						if (tcp) args.add("--tcp");
					}
					if (protocol == Protocol.Routing && isSecure(Protocol.Routing)) {
						String key = config("group.key", addr.getHostAddress());
						if (key.isEmpty()) {
							final String[] tempKey = new String[1];
							Main.syncExec(() -> {
								final PasswordDialog dlg = new PasswordDialog(name, addr);
								if (dlg.show()) tempKey[0] = dlg.groupKey();
							});
							if (tempKey[0] == null) // canceled
								throw new KnxRuntimeException("no group key entered");
							key = tempKey[0];
						}
						args.add("--group-key");
						args.add(key);
					}
					else if (protocol == Protocol.Tunneling && isSecure(Protocol.Tunneling)) {
						final String user = "" + KeyringTab.user();
						final String userPwd = config("user." + user, user);
						if (userPwd.isEmpty()) {
							final PasswordDialog dlg = new PasswordDialog(name, true);
							if (dlg.show()) {
								args.add("--user");
								args.add(dlg.user());
								args.add(dlg.isUserPasswordHash() ? "--user-key" : "--user-pwd");
								args.add(dlg.userPassword());
								if (!dlg.deviceAuthCode().isEmpty()) {
									args.add(dlg.isDeviceAuthHash() ? "--device-key" : "--device-pwd");
									args.add(dlg.deviceAuthCode());
								}
							}
						}
						else {
							args.add("--user");
							args.add(user);
							args.add("--user-pwd");
							args.add(userPwd);
							args.add("--device-key");
							args.add(config("device.key", ""));
						}
						args.add("--knx-address");
						args.add("0.0.0");
					}

					args.add("-p");
					args.add("" + remote.getPort());
				}
				case USB -> args.add("--usb");
				case FT12 -> args.add("--ft12");
				case Tpuart -> args.add("--tpuart");
				default -> throw new IllegalStateException();
			}
			if (port != null && !port.isEmpty())
				args.add(port);
			if (knxMedium != 0) {
				args.add("--medium");
				args.add(KNXMediumSettings.getMediumString(knxMedium));
			}
			if (!localKnxAddress.isEmpty()) {
				args.add("--knx-address");
				args.add(localKnxAddress);
			}
			if (!knxAddress.isEmpty()) {
				if (useRemoteAddressOption)
					args.add("-r");
				args.add(knxAddress);
			}
			return args;
		}

		boolean isSecure(final Protocol protocol) {
			for (final var service : secureServices.keySet()) {
				if (service == ServiceFamily.DeviceManagement && protocol == Protocol.DeviceManagement)
					return true;
				if (service == ServiceFamily.Tunneling && protocol == Protocol.Tunneling)
					return true;
				if (service == ServiceFamily.Routing && protocol == Protocol.Routing)
					return true;
			}
			return false;
		}

		String config(final String key, final String configValue) {
			final var result = lookupKeyring(key, configValue);
			if (result != null)
				return result;

			Path p = null;
			try {
				final Path keyfile = Paths.get("keyfile");
				final Path config = Paths.get(".calimero-gui.config");
				p = Files.exists(keyfile) ? keyfile : config;
				try (var lines = Files.lines(p)) {
					final Map<String, String> map = lines.filter(s -> s.startsWith(key))
							.collect(Collectors.toMap(
									s -> s.substring(0, s.indexOf("=")),
									s -> s.substring(s.indexOf("=") + 1)));
					return map.getOrDefault(key, "");
				}
			}
			catch (IOException | RuntimeException e) {
				System.out.printf("Failed to get value for '%s' from file '%s' (error: %s)%n", key, p, e.getMessage());
			}
			return "";
		}

		private String lookupKeyring(final String key, final String value) {
			try {
				return tryLookupKeyring(key, value);
			}
			catch (KNXFormatException | IOException | RuntimeException e) {
				System.out.println("Keyring problem for '" + key + "': " + e.getMessage());
				return null;
			}
		}

		private String tryLookupKeyring(final String key, final String value)
				throws IOException, KNXFormatException {
			final Keyring keyring = KeyringTab.keyring().orElse(null);
			final char[] keyringPassword = KeyringTab.keyringPassword();
			if (keyring == null || keyringPassword.length == 0)
				return null;

			if ("group.key".equals(key)) {
				final InetAddress remote = InetAddress.getByName(value);
				final var backbone = keyring.backbone().filter(bb -> bb.multicastGroup().equals(remote)).orElseThrow();
				return HexFormat.of().formatHex(keyring.decryptKey(backbone.groupKey().orElseThrow(), keyringPassword));
			}

			if (key.startsWith("device")) {
				final var pwd = Optional.ofNullable(keyring.devices().get(host))
						.flatMap(Keyring.Device::authentication)
						.map(auth -> keyring.decryptPassword(auth, keyringPassword));
				if (pwd.isEmpty())
					return null;
				if ("device.pwd".equals(key))
					return new String(pwd.get());
				if ("device.key".equals(key))
					return HexFormat.of().formatHex(SecureConnection.hashDeviceAuthenticationPassword(pwd.get()));
			}

			if (key.startsWith("user")) {
				final int user = Integer.parseInt(value);
				byte[] pwdData = null;
				if (user == 1) {
					final var device = keyring.devices().get(host);
					pwdData = device.password().orElse(null);
				}
				else {
					final var interfaces = keyring.interfaces().get(host);
					for (final Interface iface : interfaces) {
						if (iface.user() == user) {
							pwdData = iface.password().orElse(null);
							break;
						}
					}
				}
				if (pwdData != null)
					return new String(keyring.decryptPassword(pwdData, keyringPassword));
			}

			if (key.equals("tunnelingAddress")) {
				final var interfaces = keyring.interfaces().get(host);
				final IndividualAddress ia = new IndividualAddress(value);
				for (final Interface iface : interfaces) {
					if (iface.address().equals(ia))
						return new String(keyring.decryptPassword(iface.password().get(), keyringPassword));
				}
			}

			return null;
		}
	}


	ConnectDialog(final CTabFolder tf, final DiscoverTab.Access access, final boolean useNAT, final boolean preferRouting,
			final boolean preferTcp) {
		final Shell shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setLayout(new GridLayout());
		shell.setText("Open connection");

		final boolean confirm = !access.name().isEmpty();
		final boolean serial = access instanceof DiscoverTab.SerialAccess;

		final Label nameLabel = new Label(shell, SWT.NONE);
		nameLabel.setFont(Main.font);
		nameLabel.setText(confirm ? "Name / ID: " + access.name()
				: "Specify connection parameters.\nFor serial connections, leave the endpoints empty.");

		final Composite c = new Composite(shell, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		InetAddress localhost = null;
		try {
			localhost = InetAddress.getLocalHost();
		}
		catch (final UnknownHostException uhe) {}

		final Text hostData;
		final Text localhostData;
		if (access instanceof final DiscoverTab.IpAccess ipAccess) {
			final var local = ipAccess.localEP() != null ? ipAccess.localEP().getAddress() : localhost;
			localhostData = addHostInput(c, "Local endpoint:", local, false);
			hostData = addHostInput(c, "Remote endpoint:", ipAccess.remote().getAddress(), false);
		}
		else {
			localhostData = addHostInput(c, "Local endpoint:", localhost, serial);
			hostData = addHostInput(c, "Remote endpoint:", localhost, serial);
		}

		final Label portLabel = new Label(c, SWT.NONE);
		portLabel.setLayoutData(new GridData(SWT.LEFT, SWT.NONE, false, false));
		portLabel.setText(confirm ? serial ? "Serial port ID:" : "UDP port:" : "Port or ID:");

		final Text portData = new Text(c, SWT.BORDER);
		portData.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		if (access instanceof final DiscoverTab.SerialAccess serialAccess)
			portData.setText(serialAccess.port());
		else if (access instanceof final DiscoverTab.IpAccess ipAccess)
			portData.setText("" + ipAccess.remote().getPort());

		final Button usb = new Button(c, SWT.CHECK);
		usb.setText("Use KNX USB interface");
		usb.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		usb.setToolTipText("Fill the port field, specify either the USB vendor name "
				+ "or use vendorId:productId");
		// spacer to the right of USB checkbox
		new Label(c, SWT.NONE);

		final Button tpuart = new Button(c, SWT.CHECK);
		tpuart.setText("Use TP-UART");
		tpuart.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		tpuart.setToolTipText("Specify the serial port of the TP-UART controller");
		// local KNX address to the right of TP-UART checkbox
		final Text localKnxAddress = new Text(c, SWT.BORDER);
		localKnxAddress.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		localKnxAddress.setText("");
		localKnxAddress.setToolTipText("Specify the KNX address of the local endpoint");

		// IP connection type

		final var ipLabel = new Label(c, SWT.NONE);
		ipLabel.setText("KNXnet/IP connection:");
		final Combo ipType = new Combo(c, SWT.BORDER | SWT.READ_ONLY | SWT.DROP_DOWN);
		ipType.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		enum IpType { Udp("UDP"), UdpNat("UDP & NAT"), Tcp("TCP"), Routing("Routing");
			final String value;

			IpType(final String value) { this.value = value; }
		}
		for (final var type : IpType.values())
			ipType.add(type.value);

		ipType.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				if (IpType.values()[ipType.getSelectionIndex()] == IpType.Routing) {
					if (access instanceof final DiscoverTab.IpAccess ipAccess && ipAccess.multicast().isPresent())
						hostData.setText(ipAccess.multicast().get().getAddress().getHostAddress());
					else if (hostData.getText().isEmpty())
						hostData.setText(Discoverer.SEARCH_MULTICAST);
				} else if (access instanceof final DiscoverTab.IpAccess ipAccess)
					hostData.setText(ipAccess.remote().getAddress().getHostAddress());
			}
		});

		if (access.protocol() == Protocol.Routing || preferRouting) ipType.select(IpType.Routing.ordinal());
		else if (preferTcp) ipType.select(IpType.Tcp.ordinal());
		else if (useNAT) ipType.select(IpType.UdpNat.ordinal());
		else ipType.select(IpType.Udp.ordinal());

		ipType.notifyListeners(SWT.Selection, new Event());

		if (serial)
			ipType.setEnabled(false);

		final Label configKNXAddress = new Label(c, SWT.NONE);
		configKNXAddress.setText("KNX device address (optional): ");
		final Text knxAddr = new Text(c, SWT.BORDER);
		knxAddr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		knxAddr.setMessage("area.line.device");
		knxAddr.setToolTipText("""
				Specify device address for
				  • reading remote device info
				  • opening remote property/memory editor
				Scan devices:
				  • specify area for scanning an area
				  • specify area.line for scanning a line
				  • specify area.line.device to scan a single device""");

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

		final Button baos = new Button(mode, SWT.RADIO);
		baos.setText("BAOS view");
		baos.setToolTipText("Connection to a BAOS device");

		usb.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final boolean enabled = !usb.getSelection();
				localhostData.setEnabled(enabled);
				hostData.setEnabled(enabled);
				tpuart.setEnabled(enabled);
				ipType.setEnabled(enabled);
				localKnxAddress.setEnabled(enabled);
			}
		});

		tpuart.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final boolean enabled = !tpuart.getSelection();
				localhostData.setEnabled(enabled);
				hostData.setEnabled(enabled);
				usb.setEnabled(enabled);
				ipType.setEnabled(enabled);
			}
		});

		switch (access.protocol()) {
			case USB -> {
				usb.setSelection(true);
				// programmatically setting a selection does not invoke selection listeners :(
				usb.notifyListeners(SWT.Selection, new Event());
			}
			case Tpuart -> {
				tpuart.setSelection(true);
				// programmatically setting a selection does not invoke selection listeners :(
				tpuart.notifyListeners(SWT.Selection, new Event());
			}
			case Tunneling -> {
				usb.setEnabled(false);
				tpuart.setEnabled(false);
			}
		}

		final Composite buttons = new Composite(shell, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, true));
		final RowLayout row = new RowLayout(SWT.HORIZONTAL);
		row.fill = true;
		row.spacing = 10;
		row.wrap = false;
		buttons.setLayout(row);

		final Button connect = new Button(buttons, SWT.NONE);
		connect.setText("Connect");
		connect.setLayoutData(new RowData());
		connect.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final InetAddress local = parseIp(localhostData);
				if (local == null)
					return;
				final String h = hostData.getText();
				final String p = portData.getText();
				if (h.isEmpty() && p.isEmpty())
					return;
				final String n = confirm ? access.name() : h.isEmpty() ? p : h;
				final boolean natChecked = serial ? false : ipType.getSelectionIndex() == IpType.UdpNat.ordinal();

				ConnectArguments args;
				if (usb.getSelection())
					args = ConnectArguments.newUsb(p, knxAddr.getText());
				else if (tpuart.getSelection()) {
					// process communication and bus monitoring don't require local knx address
					final String lka = procComm.getSelection() || monitor.getSelection() ? "" : localKnxAddress.getText();
					args = ConnectArguments.newTpuart(p, lka, knxAddr.getText());
				}
				else if (!h.isEmpty()) {
					final InetAddress addr = parseIp(hostData);
					if (addr == null)
						return;
					// if no port is supplied for KNXnet/IP, we use default port
					final int port = p.isEmpty() ? KNXnetIPConnection.DEFAULT_PORT : Integer.parseInt(p);
					final var remote = new InetSocketAddress(addr, port);
					final boolean tcp = ipType.getSelectionIndex() == IpType.Tcp.ordinal();
					args = ConnectArguments.newKnxNetIP(useRouting(), local, remote, natChecked, tcp, knxAddr.getText(), null);
					if (access instanceof final DiscoverTab.IpAccess ipAccess) {
						args.serverIP = ipAccess.remote();
						args.secureServices = ipAccess.securedServices();
						args.serverIA = ipAccess.hostIA();
					}
					if (!localKnxAddress.getText().isEmpty())
						args.localKnxAddress = localKnxAddress.getText();
				}
				else
					args = ConnectArguments.newFT12(p, knxAddr.getText());
				args.name = n;
				args.knxMedium = access.medium();
				args.serialNumber = access.serialNumber();

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
					if (args.knxAddress.isEmpty()) {
						knxAddr.setFocus();
						knxAddr.setMessage("Enter address");
						return;
					}
					new MemoryEditor(tf, args);
				}
				else if (procComm.getSelection())
					new ProcCommTab(tf, args);
				else if (baos.getSelection())
					new BaosTab(tf, args);

				shell.dispose();
			}

			private boolean useRouting()
			{
				try {
					return InetAddress.getByName(hostData.getText()).isMulticastAddress();
				}
				catch (final UnknownHostException e) {}
				return false;
			}
		});

		final Button cancel = new Button(buttons, SWT.NONE);
		cancel.setLayoutData(new RowData());
		cancel.setText("Cancel");
		cancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				shell.dispose();
			}
		});

		shell.setDefaultButton(connect);
		hostData.setFocus();
		shell.pack();
		final String currentHost = hostData.getText();
		// calculate dialog size with a minimum text width for an IP numerical address
		hostData.setText("192.168.100.100");
		final Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		// set again previous content
		hostData.setText(currentHost);
		shell.setMinimumSize(size);
		shell.setSize(size);
		shell.open();
	}

	private static Text addHostInput(final Composite c, final String description, final InetAddress host,
		final boolean serial)
	{
		final Label label = new Label(c, SWT.NONE);
		label.setLayoutData(new GridData(SWT.LEFT, SWT.NONE, false, false));
		label.setText(description);
		if (serial)
			label.setEnabled(false);
		final Text data = new Text(c, SWT.BORDER);
		data.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		if (serial)
			data.setEnabled(false);
		else
			data.setText(host.getHostAddress());
		data.setToolTipText("IP address or host name");
		return data;
	}

	private InetAddress parseIp(final Text text) {
		try {
			if (!text.getText().isEmpty())
				return InetAddress.getByName(text.getText());
			return InetAddress.getByAddress(new byte[4]); // XXX OK?
		} catch (final UnknownHostException uhe) {
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

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

import static tuwien.auto.calimero.DataUnitBuilder.toHex;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.Keyring;
import tuwien.auto.calimero.Keyring.Interface;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments.Protocol;
import tuwien.auto.calimero.knxnetip.KNXnetIPConnection;
import tuwien.auto.calimero.knxnetip.SecureConnection;
import tuwien.auto.calimero.knxnetip.util.ServiceFamiliesDIB;
import tuwien.auto.calimero.link.medium.KNXMediumSettings;

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
		String serverIA;

		Protocol protocol;
		String remote;
		final String port;
		int knxMedium;

		final String local;
		private final boolean nat;
		private final IndividualAddress host;
		String serverIP;

		String localKnxAddress;
		int[] secureServices;

		static ConnectArguments newKnxNetIP(final boolean routing, final String localHost, final String remoteHost,
			final String port, final boolean nat, final String knxAddress, final IndividualAddress serverIA) {
			final var config = new ConnectArguments(routing ? Protocol.Routing : Protocol.Tunneling, localHost,
					remoteHost, port, nat, "", knxAddress);
			config.serverIA = serverIA != null ? serverIA.toString() : "";
			return config;
		}

		static ConnectArguments newUsb(final String port, final String knxAddress)
		{
			return new ConnectArguments(Protocol.USB, null, null, port, false, "", knxAddress);
		}

		static ConnectArguments newTpuart(final String port, final String localKnxAddress,
			final String remoteKnxAddress)
		{
			return new ConnectArguments(Protocol.Tpuart, null, null, port, false, localKnxAddress, remoteKnxAddress);
		}

		static ConnectArguments newFT12(final String port, final String knxAddress)
		{
			return new ConnectArguments(Protocol.FT12, null, null, port, false, "", knxAddress);
		}

		ConnectArguments(final Protocol p, final String local, final String remote,
			final String port, final boolean nat, final String localKnxAddress,
			final String remoteKnxAddress)
		{
			protocol = p;
			this.local = local;
			this.remote = remote;
			this.port = port;
			this.nat = nat;
			this.host = KNXMediumSettings.BackboneRouter;
			this.localKnxAddress = localKnxAddress;
			this.knxAddress = remoteKnxAddress;
		}

		ConnectArguments(final Protocol p, final String local, final String remote, final String port,
			final boolean nat, final IndividualAddress host, final String localKnxAddress,
			final String remoteKnxAddress) {
			protocol = p;
			this.local = local;
			this.remote = remote;
			this.port = port;
			this.nat = nat;
			this.host = host;
			this.localKnxAddress = localKnxAddress;
			this.knxAddress = remoteKnxAddress;
		}

		public boolean useNat()
		{
			return nat;
		}

		public void useServerIA() {
			knxAddress = serverIA;
		}

		public List<String> getArgs(final boolean useRemoteAddressOption)
		{
			final List<String> args = new ArrayList<String>();
			switch (protocol) {
			case Routing:
			case Tunneling:
				if (!local.isEmpty()) {
					args.add("--localhost");
					args.add(local);
				}
				args.add(remote);
				if (useNat())
					args.add("--nat");
				if (protocol == Protocol.Routing && isSecure(Protocol.Routing)) {
					String key = config("group.key", remote);
					if (key.isEmpty()) {
						final String[] tempKey = new String[1];
						Main.syncExec(() -> {
							final PasswordDialog dlg = new PasswordDialog(name, remote);
							if (dlg.show())
								tempKey[0] = dlg.groupKey();
						});
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
								args.add(dlg.isDeviceAuthHash() ? "--device-key" : "--device-auth-code");
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
				}

				if (!port.isEmpty())
					args.add("-p");
				break;
			case USB:
				args.add("--usb");
				break;
			case FT12:
				args.add("--ft12");
				break;
			case Tpuart:
				args.add("--tpuart");
				break;
			default:
				throw new IllegalStateException();
			}
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
			for (final int service : secureServices) {
				if (service == ServiceFamiliesDIB.DEVICE_MANAGEMENT && protocol == Protocol.DeviceManagement)
					return true;
				if (service == ServiceFamiliesDIB.TUNNELING && protocol == Protocol.Tunneling)
					return true;
				if (service == ServiceFamiliesDIB.ROUTING && protocol == Protocol.Routing)
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
				final Map<String, String> map = Files.lines(p).filter(s -> s.startsWith(key)).collect(Collectors
						.toMap((final String s) -> s.substring(0, s.indexOf("=")), (final String s) -> s.substring(s.indexOf("=") + 1)));
				final String value = map.getOrDefault(key, "");
				return value;
			}
			catch (IOException | RuntimeException e) {
				System.out.println(String.format("Failed to get value for '%s' from file '%s' (error: %s)", key, p, e.getMessage()));
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
				final var groupKey = (byte[]) keyring.configuration().get(remote);
				return toHex(keyring.decryptKey(groupKey, keyringPassword), "");
			}

			if (key.startsWith("device")) {
				final var devicePwd = keyring.devices().get(host).authentication();
				final char[] decryptPassword = keyring.decryptPassword(devicePwd, keyringPassword);
				if ("device.pwd".equals(key))
					return new String(decryptPassword);
				if ("device.key".equals(key))
					return toHex(SecureConnection.hashDeviceAuthenticationPassword(decryptPassword), "");
			}

			if (key.startsWith("user")) {
				final int user = Integer.parseInt(value);
				byte[] pwdData = null;
				if (user == 1) {
					final var device = keyring.devices().get(host);
					pwdData = device.password();
				}
				else {
					final var interfaces = keyring.interfaces().get(host);
					for (final Interface iface : interfaces) {
						if (iface.user() == user) {
							pwdData = iface.password();
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
						return new String(keyring.decryptPassword(iface.password(), keyringPassword));
				}
			}

			return null;
		}
	}

	private final int knxMedium;

	ConnectDialog(final CTabFolder tf, final Protocol protocol, final String localEP, final String name, final String host,
		final String port, final String mcast, final Integer medium, final boolean useNAT, final int[] secureServices,
		final boolean preferRouting, final IndividualAddress serverIA) {
		final Shell shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setLayout(new GridLayout());
		shell.setText("Open connection");

		final boolean confirm = name != null;
		final boolean serial = host == null;

		final Label nameLabel = new Label(shell, SWT.NONE);
		nameLabel.setFont(Main.font);
		nameLabel.setText(confirm ? "Name / ID: " + name
				: "Specify connection parameters.\nFor serial connections, leave the endpoints empty.");

		final Composite c = new Composite(shell, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		var localhost = "";
		try {
			localhost = InetAddress.getLocalHost().toString();
		}
		catch (final UnknownHostException uhe) {}

		final String local = localEP != null ? localEP : localhost;
		final Text localhostData = addHostInput(c, "Local endpoint:", local,  serial);
		final Text hostData = addHostInput(c, "Remote endpoint:", host, serial);

		final Label portLabel = new Label(c, SWT.NONE);
		portLabel.setLayoutData(new GridData(SWT.LEFT, SWT.NONE, false, false));
		portLabel.setText(confirm ? serial ? "Serial port ID:" : "UDP port:" : "Port or ID:");

		final Text portData = new Text(c, SWT.BORDER);
		portData.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		portData.setText(port);

		knxMedium = medium == null ? KNXMediumSettings.MEDIUM_TP1 : medium;

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

		final Button nat = new Button(c, SWT.CHECK);
		nat.setText("Use NAT aware connection");
		nat.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		if (serial)
			nat.setEnabled(false);
		else {
			nat.setToolTipText("Some KNXnet/IP devices do not support this connection mode");
			nat.setSelection(useNAT);
		}
		// spacer to the right of NAT checkbox
		final Button routing = new Button(c, SWT.CHECK);
		routing.setText("Use routing");
		routing.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		routing.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				if (routing.getSelection() && mcast != null)
					hostData.setText(mcast);
				if (!routing.getSelection())
					hostData.setText(host);
			}
		});
		routing.setSelection(preferRouting);
		routing.notifyListeners(SWT.Selection, new Event());

		final Label configKNXAddress = new Label(c, SWT.NONE);
		configKNXAddress.setText("KNX device address (optional): ");
		final Text knxAddr = new Text(c, SWT.BORDER);
		knxAddr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		knxAddr.setMessage("area.line.device");
		knxAddr.setToolTipText("Specify device address for\n"
				+ "  \u2022 reading remote device info\n"
				+ "  \u2022 opening remote property/memory editor\n"
				+ "Scan devices:\n"
				+ "  \u2022 specify area for scanning an area\n"
				+ "  \u2022 specify area.line for scanning a line\n"
				+ "  \u2022 specify area.line.device to scan a single device");

		final Composite mode = new Composite(shell, SWT.NONE);
		final RowLayout col = new RowLayout(SWT.VERTICAL);
		col.fill = true;
		col.wrap = false;
		mode.setLayout(col);

		final Button tunnel = new Button(mode, SWT.RADIO);
		tunnel.setText("Process communication / group monitor");
		tunnel.setSelection(true);

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

		usb.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final boolean enabled = !usb.getSelection();
				localhostData.setEnabled(enabled);
				hostData.setEnabled(enabled);
				nat.setEnabled(enabled);
				tpuart.setEnabled(enabled);
				routing.setEnabled(enabled);
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
				nat.setEnabled(enabled);
				usb.setEnabled(enabled);
				routing.setEnabled(enabled);
			}
		});

		if (protocol == Protocol.USB) {
			usb.setSelection(true);
			// programmatically setting a selection does not invoke selection listeners :(
			usb.notifyListeners(SWT.Selection, new Event());
		}
		else if (protocol == Protocol.Tpuart) {
			tpuart.setSelection(true);
			// programmatically setting a selection does not invoke selection listeners :(
			tpuart.notifyListeners(SWT.Selection, new Event());
		}
		else if (protocol == Protocol.Tunneling) {
			usb.setEnabled(false);
			tpuart.setEnabled(false);
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
				final String local = localhostData.getText();
				final String h = hostData.getText();
				String p = portData.getText();
				if (h.isEmpty() && p.isEmpty())
					return;
				final String n = confirm ? name : h.isEmpty() ? p : h;
				// if no port is supplied for KNXnet/IP, we use default port
				if (p.isEmpty())
					p = Integer.toString(KNXnetIPConnection.DEFAULT_PORT);
				final boolean natChecked = serial ? false : nat.getSelection();

				ConnectArguments args;
				if (usb.getSelection())
					args = ConnectArguments.newUsb(p, knxAddr.getText());
				else if (tpuart.getSelection()) {
					// process communication and bus monitoring don't require local knx address
					final String lka = tunnel.getSelection() || monitor.getSelection() ? "" : localKnxAddress.getText();
					args = ConnectArguments.newTpuart(p, lka, knxAddr.getText());
				}
				else if (!h.isEmpty()) {
					args = ConnectArguments.newKnxNetIP(useRouting(), local, h, p, natChecked, knxAddr.getText(), serverIA);
					if (!localKnxAddress.getText().isEmpty())
						args.localKnxAddress = localKnxAddress.getText();
					args.serverIP = host;
					args.secureServices = secureServices;
				}
				else
					args = ConnectArguments.newFT12(p, knxAddr.getText());
				args.name = n;
				args.knxMedium = knxMedium;

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
				else
					new TunnelTab(tf, args);

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

	private static Text addHostInput(final Composite c, final String description, final String host,
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
			data.setText(host);
		data.setToolTipText("IP address or host name");
		return data;
	}
}

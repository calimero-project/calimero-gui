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

package io.calimero.gui;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.calimero.IndividualAddress;
import io.calimero.KNXFormatException;
import io.calimero.KnxRuntimeException;
import io.calimero.gui.DiscoverTab.IpAccess;
import io.calimero.gui.DiscoverTab.SerialAccess;
import io.calimero.knxnetip.SecureConnection;
import io.calimero.knxnetip.util.ServiceFamiliesDIB.ServiceFamily;
import io.calimero.link.medium.KNXMediumSettings;
import io.calimero.secure.Keyring;
import io.calimero.secure.Keyring.Interface;

final class ConnectArguments {
	public enum Protocol {
		Unknown, DeviceManagement, Tunneling, Routing, FT12, USB, Tpuart
	}

	private final DiscoverTab.Access access;
	private boolean ignoreRouting;

	private final boolean nat;
	private final boolean tcp;

	String localKnxAddress;
	String remoteKnxAddress;

	ConnectArguments(final IpAccess access, final boolean nat, final boolean tcp, final String localKnxAddress,
			final String remoteKnxAddress) {
		this.access = access;
		this.nat = nat;
		this.tcp = tcp;
		this.localKnxAddress = localKnxAddress;
		this.remoteKnxAddress = remoteKnxAddress;
	}

	ConnectArguments(final SerialAccess access, final String localKnxAddress, final String remoteKnxAddress) {
		this.access = access;
		this.localKnxAddress = localKnxAddress;
		this.remoteKnxAddress = remoteKnxAddress;

		this.nat = false;
		this.tcp = false;
	}

	DiscoverTab.Access access() { return access; }

	public boolean useNat()
	{
		return nat;
	}

	public void ignoreRoutingProtocol() {
		ignoreRouting = true;
	}

	// if prefer routing option is set but no knx address is given, we use the server knx address
	ConnectArguments adjustPreferRoutingConfig() {
		if (access instanceof final IpAccess ipAccess && access.protocol() == Protocol.Routing && remoteKnxAddress.isEmpty())
			remoteKnxAddress = ipAccess.hostIA().toString();
		return this;
	}

	public String friendlyName() {
		if (!remoteKnxAddress.isEmpty())
			return (remoteKnxAddress.split("\\.").length < 3 ? "line " : "device ") + remoteKnxAddress;
		if (access instanceof final IpAccess ipAccess)
			return "interface " + ipAccess.remote() + (useNat() ? " (UDP/NAT)" : "") + (tcp ? " (TCP)" : "");
		if (access instanceof final SerialAccess serAccess)
			return "interface " + serAccess.port();
		return access.name();
	}

	public String id() {
		if (remoteKnxAddress != null && !remoteKnxAddress.isEmpty())
			return remoteKnxAddress;
		if (access instanceof final IpAccess ipAccess)
			return ipAccess.remote().getAddress().getHostAddress();
		if (access instanceof final SerialAccess serAccess)
			return serAccess.port();
		return access.name();
	}

	public List<String> getArgs(final boolean useRemoteAddressOption)
	{
		// make sure keyring is decrypted in case remote device requires data secure
		if (remoteKnxAddress != null && !remoteKnxAddress.isEmpty()) {
			try {
				final var device = new IndividualAddress(remoteKnxAddress);
				KeyringTab.keyring().map(Keyring::devices).filter(devices -> devices.containsKey(device))
						.ifPresent(__ -> KeyringTab.keyringPassword());
			}
			catch (final KNXFormatException e) {
				e.printStackTrace();
			}
		}

		final List<String> args = new ArrayList<>();

		var protocol = access.protocol();
		if (protocol == Protocol.Routing && ignoreRouting)
			protocol = Protocol.Tunneling;
		switch (protocol) {
			case Routing, Tunneling -> {
				final var ipAccess = (IpAccess) access;
				args.add("--localhost");
				args.add(ipAccess.localEP().getAddress().getHostAddress());

				final InetAddress addr = ipAccess.remote().getAddress();
				args.add(addr.getHostAddress());
				if (access.protocol() == Protocol.Tunneling) {
					if (useNat()) args.add("--nat");
					if (tcp) args.add("--tcp");
				}
				if (access.protocol() == Protocol.Routing && isSecure(Protocol.Routing)) {
					String key = config("group.key", addr.getHostAddress());
					if (key.isEmpty()) {
						final String[] tempKey = new String[1];
						Main.syncExec(() -> {
							final PasswordDialog dlg = new PasswordDialog(access.name(), addr);
							if (dlg.show()) tempKey[0] = dlg.groupKey();
						});
						if (tempKey[0] == null) // canceled
							throw new KnxRuntimeException("no group key entered");
						key = tempKey[0];
					}
					args.add("--group-key");
					args.add(key);
				}
				else if (access.protocol() == Protocol.Tunneling && isSecure(Protocol.Tunneling)) {
					final String user = "" + KeyringTab.user();
					final String userPwd = config("user." + user, user);
					if (userPwd.isEmpty()) {
						final PasswordDialog dlg = new PasswordDialog(access.name(), true);
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
				args.add("" + ipAccess.remote().getPort());
			}
			case USB -> args.add("--usb");
			case FT12 -> args.add("--ft12");
			case Tpuart -> args.add("--tpuart");
			default -> throw new IllegalStateException();
		}
		if (access instanceof final SerialAccess serAccess)
			args.add(serAccess.port());
		if (access.medium() != 0) {
			args.add("--medium");
			args.add(KNXMediumSettings.getMediumString(access.medium()));
		}
		if (!localKnxAddress.isEmpty()) {
			args.add("--knx-address");
			args.add(localKnxAddress);
		}
		if (!remoteKnxAddress.isEmpty()) {
			if (useRemoteAddressOption)
				args.add("-r");
			args.add(remoteKnxAddress);
		}
		return args;
	}

	boolean isSecure(final ConnectArguments.Protocol protocol) {
		final IpAccess ipAccess = (IpAccess) access;
		for (final var service : ipAccess.securedServices().keySet()) {
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

		final var hostIA = access instanceof final IpAccess ipAccess ? ipAccess.hostIA() : null;
		if (key.startsWith("device")) {
			final var pwd = Optional.ofNullable(keyring.devices().get(hostIA))
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
				final var device = keyring.devices().get(hostIA);
				pwdData = device.password().orElse(null);
			}
			else {
				final var interfaces = keyring.interfaces().get(hostIA);
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
			final var interfaces = keyring.interfaces().get(hostIA);
			final IndividualAddress ia = new IndividualAddress(value);
			for (final Interface iface : interfaces) {
				if (iface.address().equals(ia))
					return new String(keyring.decryptPassword(iface.password().get(), keyringPassword));
			}
		}

		return null;
	}
}
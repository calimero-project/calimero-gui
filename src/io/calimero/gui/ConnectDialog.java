/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2006, 2025 B. Malinowsky

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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;

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
	ConnectDialog(final CTabFolder tf, final DiscoverTab.Access access, final boolean useNAT, final boolean preferRouting,
			final boolean preferTcp) {
		final Shell shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setLayout(new GridLayout());
		shell.setText("Open connection");

		final boolean confirm = !access.name().isEmpty();
		final boolean serial = access instanceof SerialAccess;

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
		catch (final UnknownHostException ignore) {}

		final Text hostData;
		final Text localhostData;
		if (access instanceof final IpAccess ipAccess) {
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
		if (access instanceof final SerialAccess serialAccess)
			portData.setText(serialAccess.port());
		else if (access instanceof final IpAccess ipAccess)
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
					if (access instanceof final IpAccess ipAccess && ipAccess.multicast().isPresent())
						hostData.setText(ipAccess.multicast().get().getAddress().getHostAddress());
					else {
						// if no valid multicast address, set to default knx multicast group
						try {
							if (!KNXnetIPRouting.isValidRoutingMulticast(InetAddress.getByName(hostData.getText())))
								hostData.setText(KNXnetIPRouting.DEFAULT_MULTICAST);
						} catch (final UnknownHostException ex) {
							hostData.setText(KNXnetIPRouting.DEFAULT_MULTICAST);
						}
					}
				} else if (access instanceof final IpAccess ipAccess)
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
				portLabel.setText(enabled ? "UDP port:" : "USB port or ID:");
				portLabel.requestLayout();
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
				portLabel.setText(enabled ? "UDP port:" : "Serial port ID:");
				portLabel.requestLayout();
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
				if (usb.getSelection()) {
					final String knxAddress = knxAddr.getText();
					args = new ConnectArguments(new SerialAccess(Protocol.USB, n, access.medium(), p,
							access.serialNumber()), "", knxAddress);
				}
				else if (tpuart.getSelection()) {
					// process communication and bus monitoring don't require local knx address
					final String lka = procComm.getSelection() || monitor.getSelection() ? "" : localKnxAddress.getText();
					final String remoteKnxAddress = knxAddr.getText();
					args = new ConnectArguments(new SerialAccess(Protocol.Tpuart, n, access.medium(), p,
							access.serialNumber()), lka, remoteKnxAddress);
				}
				else if (!h.isEmpty()) {
					final InetAddress addr = parseIp(hostData);
					if (addr == null)
						return;

					// if no/invalid port is supplied for KNXnet/IP, we use default port
					int port;
					try {
						port = Integer.parseUnsignedInt(p);
					} catch (final NumberFormatException ex) {
						port = KNXnetIPConnection.DEFAULT_PORT;
					}

					final var remote = new InetSocketAddress(addr, port);
					final boolean tcp = ipType.getSelectionIndex() == IpType.Tcp.ordinal();

					var mcast = Optional.<InetSocketAddress>empty();
					Map<ServiceFamily, Integer> securedServices = Map.of();
					IndividualAddress hostIA = null;
					var protocol = useRouting();
					if (access instanceof final IpAccess ipAccess) {
						mcast = ipAccess.multicast();
						securedServices = ipAccess.securedServices();
						hostIA = ipAccess.hostIA();
					}
					else if (protocol == Protocol.Routing)
						mcast = Optional.of(remote);

					final String knxAddress = knxAddr.getText();
					args = new ConnectArguments(new IpAccess(protocol, n, access.medium(),
							new InetSocketAddress(local, 0), remote, mcast,
							securedServices, hostIA, access.serialNumber()), natChecked, tcp, "", knxAddress);
					if (!localKnxAddress.getText().isEmpty())
						args.localKnxAddress = localKnxAddress.getText();
				}
				else {
					final String knxAddress = knxAddr.getText();
					args = new ConnectArguments(
							new SerialAccess(Protocol.FT12, n, access.medium(), p, access.serialNumber()), "", knxAddress);
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

			private Protocol useRouting()
			{
				try {
					return InetAddress.getByName(hostData.getText()).isMulticastAddress()
							? Protocol.Routing : Protocol.Tunneling;
				}
				catch (final UnknownHostException ignore) {}
				return Protocol.Tunneling;
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
			return InetAddress.getByAddress(new byte[4]);
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

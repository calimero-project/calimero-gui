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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import tuwien.auto.calimero.knxnetip.KNXnetIPConnection;

/**
 * @author B. Malinowsky
 */
class ConnectDialog
{
	ConnectDialog(final CTabFolder tf, final String name, final String host, final String port,
		final String mcast, final boolean useNAT)
	{
		final Shell shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setLayout(new GridLayout());
		shell.setText("Open connection");

		final boolean confirm = name != null;
		final boolean serial = host == null;

		final Label nameLabel = new Label(shell, SWT.NONE);
		nameLabel.setFont(Main.font);
		nameLabel.setText(confirm ? "Name or ID: " + name
			: "Specify connection parameters.\nFor serial connections, "
				+ "leave the host empty.");

		final Composite c = new Composite(shell, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final Text localhostData = addHostInput(c, "Local host:", Main.getLocalHost(), serial);
		final Text hostData = addHostInput(c, "IP address or host name:", host, serial);

		final Label portLabel = new Label(c, SWT.NONE);
		portLabel.setLayoutData(new GridData(SWT.LEFT, SWT.NONE, false, false));
		portLabel.setText(confirm ? serial ? "Serial port ID:" : "UDP port:" : "Port or ID:");

		final Text portData = new Text(c, SWT.BORDER);
		portData.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		portData.setText(port);

		final Button nat = new Button(c, SWT.CHECK);
		nat.setText("Use NAT aware connection");
		nat.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		if (serial)
			nat.setEnabled(false);
		else {
			nat.setToolTipText("Some KNXnet/IP devices do not support this mode of connection");
			nat.setSelection(useNAT);
		}

		// spacer to the right of NAT checkbox
		new Label(c, SWT.NONE);

		final Label configKNXAddress = new Label(c, SWT.NONE);
		configKNXAddress.setText("KNX device address (optional): ");
		final Text knxAddr = new Text(c, SWT.BORDER);
		knxAddr.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		final Composite mode = new Composite(shell, SWT.NONE);
		final RowLayout col = new RowLayout(SWT.VERTICAL);
		col.fill = true;
		col.wrap = false;
		mode.setLayout(col);

		final Button tunnel = new Button(mode, SWT.RADIO);
		tunnel.setText("Process communication/monitor (Tunneling)");
		tunnel.setSelection(true);
		final Button routing = new Button(mode, SWT.RADIO);
		routing.setText("Process communication/monitor (Routing)");
		if (serial)
			routing.setEnabled(false);
		else {
			routing.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(final SelectionEvent e)
				{
					if (routing.getSelection() && mcast != null)
						hostData.setText(mcast);
					if (!routing.getSelection())
						hostData.setText(host);
				}
			});
		}
		final Button monitor = new Button(mode, SWT.RADIO);
		monitor.setText("Network monitor (Tunneling)");
		final Button config = new Button(mode, SWT.RADIO);
		config.setText("Configure KNXnet/IP");

		final Button scan = new Button(mode, SWT.RADIO);
		scan.setText("Scan for KNX devices");
		scan.setToolTipText("Requires a KNX area.line or KNX device address (hex)");

		final Button devinfo = new Button(mode, SWT.RADIO);
		devinfo.setText("Read KNX device information");
		devinfo.setToolTipText("Requires a KNX device address");

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
		connect.addSelectionListener(new SelectionAdapter()
		{
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
				if (monitor.getSelection())
					new MonitorTab(tf, n, local, h, p, natChecked);
				else if (config.getSelection())
					new IPConfigTab(tf, n, local, h, p, natChecked, knxAddr.getText());
				else if (scan.getSelection())
					new ScanDevicesTab(tf, n, local, h, p, natChecked, knxAddr.getText());
				else if (devinfo.getSelection())
					new DeviceInfoTab(tf, n, local, h, p, natChecked, knxAddr.getText());
				else if (tunnel.getSelection())
					new TunnelTab(tf, n, local, h, p, natChecked, false);
				else if (routing.getSelection())
					new TunnelTab(tf, n, local, h, p, natChecked, true);
				shell.dispose();
			}
		});

		final Button cancel = new Button(buttons, SWT.NONE);
		cancel.setLayoutData(new RowData());
		cancel.setText("Cancel");
		cancel.addSelectionListener(new SelectionAdapter()
		{
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

	private Text addHostInput(final Composite c, final String description, final String host,
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
		return data;
	}
}

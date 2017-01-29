/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2017 B. Malinowsky

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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.GroupAddress.Presentation;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments.Protocol;

/**
 * @author B. Malinowsky
 */
public class Main
{
	static Display display;
	static Shell shell;
	static Font font;

	private static Combo localInterfaces;
	private final CTabFolder tf;

	Main()
	{
		shell.setSize(300, 200);
		shell.setText("Calimero - Open Source KNX Access with Java");
		shell.setLayout(new GridLayout());

		final ToolBar header = new ToolBar(shell, SWT.FLAT | SWT.WRAP);
		final ToolItem connect = new ToolItem(header, SWT.NONE);
		connect.setText("Connect ...");

		final ToolItem showLog = new ToolItem(header, SWT.NONE);
		showLog.setText("Show log");
		showLog.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				new LogTab(tf);
			}
		});

		final ToolItem about = new ToolItem(header, SWT.NONE);
		about.setText("About");
		about.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				new About(shell);
			}
		});

		// use separator to start area for local host fields
		new ToolItem(header, SWT.SEPARATOR);

		// add local host label, use a composite to vertically center the label
		final Composite comp = new Composite(header, SWT.NONE);
		final GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.makeColumnsEqualWidth = true;
		comp.setLayout(layout);

		final Label localHostLabel = new Label(comp, SWT.NONE);
		localHostLabel.setFont(header.getFont());
		localHostLabel.setText("Local network interface:");
		final GridData gridData = new GridData();
		gridData.verticalAlignment = SWT.CENTER;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = SWT.CENTER;
		localHostLabel.setLayoutData(gridData);

		// set composite (with label) by using a separator item
		final ToolItem labelItem = new ToolItem(header, SWT.SEPARATOR);
		labelItem.setControl(comp);
		Point size = comp.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		labelItem.setWidth(size.x);

		localInterfaces = new Combo(header, SWT.BORDER);
		try {
			localInterfaces.setText(InetAddress.getLocalHost().getHostAddress());
			Collections.list(NetworkInterface.getNetworkInterfaces()).forEach(ni -> Collections
					.list(ni.getInetAddresses()).stream().filter(ip -> ip instanceof Inet4Address).forEach(ip -> {
						localInterfaces.add(ip.getHostAddress());
						localInterfaces.setData(ip.getHostAddress(), ni.getName() + ": " + ip.getHostAddress());
					}));
		}
		catch (UnknownHostException | SocketException e) {}
		localInterfaces.setToolTipText((String) localInterfaces.getData(localInterfaces.getText()));
		localInterfaces.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				localInterfaces.setToolTipText((String) localInterfaces.getData(localInterfaces.getText()));
			}
		});
		size = localInterfaces.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		final ToolItem hostItem = new ToolItem(header, SWT.SEPARATOR);
		hostItem.setControl(localInterfaces);
		hostItem.setWidth(size.x + 100);

		new ToolItem(header, SWT.SEPARATOR);

		// Let user choose group address presentation
		// add description label, use a composite to vertically center the label
		final Composite compAddressStyle = new Composite(header, SWT.NONE);
		final GridLayout layout2 = new GridLayout();
		layout2.numColumns = 1;
		layout2.makeColumnsEqualWidth = true;
		compAddressStyle.setLayout(layout2);
		final Label labelAddressStyle = new Label(compAddressStyle, SWT.NONE);
		labelAddressStyle.setFont(header.getFont());
		labelAddressStyle.setText("Group address presentation:");
		final GridData gridData2 = new GridData();
		gridData2.verticalAlignment = SWT.CENTER;
		gridData2.grabExcessVerticalSpace = true;
		gridData2.horizontalAlignment = SWT.CENTER;
		labelAddressStyle.setLayoutData(gridData2);

		final ToolItem addrStyleItem = new ToolItem(header, SWT.SEPARATOR);
		addrStyleItem.setControl(compAddressStyle);
		addrStyleItem.setWidth(compAddressStyle.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);

		// selection of available address formats
		final Combo addressStyles = new Combo(header, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
		addressStyles.setItems("3-level style", "2-level style", "Free style");
		addressStyles.select(0);
		addressStyles.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				if (addressStyles.getSelectionIndex() == 0)
					GroupAddress.addressStyle(Presentation.ThreeLevelStyle);
				else if (addressStyles.getSelectionIndex() == 1)
					GroupAddress.addressStyle(Presentation.TwoLevelStyle);
				else
					GroupAddress.addressStyle(Presentation.FreeStyle);
			}
		});

		final ToolItem addrStylesItem = new ToolItem(header, SWT.SEPARATOR);
		addrStylesItem.setControl(addressStyles);
		addrStylesItem.setWidth(addressStyles.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);

		new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(SWT.FILL,
				SWT.NONE, true, false));

		tf = new CTabFolder(shell, SWT.NONE | SWT.CAP_ROUND | SWT.BORDER);
		tf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tf.setUnselectedCloseVisible(false);
		tf.setSimple(false);
		tf.setMRUVisible(true);
		tf.setFont(font);
		tf.setSelectionBackground(
				new Color[] { display.getSystemColor(SWT.COLOR_WHITE),
					display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND), }, new int[] { 75 }, true);
		connect.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				new ConnectDialog(tf, Protocol.Unknown, null, null, "", "", null, null, false);
			}
		});

		new DiscoverTab(tf);
		shell.pack();
		shell.setSize(shell.getSize().x + 150, shell.getSize().y + 100);
		shell.open();
		while (!shell.isDisposed())
			if (!display.readAndDispatch())
				display.sleep();
	}

	/**
	 * The main entry routine of the GUI.
	 *
	 * @param args none expected
	 */
	public static void main(final String[] args)
	{
		display = new Display();
		try {
			shell = new Shell(display);
			final FontData[] fontData = shell.getFont().getFontData();
			final String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
			if (os.indexOf("windows") == -1) {
				int height = fontData[0].getHeight();
				height -= 0.15 * height;
				fontData[0].setHeight(height);
			}
			font = new Font(Main.display, fontData);
			new Main();
		}
		catch (final Throwable e) {
			System.setErr(BaseTabLayout.oldSystemErr);
			e.printStackTrace();
		}
		finally {
			display.dispose();
		}
	}

	static String getLocalHost()
	{
		return localInterfaces.getText();
	}

	static void asyncExec(final Runnable task)
	{
		if (display.isDisposed())
			return;
		display.asyncExec(task);
	}

	static void syncExec(final Runnable task)
	{
		if (display.isDisposed())
			return;
		display.syncExec(task);
	}
}

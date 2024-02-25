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

import java.util.Locale;
import java.util.Optional;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.GroupAddress.Presentation;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;

/**
 * @author B. Malinowsky
 */
public class Main
{
	static Display display;
	static Shell shell;
	static Font font;

	private final CTabFolder tf;
	private final DiscoverTab discoverTab;
	private Text address;

	Main()
	{
		shell.setSize(300, 200);
		shell.setText("Calimero - Open Source KNX Access with Java");
		shell.setLayout(new GridLayout());

		final ToolBar header = new ToolBar(shell, SWT.FLAT | SWT.WRAP);
		new Label(shell, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData(new GridData(SWT.FILL,
				SWT.NONE, true, false));
		addLauncherBar();
		tf = new CTabFolder(shell, SWT.NONE | SWT.BORDER);

		addToolItem(header, "Connect ...", () -> new ConnectDialog(tf, DiscoverTab.UnknownAccess, discoverTab().nat.getSelection(),
				discoverTab().preferRouting.getSelection(), discoverTab().preferTcp.getSelection()));
		addToolItem(header, "Show log", () -> new LogTab(tf));
		addToolItem(header, "Show keyring", () -> new KeyringTab(tf));
		addToolItem(header, "Show projects", () -> new ProjectTab(tf));
		addToolItem(header, "About", () -> new About(shell));

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
		addressStyles.addSelectionListener(widgetSelected(() -> {
			if (addressStyles.getSelectionIndex() == 0)
				GroupAddress.addressStyle(Presentation.ThreeLevelStyle);
			else if (addressStyles.getSelectionIndex() == 1)
				GroupAddress.addressStyle(Presentation.TwoLevelStyle);
			else
				GroupAddress.addressStyle(Presentation.FreeStyle);
		}));

		final ToolItem addrStylesItem = new ToolItem(header, SWT.SEPARATOR);
		addrStylesItem.setControl(addressStyles);
		addrStylesItem.setWidth(addressStyles.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);

		tf.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tf.setUnselectedCloseVisible(false);
		tf.setSimple(true);
		tf.setMRUVisible(true);
		tf.setFont(font);
		tf.setSelectionBackground(
				new Color[] { display.getSystemColor(SWT.COLOR_WHITE),
					display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND), }, new int[] { 75 }, true);

		discoverTab = new DiscoverTab(tf);
		shell.pack();
		shell.setSize(shell.getSize().x + 150, shell.getSize().y + 100);
		shell.open();

		KeyringTab.keyring();

		ProjectTab.findProjects();

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
		// use current system theme on macOS, i.e., light/dark appearance
		System.setProperty("org.eclipse.swt.display.useSystemTheme", "true");
		display = new Display();
		try {
			shell = new Shell(display);
			final FontData[] fontData = shell.getFont().getFontData();
			final String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
			if (!os.contains("windows")) {
				int height = fontData[0].getHeight();
				height -= (int) (0.15 * height);
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
			System.setErr(BaseTabLayout.oldSystemErr);
			display.dispose();
		}
	}

	private DiscoverTab discoverTab() { return discoverTab; }

	private void addLauncherBar()
	{
		final ToolBar functions = new ToolBar(shell, SWT.FLAT);
		addToolItem(functions, "Group Monitor", () -> new ProcCommTab(tf, ofDefaultInterface()));
		addToolItem(functions, "Network Monitor", () -> new MonitorTab(tf, ofDefaultInterface()));
		addToolItem(functions, "Programming Mode", () -> new ProgmodeTab(tf, ofDefaultInterface()));
		addToolItem(functions, "Scan Devices", () -> new ScanDevicesTab(tf, ofDefaultInterface()));
		addToolItem(functions, "Device Info", () -> new DeviceInfoTab(tf, ofDefaultInterface()));
		addToolItem(functions, "IP Config", () -> new IPConfigTab(tf, ofDefaultInterface()));
		addToolItem(functions, "Property Editor", () -> new PropertyEditorTab(tf, ofDefaultInterface()));
		addToolItem(functions, "Memory Editor", () -> new MemoryEditor(tf, ofDefaultInterface()));
		addToolItem(functions, "BAOS View", () -> new BaosTab(tf, ofDefaultInterface()));

		new ToolItem(functions, SWT.SEPARATOR_FILL).setEnabled(false);

		addToolbarLabel(functions, "Device Address:");
		address = new Text(functions, SWT.CENTER);
		address.setMessage("area.line.dev");
		address.setToolTipText("""
				Specify device address for
				  • reading remote device info
				  • opening remote property/memory editor
				Scan devices:
				  • specify area for scanning an area
				  • specify area.line for scanning a line
				  • specify area.line.device to scan a single device""");
		address.setText("XXX.XXX.XXX");
		final ToolItem item = addNonToolItem(functions, address);
		item.setWidth(address.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x);
		address.setText("");
		address.addListener(SWT.Verify, e -> {
			final String string = e.text;
			final char[] chars = new char[string.length()];
			string.getChars(0, chars.length, chars, 0);
			for (final char c : chars) {
				if (!(('0' <= c && c <= '9') || c == '.')) {
					e.doit = false;
					return;
				}
			}
		});
	}

	private ToolItem addNonToolItem(final ToolBar tb, final Control t) {
		final ToolItem item = new ToolItem(tb, SWT.SEPARATOR);
		final Composite c = new Composite(tb, SWT.NONE);
		t.setParent(c);
		item.setControl(c);
		final GridLayout layout = new GridLayout(1, true);
		layout.horizontalSpacing = 0;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		c.setLayout(layout);
		t.setFont(tb.getFont());
		t.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true));
		item.setWidth(t.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);
		return item;
	}

	private Label addToolbarLabel(final ToolBar tb, final String text)
	{
		final Label l = new Label(tb, SWT.NONE);
		l.setText(text);
		addNonToolItem(tb, l);
		return l;
	}

	private void addToolItem(final ToolBar tb, final String text, final Runnable selected)
	{
		final ToolItem item = new ToolItem(tb, SWT.NONE);
		item.setText(text);
		item.addSelectionListener(widgetSelected(selected));
	}

	private SelectionListener widgetSelected(final Runnable r)
	{
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					r.run();
				}
				catch (final RuntimeException rte) {
					discoverTab.asyncAddLog(rte.getMessage());
				}
			}
		};
	}

	private ConnectArguments ofDefaultInterface()
	{
		final Optional<ConnectArguments> args = discoverTab.defaultInterface();

		// TODO we don't have a provider for the local knx address
		final String localKnxAddress = "";
		args.ifPresent(ca -> ca.knxAddress = address.getText());
		return args.orElseThrow(() -> new RuntimeException("Discover and check default interface first!"));
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

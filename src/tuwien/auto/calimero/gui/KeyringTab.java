/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2019, 2023 B. Malinowsky

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringJoiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.secure.Keyring;
import tuwien.auto.calimero.secure.Security;

class KeyringTab extends BaseTabLayout {
	private Button load;
	private Label keyringLabel;

	static String keyringResource = "";
	private static Keyring keyring;
	private static char[] keyringPassword = new char[0];

	private static IndividualAddress address = new IndividualAddress(0);
	private static int user = 1;

	static Optional<Keyring> keyring() {
		if (keyring == null) {
			try {
				Files.list(Path.of(".")).map(path -> path.toAbsolutePath().normalize().toString())
						.filter(path -> path.endsWith(".knxkeys")).findFirst().ifPresent(KeyringTab::loadKeyring);
			}
			catch (final IOException | RuntimeException e) {
				System.out.println("Keyring lookup: " + e.getMessage());
			}
		}
		return Optional.ofNullable(keyring);
	}

	static char[] keyringPassword() {
		if (keyringPassword.length == 0 && keyring != null) {
			final var dlg = PasswordDialog.forKeyring(Path.of(keyringResource));
			if (dlg.show()) {
				keyringPassword = dlg.keyringPassword();
				Security.defaultInstallation().useKeyring(keyring, keyringPassword);
			}
		}
		return keyringPassword;
	}

	static int user() {
		return user;
	}

	static IndividualAddress tunnelingAddress() { return address; }

	KeyringTab(final CTabFolder tf) {
		super(tf, "Keyring", "Available interfaces in");

		log.dispose();

		final Composite parent = list.getParent();
		final int style = list.getStyle();
		final Sash bottom = (Sash) ((FormData) list.getLayoutData()).bottom.control;
		list.dispose();
		list = newTable(parent, style | SWT.CHECK | SWT.SINGLE, bottom);
		list.addSelectionListener(selected(this::updateInterfaceSelection));
		final var dropTarget = new DropTarget(list, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK | DND.DROP_DEFAULT);
		dropTarget.setTransfer(TextTransfer.getInstance(), FileTransfer.getInstance());
		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void dragEnter(final DropTargetEvent event) {
				event.detail = isSupportedType(event) ? DND.DROP_LINK : DND.DROP_NONE;
			}

			@Override
			public void drop(final DropTargetEvent event) { dataDropped(event); }
		});


		final TableColumn host = new TableColumn(list, SWT.RIGHT);
		host.setText("Host interface");
		host.setWidth(90);
		final TableColumn iface = new TableColumn(list, SWT.RIGHT);
		iface.setText("Tunneling address");
		iface.setWidth(120);
		final TableColumn user = new TableColumn(list, SWT.RIGHT);
		user.setText("User");
		user.setWidth(50);
		final TableColumn groups = new TableColumn(list, SWT.LEFT);
		groups.setText("Groups");
		groups.setWidth(400);

		populateList();

		workArea.layout(true, true);
	}

	@Override
	protected void initTableBottom(final Composite parent, final Sash sash) {
		((FormData) sash.getLayoutData()).top = new FormAttachment(100);
		sash.setEnabled(false);
	}

	@Override
	protected void initWorkAreaTop() {
		super.initWorkAreaTop();

		((GridLayout) top.getLayout()).numColumns = 3;
		((GridLayout) top.getLayout()).makeColumnsEqualWidth = false;
		((GridLayout) top.getLayout()).horizontalSpacing = ((GridLayout) top.getLayout()).horizontalSpacing;

		keyringLabel = new Label(top, SWT.NONE);
		keyringLabel.setText(keyringResource);

		load = new Button(top, SWT.NONE);
		load.setText("Load keyring ...");
		load.addSelectionListener(selected(this::loadKeyring));
		load.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
	}

	private void updateInterfaceSelection(final SelectionEvent e) {
		if (e.detail == SWT.CHECK) {
			for (final TableItem item : list.getItems())
				if (item != e.item)
					item.setChecked(false);
			final TableItem current = (TableItem) e.item;

			if (current.getChecked()) {
				user = Integer.parseInt(current.getText(2));
				try {
					address = new IndividualAddress(current.getText(1));
				}
				catch (final KNXFormatException kfe) {
					asyncAddLog(kfe);
				}
			}
			else {
				user = 1;
				address = new IndividualAddress(0);
			}
		}
	}

	private void loadKeyring(final SelectionEvent e) {
		final FileDialog dlg = new FileDialog(Main.shell, SWT.OPEN);
		dlg.setFilterNames(new String[] { "KNX Keyring Files (*.knxkeys)", "All Files (*.*)" });
		dlg.setFilterExtensions(new String[] { "*.knxkeys", "*.*" });
		final var resource = dlg.open();
		if (resource == null)
			return;
		setKeyring(resource);
	}

	private boolean isSupportedType(final DropTargetEvent event) {
		return FileTransfer.getInstance().isSupportedType(event.currentDataType);
	}

	private void dataDropped(final DropTargetEvent event) {
		if (isSupportedType(event) && event.data instanceof String[]) {
			final String[] paths = (String[]) event.data;
			for (final String path : paths) {
				if (path.endsWith(".knxkeys")) {
					setKeyring(path);
					break;
				}
			}
		}
	}

	private void setKeyring(final String resource) {
		loadKeyring(resource);
		keyringLabel.setText(keyringResource);
		keyringLabel.requestLayout();

		list.removeAll();
		populateList();

		keyringPassword();
	}

	private void populateList() {
		if (keyring != null)
			keyring.interfaces().keySet().stream().sorted().forEach(this::addInterfaces);
	}

	private void addInterfaces(final IndividualAddress host) {
		final var interfaces = keyring.interfaces().get(host);
		for (final var iface : interfaces) {
			// we only want interfaces with a user specified
			if (iface.user() == 0)
				continue;

			final var groups = iface.groups().keySet().toArray();
			Arrays.sort(groups);
			final var joiner = new StringJoiner(", ");
			for (final Object group : groups)
				joiner.add(group.toString());

			final String[] item = { host.toString(), iface.address().toString(), "" + iface.user(), joiner.toString() };
			addListItem(item, null, null);
		}

		for (final TableItem item : list.getItems())
			if (item.getText(1).equals(address.toString()))
				item.setChecked(true);
	}

	private static void loadKeyring(final String resource) {
		keyringPassword = new char[0];
		keyring = Keyring.load(resource);
		keyringResource = resource;
	}
}

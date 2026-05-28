/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2019, 2026 B. Malinowsky

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

import static java.lang.System.Logger.Level.DEBUG;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.calimero.gui.logging.LogNotifier;
import io.calimero.internal.Executor;
import io.calimero.tools.DatapointImporter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import io.calimero.KNXFormatException;
import io.calimero.tools.KnxProject;

class ProjectTab extends BaseTabLayout {
	private static volatile ProjectTab currentTab;

	private static final List<Path> projects = new ArrayList<>();
	private static KnxProject selected;

	static {
		// KnxProject uses a static logger so add our notifier at class init
		final LogNotifier notifier = (name, level, msg, thrown) -> Optional.ofNullable(currentTab).ifPresent(
				t -> t.log(name, level, msg, thrown));
		LogNotifier.add(notifier);
		Executor.scheduledExecutor().schedule(() -> LogNotifier.remove(notifier), 1, TimeUnit.SECONDS);
	}

	static void findProjects() {
		try {
			projects.addAll(KnxProject.list(Path.of(".")));
			if (projects.size() != 1)
				return;

			importDatapoints(projects.getFirst());
		}
		catch (final IOException | KNXFormatException | RuntimeException e) {
			System.out.println("Error during lookup of KNX projects: " + e);
		}
	}

	public static void show(final CTabFolder tf) {
		final var tab = currentTab;
		if (tab != null)
			Arrays.stream(tf.getItems()).filter(t -> t == tab.tab).findFirst().ifPresent(tf::setSelection);
		else
			new ProjectTab(tf);
	}

	private ProjectTab(final CTabFolder tf) {
		super(tf, "KNX Projects", "Available projects (*.knxproj)");
		currentTab = this;

		final Composite parent = list.getParent();
		final int style = list.getStyle();
		final Sash bottom = (Sash) ((FormData) list.getLayoutData()).bottom.control;
		list.dispose();
		list = newTable(parent, style | SWT.CHECK | SWT.SINGLE, bottom);
		list.addSelectionListener(selected(this::updateProjectSelection));
		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.keyCode == SWT.BS && e.stateMask == 0) {
					final var items = list.getSelection();
					for (final var item : items) {
						projects.remove((Path) item.getData("path"));
						list.remove(list.indexOf(item));
					}
					list.redraw();
				}
			}
		});

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

		final TableColumn name = new TableColumn(list, SWT.LEFT);
		name.setText("Name");
		name.setWidth(300);
		final TableColumn path = new TableColumn(list, SWT.LEFT);
		path.setText("Location");
		path.setWidth(600);

		populateList();

		setLogLevel(DEBUG);

		workArea.layout(true, true);
	}

	@Override
	protected void initWorkAreaTop() {
		super.initWorkAreaTop();

		((GridLayout) top.getLayout()).numColumns = 3;
		((GridLayout) top.getLayout()).makeColumnsEqualWidth = false;

		final var open = new Button(top, SWT.NONE);
		open.setText("Add project ...");
		open.addSelectionListener(selected(this::openProject));
		open.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
	}

	@Override
	protected void onDispose(final DisposeEvent e) {
		currentTab = null;
		super.onDispose(e);
	}

	KnxProject selected() { return selected; }

	private void updateProjectSelection(final SelectionEvent event) {
		if (event.detail == SWT.CHECK) {
			for (final TableItem item : list.getItems())
				if (item != event.item)
					item.setChecked(false);
			final TableItem current = (TableItem) event.item;

			if (current.getChecked()) {
				var imported = (KnxProject) current.getData("project");
				if (imported == null) {
					final Path path = Path.of(current.getText(1));
					try {
						imported = importDatapoints(path);
						current.setData("project", imported);
					}
					catch (IOException | KNXFormatException | RuntimeException e) {
						System.out.println("Error importing datapoints from " + path);
						e.printStackTrace();
					}
				}
				selected = imported;
				if (imported == null)
					current.setChecked(false);
			}
			else
				selected = null;
		}
	}

	private void openProject(final SelectionEvent __) {
		final FileDialog dlg = new FileDialog(Main.shell, SWT.OPEN);
		dlg.setFilterNames("KNX Project Files (*.knxproj)", "All Files (*.*)");
		dlg.setFilterExtensions("*.knxproj", "*.*");
		final var resource = dlg.open();
		if (resource != null)
			addProject(Path.of(resource));
	}

	private boolean isSupportedType(final DropTargetEvent event) {
		return FileTransfer.getInstance().isSupportedType(event.currentDataType);
	}

	private void dataDropped(final DropTargetEvent event) {
		if (isSupportedType(event) && event.data instanceof final String[] paths) {
			for (final String path : paths) {
				if (path.endsWith(".knxproj"))
					addProject(Path.of(path));
			}
		}
	}

	private void addProject(final Path path) {
		projects.add(path);
		addListItem(path);
	}

	private void populateList() {
		projects.forEach(this::addListItem);
		if (projects.size() == 1)
			list.getItem(0).setChecked(true);
	}

	private void addListItem(final Path path) {
		addListItem(new String[] { path.getFileName().toString(), path.toAbsolutePath().normalize().toString() },
				new String[] { "path" }, new Object[] { path });
	}

	private static KnxProject importDatapoints(final Path path) throws IOException, KNXFormatException {
		final String output = path.getFileName().toString().replace(".knxproj", ".xml");
		final var importer = new DatapointImporter(path.toString(), output) {
			@Override
			protected Optional<char[]> projectPassword() {
				final var dlg = PasswordDialog.forProject(path);
				return dlg.show() ? Optional.of(dlg.password()) : Optional.empty();
			}
		};

		System.out.println("Import KNX project \"" + path + "\"");
		importer.run();
		return null;
	}
}

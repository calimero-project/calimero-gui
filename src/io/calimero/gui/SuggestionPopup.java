/*
    Calimero 3 - A library for KNX network access
    Copyright (c) 2026 B. Malinowsky

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

import java.util.Locale;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

final class SuggestionPopup {
	private final Text owner;
	private final Set<String> suggestions;
	private final Shell popup;
	private final List list;

	public SuggestionPopup(final Text owner, final Set<String> suggestions) {
		this.owner = owner;
		this.suggestions = suggestions;
		popup = new Shell(owner.getShell(), SWT.ON_TOP | SWT.TOOL);
		popup.setLayout(new FillLayout());
		list = new List(popup, SWT.SINGLE);

		owner.addDisposeListener(__ -> popup.dispose());
		owner.addModifyListener(this::inputModified);
		owner.addListener(SWT.Traverse, this::ownerNavigate);
		owner.addListener(SWT.FocusOut, this::focusLost);
		owner.addListener(SWT.Move, this::positionPopup);
		owner.addListener(SWT.Resize, this::positionPopup);

		owner.getShell().addListener(SWT.Move, this::positionPopup);
		owner.getShell().addListener(SWT.Resize, this::positionPopup);

		popup.addListener(SWT.Traverse, this::popupNavigate);

		list.addListener(SWT.KeyDown, this::listNavigate);
		list.addListener(SWT.MouseUp, this::applySelection);
		list.addListener(SWT.FocusOut, this::focusLost);
	}

	private void ownerNavigate(final Event e) {
		if (!popup.isVisible() || list.getItemCount() == 0)
			return;
		switch (e.keyCode) {
			case SWT.TAB, SWT.ARROW_RIGHT -> {
				final String current = owner.getText();
				final String item = list.getItem(0);
				owner.setText(item);
				// with exact prefix match, select only the suggested suffix; otherwise, select all
				final int start = item.startsWith(current) ? current.length() : item.length();
				owner.setSelection(new Point(start, item.length()));
				popup.setVisible(false);
				e.doit = false; // prevent tab focus traversal
			}
			case SWT.ARROW_DOWN -> {
				list.setFocus();
				list.setSelection(0);
			}
		}
	}

	private void popupNavigate(final Event e) {
		if (e.detail == SWT.TRAVERSE_ESCAPE) {
			e.doit = false;
			e.detail = SWT.TRAVERSE_NONE;
			popup.setVisible(false);
			// ensure focus returns to owner after shell hides
			popup.getDisplay().asyncExec(() -> {
				if (!owner.isDisposed())
					owner.setFocus();
			});
		}
	}

	private void listNavigate(final Event e) {
		switch (e.keyCode) {
			case SWT.SPACE, SWT.TAB, SWT.CR -> {
				applySelection(e);
				e.doit = false;
			}
			case SWT.ARROW_UP -> {
				if (list.getSelectionIndex() <= 0) {
					e.doit = false;
					if ("gtk".equals(SWT.getPlatform())) {
						// on GTK, list keeps focus otherwise and swallows future key events
						owner.getShell().setFocus();
						list.getDisplay().asyncExec(() -> {
							if (!owner.isDisposed())
								owner.setFocus();
						});
					}
					else
						owner.setFocus();
				}
			}
		}
	}

	private void inputModified(@SuppressWarnings("unused") final ModifyEvent e) {
		final String current = owner.getText().toLowerCase(Locale.ROOT);
		if (current.isEmpty())
			return;
		list.removeAll();
		suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains(current)).forEach(list::add);
		if (list.getItemCount() == 0) {
			popup.setVisible(false);
			return;
		}

		positionPopup(new Event());
		popup.setVisible(true);
	}

	private void applySelection(@SuppressWarnings("unused") final Event e) {
		final int idx = list.getSelectionIndex();
		if (idx >= 0) {
			owner.setText(list.getItem(idx));
			owner.setSelection(owner.getText().length());
			popup.setVisible(false);
			owner.setFocus();
		}
	}

	private void focusLost(@SuppressWarnings("unused") final Event e) {
		Display.getDefault().asyncExec(() -> {
			if (popup.isDisposed())
				return;
			final var focus = popup.getDisplay().getFocusControl();
			if (focus != owner && focus != list)
				popup.setVisible(false);
		});
	}

	private void positionPopup(@SuppressWarnings("unused") final Event e) {
		final var rc = owner.getBounds();
		final Point p = owner.getParent().toDisplay(rc.x, rc.y + rc.height);
		final Point size = popup.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		popup.setBounds(p.x, p.y, rc.width, size.y);
	}
}

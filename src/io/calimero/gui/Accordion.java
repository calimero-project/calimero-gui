/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2026, 2026 B. Malinowsky

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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

final class Accordion<T> {
	private final Font boldHeaderFont;

	private final Color bg;
	private final Color hover;

	private Section openSection = null;
	private Section focusedSection = null;


	public Accordion(final Control parent) {
		final var display = parent.getDisplay();

		final FontData[] data = display.getSystemFont().getFontData();
		for (final FontData fd : data)
			fd.setStyle(SWT.BOLD);
		boldHeaderFont = new Font(display, data);
		parent.getShell().addListener(SWT.Dispose, __ -> boldHeaderFont.dispose());

		bg = display.getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND);
		hover = display.getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);

		final Listener keyListener = e -> {
			if (focusedSection != null) {
				switch (e.keyCode) {
					case SWT.ARROW_DOWN -> focusedSection.focusNext();
					case SWT.ARROW_UP -> focusedSection.focusPrev();
					case SWT.SPACE, SWT.CR, SWT.KEYPAD_CR -> focusedSection.toggle();
				}
			}
		};
		parent.getDisplay().addFilter(SWT.KeyDown, keyListener);
		parent.getShell().addListener(SWT.Dispose, __ -> parent.getDisplay().removeFilter(SWT.KeyDown, keyListener));
	}

	public Section addSection(final Composite parent, final T id, final String title) {
		return new Section(parent, id, title);
	}

	public Section currentSection() {
		return openSection;
	}

	public final class Section extends Composite {
		private final T id;

		private final Composite header;
		private final Canvas arrowCanvas;

		private final Composite viewport;
		private final Composite contentClip;

		private boolean expanded = false;

		private int contentHeight = 0;
		private int currentHeight = 0;
		private float arrowAngle = (float) Math.PI / 2;

		private Runnable onExpandCallback = () -> {};
		private Runnable onCollapseCallback = () -> {};


		private Section(final Composite parent, final T id, final String title) {
			super(parent, SWT.NONE);
			this.id = id;

			final var sectionLayout = new GridLayout(1, false);
			sectionLayout.marginHeight = 0;
			sectionLayout.verticalSpacing = 0;
			setLayout(sectionLayout);
			setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			header = new Composite(this, SWT.NONE);
			final var headerGD = new GridData(SWT.FILL, SWT.TOP, true, false);
			header.setLayoutData(headerGD);
			final var headerLayout = new FormLayout();
			headerLayout.marginTop = 8;
			headerLayout.marginBottom = 8;
			headerLayout.marginLeft = 8;
			headerLayout.marginRight = 8;
			header.setLayout(headerLayout);

			final Label titleLabel = new Label(header, SWT.NONE);
			titleLabel.setText(title);
			titleLabel.setFont(boldHeaderFont);
			final var titleFD = new FormData();
			titleFD.left = new FormAttachment(0);
			titleFD.top = new FormAttachment(50, -9);
			titleLabel.setLayoutData(titleFD);

			arrowCanvas = new Canvas(header, SWT.NONE);
			final var arrowFD = new FormData();
			arrowFD.right = new FormAttachment(100, 0);
			arrowFD.top = new FormAttachment(50, -6);
			arrowFD.width = 14;
			arrowFD.height = 14;
			arrowCanvas.setLayoutData(arrowFD);
			arrowCanvas.addPaintListener(e -> drawArrow(e.gc));

			setHeaderBg(bg);

			// animated viewpoint
			viewport = new Composite(this, SWT.NONE);
			final var viewportGD = new GridData(SWT.FILL, SWT.TOP, true, false);
			viewportGD.heightHint = 0;
			viewport.setLayoutData(viewportGD);
			final var viewportLayout = new FillLayout();
			viewportLayout.marginHeight = 0;
			viewportLayout.marginWidth = 0;
			viewport.setLayout(viewportLayout);
			viewport.setVisible(false);

			contentClip = new Composite(viewport, SWT.NONE);
			final var contentLayout = new GridLayout();
			contentLayout.marginHeight = 0;
			contentLayout.marginWidth = 0;
			contentLayout.marginLeft = 20;
			contentClip.setLayout(contentLayout);

			final var sep = new Composite(this, SWT.NONE);
			sep.setBackground(getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND));
			final var gridData = new GridData(SWT.FILL, SWT.TOP, true, false);
			gridData.heightHint = 1;
			sep.setLayoutData(gridData);

			final Listener toggleListener = e -> toggle();
			header.addListener(SWT.MouseDown, toggleListener);
			titleLabel.addListener(SWT.MouseDown, toggleListener);
			arrowCanvas.addListener(SWT.MouseDown, toggleListener);

			header.addListener(SWT.MouseDown, e -> { focusedSection = this; });

			addHover(header);
			addHover(titleLabel);
			addHover(arrowCanvas);
		}

		public T id() { return id; }

		public void setContent(final Control content) {
			content.setParent(contentClip);
			contentClip.layout(true, true);
			contentHeight = contentClip.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
		}

		public void setExpandCallback(final Runnable onExpand) { onExpandCallback = onExpand; }

		public void setCollapseCallback(final Runnable onCollapse) { onCollapseCallback = onCollapse; }

		public void expand() {
			if (expanded)
				return;
			if (openSection != null && openSection != this)
				openSection.collapse();
			openSection = this;
			expanded = true;
			viewport.setVisible(true);

			animateHeight(currentHeight, contentHeight);
			animateArrow((float) -Math.PI / 2);

			onExpandCallback.run();
		}

		private void collapse() {
			if (!expanded)
				return;
			expanded = false;

			animateHeight(currentHeight, 0);
			animateArrow((float) Math.PI / 2);

			onCollapseCallback.run();
		}

		public void toggle() {
			if (expanded)
				collapse();
			else
				expand();
		}

		private static final int steps = 14;
		private static final int delay = 15; // ms

		private void animateHeight(final int start, final int end) {
			for (int i = 0; i <= steps; i++) {
				final int step = i;
				getDisplay().timerExec(step * delay, () -> {
					if (isDisposed())
						return;
					final float t = step / (float) steps;
					final float alpha = (float)(t < 0.5
									? 4 * t * t * t
									: 1 - Math.pow(-2 * t + 2, 3) / 2);
					currentHeight = (int) (start + (end - start) * alpha);
					((GridData) viewport.getLayoutData()).heightHint = currentHeight;

					layout(true, true);
					final Composite p = getParent();
					if (p != null && !p.isDisposed())
						p.layout(true, true);
					// resize shell
					final var shell = getShell();
					final Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
					// on GTK, computeSize underestimates the preferred size, leading to clipping
					if (ConnectDialog.isGnomeDesktop) {
						size.x = (int) (size.x * 1.2);
						size.y = (int) (size.y * 1.2);
					}
					shell.setMinimumSize(size);
					shell.setMaximumSize(shell.getMaximumSize().x, size.y);
					shell.setSize(shell.getSize().x, size.y);

					if (step == steps && !expanded)
						viewport.setVisible(false);
				});
			}
		}

		private void animateArrow(final float target) {
			final float start = arrowAngle;
			for (int i = 0; i <= steps; i++) {
				final int step = i;
				getDisplay().timerExec(step * delay, () -> {
					if (arrowCanvas.isDisposed())
						return;
					final float t = step / (float) steps;
					arrowAngle = start + (target - start) * t;
					arrowCanvas.redraw();
				});
			}
		}

		private void focusNext() {
			final Control[] kids = getParent().getChildren();
			for (int i = 0; i < kids.length - 1; i++) {
				if (kids[i] == this && kids[i + 1] instanceof final Accordion<?>.Section s) {
					s.setFocus();
					return;
				}
			}
		}

		private void focusPrev() {
			final Control[] kids = getParent().getChildren();
			for (int i = 1; i < kids.length; i++) {
				if (kids[i] == this && kids[i - 1] instanceof final Accordion<?>.Section s) {
					s.setFocus();
					return;
				}
			}
		}

		@Override
		public boolean setFocus() {
			if (header.isDisposed())
				return false;
			focusedSection = this;
			return header.setFocus();
		}

		private void drawArrow(final GC gc) {
			gc.setAntialias(SWT.ON);
			gc.setLineWidth(2);

			final int cx = 6, cy = 6, size = 6;

			final int x1 = (int) (cx - size * Math.cos(arrowAngle + Math.PI / 4));
			final int y1 = (int) (cy - size * Math.sin(arrowAngle + Math.PI / 4));
			final int x2 = cx;
			final int y2 = cy;
			final int x3 = (int) (cx - size * Math.cos(arrowAngle - Math.PI / 4));
			final int y3 = (int) (cy - size * Math.sin(arrowAngle - Math.PI / 4));

			gc.drawLine(x1, y1, x2, y2);
			gc.drawLine(x2, y2, x3, y3);
		}

		private void addHover(final Control c) {
			c.addListener(SWT.MouseEnter, e -> setHeaderBg(hover));
			c.addListener(SWT.MouseExit, e -> setHeaderBg(bg));
		}

		private void setHeaderBg(final Color color) {
			header.setBackground(color);
			for (final Control child : header.getChildren())
				child.setBackground(color);
		}
	}
}

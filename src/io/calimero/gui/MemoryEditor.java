/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2017, 2025 B. Malinowsky

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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import io.calimero.IndividualAddress;
import io.calimero.KNXFormatException;
import io.calimero.internal.Executor;
import io.calimero.tools.Memory;
import io.calimero.tools.Restart;

/**
 * @author B. Malinowsky
 */
class MemoryEditor extends BaseTabLayout
{
	private Text setStartOffset;
	private Table addressView;
	private TableEditor editor;

	private Composite detailPane;
	private Table asciiView;

	private Composite editArea;
	private Label binary;
	private Button write;

	private final IndividualAddress device;
	private Thread workerThread;

	private final int viewerColumns = 16;
	private static final int initialStartAddress = 0x100;
	private int viewerStartOffset = initialStartAddress;

	private final Map<Integer, Integer> modified = new ConcurrentHashMap<>();

	private static final Listener hexOnly = e -> {
		final char[] chars = e.text.toLowerCase().toCharArray();
		for (final char c : chars)
			if (!('0' <= c && c <= '9') && !('a' <= c && c <= 'f')) {
				e.doit = false;
				break;
			}
	};

	private class MemoryTool extends Memory {
		public MemoryTool(final String[] args) { super(args); }

		@Override
		protected void onCompletion(final Exception thrown, final boolean canceled) {
			if (thrown != null)
				asyncAddLog(thrown.getMessage());
			if (canceled)
				asyncAddLog("memory access canceled before completion");
			else
				asyncAddLog("memory access operation complete");
		}
	}


	MemoryEditor(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, args.access().protocol() + " connection to " + args.access().name(), "Connecting to", true, args);
		IndividualAddress ia;
		try {
			ia = new IndividualAddress(connect.remoteKnxAddress);
		}
		catch (final KNXFormatException e) {
			ia = null;
			setHeaderInfoPhase(statusInfo(2));
			asyncAddLog(e.getMessage());
		}
		device = ia;

		list.setFont(null);
		list.setLinesVisible(true);
		for (int i = 0; i < viewerColumns; i++) {
			final TableColumn c = new TableColumn(list, SWT.RIGHT | SWT.NO_FOCUS);
			c.setResizable(false);
			c.setText(String.format("%02x", i));
			c.setToolTipText("Column " + i);
			c.setWidth(40);
		}

		addAddressView();
		addAsciiView();
		addTableEditor(list);

		list.addListener(SWT.PaintItem, this::onItemPaint);
		list.addListener(SWT.EraseItem, e -> {
			if ((e.detail & SWT.SELECTED) != 0)
				e.detail &= ~SWT.SELECTED;
			if ((e.detail & SWT.FOREGROUND) != 0)
				e.detail &= ~SWT.FOREGROUND;
		});

		final String prefix = "memory_" + device + "_";
		final String suffix = ".hex";
		setExportName(prefix, suffix);

		workArea.layout(true, true);

		if (device != null)
			readMemory(initialStartAddress, 40);
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();

		setStartOffset = new Text(top, SWT.BORDER | SWT.RIGHT);
		setStartOffset.addListener(SWT.Verify, hexOnly);
		setStartOffset.setTextLimit(4);
		setStartOffset.setText(address(initialStartAddress));
		setStartOffset.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(final SelectionEvent e)
			{
				try {
					setViewerStartOffset();
				}
				catch (final RuntimeException rte) {
					asyncAddLog(rte);
				}
			}
		});
		setStartOffset.moveAbove(null);

		addResetAndExport(false, ".hex");
	}

	@Override
	protected void initTableBottom(final Composite parent, final Sash sash)
	{
		editArea = new Composite(parent, SWT.NONE);
		final FormData editData = new FormData();
		editData.bottom = new FormAttachment(sash);
		editData.left = new FormAttachment(0);
		editData.right = new FormAttachment(100);
		editArea.setLayoutData(editData);
		((FormData) list.getLayoutData()).bottom = new FormAttachment(editArea);

		final RowLayout row = new RowLayout(SWT.HORIZONTAL);
		row.center = true;
		editArea.setLayout(row);

		final Label startLabel = new Label(editArea, SWT.NONE);
		startLabel.setText("Offset (h):");

		final Text start = new Text(editArea, SWT.BORDER | SWT.RIGHT);
		start.setTextLimit(4);
		start.pack();
		RowData data = new RowData();
		data.width = start.getBounds().width;
		start.setLayoutData(data);
		start.addListener(SWT.Verify, hexOnly);

		final Text bytes = new Text(editArea, SWT.BORDER | SWT.RIGHT);
		bytes.addListener(SWT.Verify, hexOnly);
		bytes.setTextLimit(4);
		data = new RowData();
		data.width = start.getBounds().width;
		bytes.setLayoutData(data);

		final Label bytesLabel = new Label(editArea, SWT.NONE);
		bytesLabel.setText("Bytes");

		final Button read = new Button(editArea, SWT.NONE);
		read.setText("Read");
		read.addSelectionListener(selected(e -> {
			if (start.getText().isEmpty() || bytes.getText().isEmpty())
				return;
			read.setEnabled(false);
			readMemory(Long.parseUnsignedLong(start.getText(), 16), Integer.parseUnsignedInt(bytes.getText()));
		}));

		bytes.addTraverseListener(traverseEvent -> {
			if (traverseEvent.detail == SWT.TRAVERSE_RETURN)
				read.notifyListeners(SWT.Selection, new Event());
		});

		Label spacer = new Label(editArea, SWT.SEPARATOR);
		data = new RowData();
		data.height = read.getSize().y;
		data.width = 20;
		spacer.setLayoutData(data);

		binary = new Label(editArea, SWT.NONE);
		data = new RowData();
		data.width = 80;
		binary.setLayoutData(data);

		spacer = new Label(editArea, SWT.SEPARATOR);
		data = new RowData();
		data.height = read.getSize().y;
		data.width = 20;
		spacer.setLayoutData(data);

		write = new Button(editArea, SWT.NONE);
		write.setText("Write back modified memory");
		write.addSelectionListener(selected(e -> writeModifiedMemory()));

		final Button restart = new Button(editArea, SWT.NONE);
		restart.setText("Restart KNX device");
		restart.addSelectionListener(selected(e -> restart()));

		for (final Control c : editArea.getChildren())
			c.setEnabled(false);
	}

	@Override
	protected void saveAs(final String resource)
	{
		asyncAddLog("Export data in CSV format to " + resource);
		try {
			final char comma = ' ';
			final char delim = '\n';

			final Writer w = Files.newBufferedWriter(Paths.get(resource), StandardCharsets.UTF_8);
			w.write("Start Offset ");
			w.write(Integer.toHexString(viewerStartOffset));
			w.write(delim);

			// write list
			for (int i = 0; i < list.getItemCount(); i++) {
				final TableItem ti = list.getItem(i);
				w.append(ti.getText(0));
				for (int k = 1; k < list.getColumnCount(); k++)
					w.append(comma).append(ti.getText(k));
				w.write('\n');
			}
			w.close();
			asyncAddLog("Export completed successfully");
		}
		catch (final IOException e) {
			e.printStackTrace();
			asyncAddLog("Export aborted with error: " + e.getMessage());
		}
	}

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (workerThread != null)
			workerThread.interrupt();
	}

	private void addAddressView()
	{
		final Composite composite = new Composite(list.getParent(), SWT.NONE);
		composite.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_GREEN));
		composite.moveAbove(list.getParent());
		final FormData formData = new FormData();
		formData.top = new FormAttachment(0);
		formData.bottom = ((FormData) list.getLayoutData()).bottom;
		composite.setLayoutData(formData);
		final GridLayout layout = new GridLayout(4, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.horizontalSpacing = 0;
		composite.setLayout(layout);

		addressView = new Table(composite, SWT.SINGLE | SWT.HIDE_SELECTION | SWT.V_SCROLL | SWT.BORDER | SWT.NO_FOCUS);
		addressView.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true));
		final TableColumn column = new TableColumn(addressView, SWT.FILL);
		column.setText("Offset (h)");
		column.setResizable(false);
		column.pack();
		addressView.setLinesVisible(true);
		addressView.setHeaderVisible(true);
		addressView.layout();

		list.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true));
		list.setParent(composite);

		// sync scrolling address view to memory view
		syncScrolling(addressView, list);

		detailPane = new Composite(composite, SWT.NONE);
		detailPane.setLayout(new FillLayout());
		detailPane.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true));
	}

	private void addAsciiView()
	{
		asciiView = new Table(detailPane, SWT.SINGLE | SWT.HIDE_SELECTION | SWT.V_SCROLL | SWT.BORDER | SWT.NO_FOCUS);
		for (int i = 0; i < viewerColumns; i++) {
			final TableColumn c = new TableColumn(asciiView, SWT.CENTER | SWT.NO_FOCUS);
			c.setResizable(false);
			c.setText("W");
			c.pack();
			final int width = c.getWidth();
			c.setText(" ");
			final GC gc = new GC(asciiView);
			final FontMetrics fm = gc.getFontMetrics();
			final int charWidth = (int) (3 * fm.getAverageCharacterWidth());
			gc.dispose();
			c.setWidth(Math.max(width, charWidth));
		}
		asciiView.setLinesVisible(true);
		asciiView.setHeaderVisible(true);
		asciiView.addListener(SWT.EraseItem, e -> {
			e.detail |= SWT.FOREGROUND;
			e.gc.setForeground(Main.display.getSystemColor(SWT.COLOR_LIST_FOREGROUND));
			if ((e.detail & SWT.SELECTED) != 0)
				e.detail &= ~SWT.SELECTED;
			if ((e.detail & SWT.FOCUSED) != 0)
				e.detail &= ~SWT.FOCUSED;
		});

		// sync scrolling to address and memory view
		syncScrolling(asciiView, addressView);
		syncScrolling(asciiView, list);
	}

	private void addTableEditor(final Table table)
	{
		editor = new TableEditor(table);
		editor.horizontalAlignment = SWT.RIGHT;
		editor.grabHorizontal = true;
		table.addListener(SWT.MouseDown, this::onMouseDown);
	}

	private void syncScrolling(final Table thisOne, final Table with)
	{
		// for keys
		thisOne.addListener(SWT.Selection, e -> with.setSelection(thisOne.getSelectionIndices()));
		with.addListener(SWT.Selection, e -> thisOne.setSelection(with.getSelectionIndices()));

		// for mouse/scrollbar
		thisOne.getVerticalBar().addSelectionListener(selected(e -> {
			with.setTopIndex(thisOne.getTopIndex());
			// align ourselves at top row (enforce discrete scrolling by row)
			thisOne.setTopIndex(thisOne.getTopIndex());
		}));
		with.getVerticalBar().addSelectionListener(selected(e -> {
			thisOne.setTopIndex(with.getTopIndex());
			// align ourselves at top row (enforce discrete scrolling by row)
			with.setTopIndex(with.getTopIndex());
		}));
	}

	private void onItemPaint(final Event event)
	{
		if (event.type == SWT.PaintItem && event.item != null) {
			final TableItem ti = (TableItem) event.item;
			final int location = viewerStartOffset + list.indexOf(ti) * viewerColumns + event.index;
			final int fgnd = modified.containsKey(location) ? SWT.COLOR_DARK_YELLOW : SWT.COLOR_LIST_FOREGROUND;
			event.gc.setForeground(Main.display.getSystemColor(fgnd));
			final Rectangle rect = ti.getTextBounds(event.index);
			final Point extent = event.gc.stringExtent(ti.getText(event.index));
			event.gc.drawString(ti.getText(event.index), rect.x + rect.width - extent.x - 1, rect.y, true);
		}
	}

	private void onMouseDown(final Event event)
	{
		final Table table = list;
		final Rectangle clientArea = table.getClientArea();
		final Point pt = new Point(event.x, event.y);

		int index = table.getTopIndex();
		while (index < table.getItemCount()) {
			boolean visible = false;
			final TableItem item = table.getItem(index);
			for (int col = 0; col < list.getColumnCount(); col++) {
				final Rectangle rect = item.getBounds(col);
				if (rect.contains(pt) && !item.getText(col).isEmpty()) {
					final int row = index;
					final int column = col;
					final Text edit = new Text(table, SWT.RIGHT);

					edit.addListener(SWT.FocusOut, e -> {
						updateMemoryValue(item, row, column, edit.getText());
						edit.dispose();
						updateBinary("");
					});
					edit.addListener(SWT.Traverse, e -> {
						if (e.detail == SWT.TRAVERSE_RETURN)
							updateMemoryValue(item, row, column, edit.getText());
						if (e.detail == SWT.TRAVERSE_RETURN || e.detail == SWT.TRAVERSE_ESCAPE) {
							edit.dispose();
							updateBinary("");
							e.doit = false;
						}
					});
					edit.addListener(SWT.Verify, hexOnly);
					edit.addListener(SWT.Modify, e -> updateBinary(edit.getText()));

					editor.setEditor(edit, item, column);
					edit.setTextLimit(2);
					final String v = item.getText(col);
					edit.setText(v);
					edit.selectAll();
					edit.setFocus();
					updateBinary(v);
					return;
				}
				if (!visible && rect.intersects(clientArea))
					visible = true;
				if (!visible)
					return;
			}
			index++;
		}
	}

	private void updateMemoryValue(final TableItem item, final int row, final int column, final String value)
	{
		if (value.isEmpty())
			return;
		final int v = Integer.parseUnsignedInt(value, 16);
		if (v != Integer.parseUnsignedInt(item.getText(column), 16)) {
			final int location = viewerStartOffset + row * viewerColumns + column;
			addToModifiedList(location, v);
			item.setText(column, value);
		}
	}

	private void updateBinary(final String text)
	{
		if (text.isEmpty())
			binary.setText("");
		else
			binary.setText("0b" + Integer.toBinaryString((1 << 8) | Integer.parseUnsignedInt(text, 16)).substring(1));
	}

	private void setViewerStartOffset()
	{
		final int offset = Integer.parseUnsignedInt(setStartOffset.getText(), 16);
		while (viewerStartOffset > offset) {
			viewerStartOffset -= viewerColumns;
			addMemoryRow(true);
		}
		while (viewerStartOffset + viewerColumns <= offset) {
			viewerStartOffset += viewerColumns;
			removeTopRow();
		}
	}

	private void restart() {
		if (askUser("Restart KNX Device " + device, "Perform a confirmed restart of " + device
				+ " in connection-less mode?") != SWT.YES)
			return;

		final var tryBasicRestart = new AtomicBoolean();
		final class RestartTool extends Restart {
			public RestartTool(final String[] args) { super(args); }

			@Override
			protected void onCompletion(final Exception thrown, final boolean canceled) {
				if (thrown != null) {
					tryBasicRestart.set(true);
					asyncAddLog(thrown);
				}
				if (canceled)
					asyncAddLog("restart canceled before completion");
				else
					asyncAddLog("restart complete");
			}
		}

		final var args = connect.getArgs(false);
		args.add("--confirmed");
		final var confirmedRestart = new RestartTool(args.toArray(String[]::new));
		runWorker(4, () -> {
			confirmedRestart.run();
			if (tryBasicRestart.get()) {
				asyncAddLog("resort to basic restart");
				args.remove(args.size() - 1);
				final var basicRestart = new RestartTool(args.toArray(String[]::new));
				basicRestart.run();
			}
		});
	}

	private int askUser(final String title, final String msg)
	{
		final int style = SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL | SWT.SHEET;
		final MessageBox dlg = new MessageBox(Main.shell, style);
		dlg.setText(title);
		dlg.setMessage(msg);
		final int id = dlg.open();
		final String response = switch (id) {
			case SWT.YES -> "yes";
			case SWT.NO -> "no";
			case SWT.CANCEL -> "canceled";
			default -> "button " + id;
		};
		asyncAddLog(title + ": " + response);
		return id;
	}

	private void writeModifiedMemory() {
		final var i = modified.entrySet().iterator();
		final var entry = new AtomicReference<Map.Entry<Integer, Integer>>();
		final var tool = new MemoryTool(connect.getArgs(false).toArray(String[]::new)) {
			@Override
			protected Command fetchCommand() {
				final var last = entry.get();
				if (last != null)
					modified.remove(last.getKey());
				if (!i.hasNext())
					return Done;
				final var next = i.next();
				entry.set(next);
				asyncAddLog(String.format("apply W[%s]=%02x to device memory", address(next.getKey()), next.getValue()));
				return new Write(next.getKey(), (byte) (int) next.getValue());
			}
		};

		runWorker(3, tool);
	}

	private void readMemory(final long startAddress, final int bytes) {
		final var args = connect.getArgs(false);
		args.add("read");
		args.add("0x" + Long.toHexString(startAddress));
		args.add("" + bytes);

		final var tool = new MemoryTool(args.toArray(String[]::new)) {
			@Override
			protected void onMemoryRead(final int startAddress, final byte[] data) {
				asyncAddLog(HexFormat.ofDelimiter(" ").formatHex(data));
				Main.asyncExec(() -> updateMemoryRange(startAddress, data));
			}
		};

		asyncAddLog("read device memory range 0x" + address(startAddress) + " to 0x" + address(startAddress + bytes));
		runWorker(0, tool);
	}

	private void runWorker(int phase, final Runnable r)
	{
		setHeaderInfoPhase(statusInfo(phase));
		workerThread = Executor.execute(() -> {
			Main.asyncExec(() -> {
				for (final Control c : editArea.getChildren())
					c.setEnabled(false);
			});
			try {
				r.run();
			}
			finally {
				Main.asyncExec(() -> {
					setHeaderInfoPhase(statusInfo(1));
					editArea.setEnabled(true);
					for (final Control c : editArea.getChildren()) {
						if (c != write || !modified.isEmpty())
							c.setEnabled(true);
					}
				});
			}
		}, "Calimero memory worker");
	}

	private void addMemoryRow(final boolean topOfList)
	{
		final int index = topOfList ? 0 : addressView.getItemCount();
		final TableItem ti = new TableItem(addressView, SWT.NONE, index);
		ti.setText(0, address(viewerStartOffset + index * viewerColumns));
		new TableItem(list, SWT.NONE, index);
		new TableItem(asciiView, SWT.NONE, index);
	}

	private void removeTopRow()
	{
		if (addressView.getItemCount() == 0)
			return;
		final int index = 0;
		addressView.remove(index);
		list.remove(index);
		asciiView.remove(index);
	}

	private void updateMemoryRange(final int address, final byte[] data)
	{
		while ((viewerStartOffset + list.getItemCount() * viewerColumns) < (address + data.length))
			addMemoryRow(false);
		for (int i = 0; i < data.length; i++) {
			removeFromModifiedList(address + i);

			final int row = (address + i - viewerStartOffset) / viewerColumns;
			if (row < 0)
				continue;
			final TableItem memory = list.getItem(row);
			final int index = (address + i - viewerStartOffset) % viewerColumns;
			final int v = data[i] & 0xff;
			memory.setText(index, String.format("%02x", v));

			final TableItem ascii = asciiView.getItem(row);
			// 7-bit ascii printable would be: v > 0x1f && v < 0x7f
			ascii.setText(index, isPrintable((char) v) ? String.valueOf((char) v) : ".");
		}
	}

	private void addToModifiedList(final int address, final int value)
	{
		modified.put(address, value);
		asyncAddLog(String.format("add W[%s]=%02x to modified memory list", address(address), value));
		write.setEnabled(true);
	}

	private void removeFromModifiedList(final int address)
	{
		final Integer v = modified.remove(address);
		if (v != null)
			asyncAddLog(String.format("remove W[%s]=%s from modified memory list", address(address), v));
		if (modified.isEmpty())
			write.setEnabled(false);
	}

	private static String statusInfo(final int phase) {
		return switch (phase) {
			case 0 -> "Reading memory of";
			case 1 -> "Completed access to";
			case 2 -> "Error reading memory of";
			case 3 -> "Writing memory to";
			case 4 -> "Restart";
			default -> "Unknown";
		};
	}

	private static String address(final long addr)
	{
		return String.format("%04x", addr);
	}

	private static boolean isPrintable(final char c)
	{
		final Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
		return (!Character.isISOControl(c)) && block != null && !block.equals(Character.UnicodeBlock.SPECIALS);
	}
}

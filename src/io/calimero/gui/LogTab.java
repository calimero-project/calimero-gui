/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2006, 2026 B. Malinowsky

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
import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.TRACE;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TableColumn;

import io.calimero.gui.logging.LogNotifier;

/**
 * @author B. Malinowsky
 */
class LogTab extends BaseTabLayout
{
	private static class StreamRedirector extends PrintStream {
		private final String name;
		private final Level level;

		StreamRedirector(final String name, final Level level, final OutputStream out) {
			super(out, true);
			this.name = name;
			this.level = level;
		}

		@Override
		public void println(final String s) {
			print(s);
		}

		@Override
		public void print(final String s) {
			if (s != null)
				addToLogBuffer(name, level, s);
		}

		@Override
		public void write(final byte[] buf, final int off, final int len) {
			if (len == 1 && buf[off] == '\n' ||
			    len == 2 && buf[off] == '\r' && buf[off + 1] == '\n')
				return;
			final String s = new String(buf, off, len);
			addToLogBuffer(name, level, s);
		}
	}

	static final PrintStream oldSystemErr;
	static {
		final PrintStream oldSystemOut = System.out;
		final PrintStream redirector = new StreamRedirector("System.out", DEBUG, oldSystemOut);
		System.setOut(redirector);

		oldSystemErr = System.err;
		System.setErr(new StreamRedirector("System.err", ERROR, oldSystemErr));
	}

	private static final String[] levels = new String[] { "All", "Trace", "Debug", "Info", "Warn", "Error", "Off" };

	private record LogEntry(Instant instant, String name, Level level, String msg, Throwable throwable) {}

	private static final int maxHistorySize = 1000;
	private static final List<LogEntry> logHistory = new ArrayList<>(maxHistorySize);
	private static final Map<LogTab, List<LogEntry>> logBuffer = new ConcurrentHashMap<>();

	private static final LogNotifier notifier = LogTab::addToLogBuffer;

	static void initLogging() {
		LogNotifier.add(LogTab.notifier);
	}

	LogTab(final CTabFolder tf)
	{
		super(tf, "Logging", null);
		logBuffer.put(this, Collections.synchronizedList(new ArrayList<>()));

		log.dispose();

		final Composite parent = list.getParent();
		final Sash bottom = (Sash) ((FormData) list.getLayoutData()).bottom.control;
		list.dispose();
		list = newTable(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL, bottom);
		list.setLinesVisible(true);

		final var date = new TableColumn(list, SWT.RIGHT);
		date.setText("Date");
		date.setWidth(35);
		final var time = new TableColumn(list, SWT.RIGHT);
		time.setText("Time");
		time.setWidth(35);
		final var level = new TableColumn(list, SWT.LEFT);
		level.setText("Level");
		level.setWidth(25);
		final var logger = new TableColumn(list, SWT.LEFT);
		logger.setText("Logger");
		logger.setWidth(85);
		final var msg = new TableColumn(list, SWT.LEFT);
		msg.setText("Message");
		msg.setWidth(250);
		workArea.layout(true, true);
		enableColumnAdjusting();

		final int cmd = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac") ? SWT.COMMAND : SWT.CTRL;
		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (e.stateMask == cmd && (e.keyCode == 'c' || e.keyCode == 'C')) {
					final Clipboard clipboard = new Clipboard(Display.getDefault());
					clipboard.setContents(new Object[] { tableSelection() },
							new Transfer[] { TextTransfer.getInstance() });
					clipboard.dispose();
				}
			}
		});

		populateHistory();
	}

	@Override
	protected void initTableBottom(final Composite parent, final Sash sash)
	{
		((FormData) sash.getLayoutData()).top = new FormAttachment(100);
		((FormData) sash.getLayoutData()).bottom = new FormAttachment(100);
		sash.setEnabled(false);
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();

		final var topLayout = (GridLayout) top.getLayout();
		topLayout.numColumns = 2;
		topLayout.marginWidth = 12;
		topLayout.marginHeight = 8;
		topLayout.horizontalSpacing = 20;
		top.setLayout(topLayout);

		final var logLevels = new Composite(top, SWT.NONE);
		final var rl = new RowLayout(SWT.HORIZONTAL);
		rl.marginLeft = 0;
		rl.marginRight = 0;
		rl.marginTop = 0;
		rl.spacing = 8;
		logLevels.setLayout(rl);

		for (int i = 1; i < levels.length - 1; i++) {
			final var button = new Button(logLevels, SWT.RADIO);
			button.setText(levels[i]);
			button.setData("ordinal", i);
			if (i == TRACE.ordinal())
				button.setSelection(true);
			button.addListener(SWT.Selection, e -> {
				if (button.getSelection())
					setLogLevel(Level.values()[(int) button.getData("ordinal")]);
			});
		}
		setLogLevel(Level.values()[TRACE.ordinal()]);

		final Button clear = new Button(top, SWT.NONE);
		clear.setText("Clear log");
		clear.addListener(SWT.Selection, __ -> {
			list.removeAll();
			list.redraw();
		});
	}

	@Override
	protected void onDispose(final DisposeEvent e) {
		logBuffer.remove(this);
	}

	@Override
	void asyncAddLog() {
		Main.asyncExec(() -> {
			if (list.isDisposed())
				return;

			final List<LogEntry> buf = logBuffer.get(this);
			synchronized (buf) {
				for (final var entry : buf) {
					if (matches(entry.level())) {
						final var items = logEntryToListItems(entry);
						for (final var item : items)
							addListItem(item, new String[0], new String[0]);
					}
				}
				buf.clear();
			}
		});
	}

	private boolean matches(final Level msgLevel) {
		return msgLevel.getSeverity() >= logLevel().getSeverity();
	}

	private List<String[]> logEntryToListItems(final LogEntry logEntry) {
		final String date = dateFormatter.format(logEntry.instant());
		final String time = timeFormatter.format(logEntry.instant());

		final var msg = expandTabs(logEntry.msg());
		final var lines = msg.split("\n");
		if (lines.length == 0)
			return List.of();

		final var list = new ArrayList<String[]>();
		final String[] first = { date, time, logEntry.level().toString(), logEntry.name(), lines[0] };
		list.add(first);
		for (int i = 1; i < lines.length; ++i)
			list.add(new String[] { "", "", "", "", lines[i] });

		final Throwable t = logEntry.throwable();
		if (t != null)
			list.add(new String[] { "", "", "", "", t.toString() });
		return list;
	}

	private void populateHistory() {
		Main.asyncExec(() -> {
			synchronized (logHistory) {
				for (final var entry : logHistory) {
					final var items = logEntryToListItems(entry);
					for (final var item : items)
						addListItem(item, new String[0], new String[0]);
				}
			}
		});
	}

	private String tableSelection() {
		final var joiner = new StringJoiner("\n");
		for (final var item : list.getSelection()) {
			final var line = new StringJoiner(" ");
			for (int i = 0; i < list.getColumnCount(); i++)
				line.add(item.getText(i));
			joiner.add(line.toString());
		}
		return joiner.toString();
	}

	private static void addToLogBuffer(final String name, final Level level, final String msg) {
		addToLogBuffer(name, level, expandTabs(msg), null);
	}

	private static void addToLogBuffer(final String name, final Level level, final String msg, final Throwable thrown) {
		final var entry = new LogEntry(Instant.now(), name, level, msg, thrown);
		addToLogHistory(entry);
		logBuffer.forEach((k, v) -> {
			v.add(entry);
			k.asyncAddLog();
		});
	}

	private static void addToLogHistory(final LogEntry entry) {
		synchronized (logHistory) {
			if (logHistory.size() >= maxHistorySize)
				logHistory.removeFirst();
			logHistory.add(entry);
		}
	}
}

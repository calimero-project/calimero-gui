/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2019 B. Malinowsky

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
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.log.LogService.LogLevel;

/**
 * @author B. Malinowsky
 */
class BaseTabLayout
{
	private static class StreamRedirector extends PrintStream
	{
		StreamRedirector(final OutputStream out)
		{
			super(out, true);
		}

		@Override
		public void println(final String s)
		{
			print(s);
		}

		@Override
		public void print(final String s)
		{
			if (s != null) {
				logBuffer.forEach((k, v) -> { v.add(s); k.asyncAddLog(); });
				LogTab.log(s);
			}
		}
	}

	static final PrintStream oldSystemErr;

	static {
		System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
		System.setProperty("org.slf4j.simpleLogger.showLogName", "true");

		final PrintStream oldSystemOut = System.out;
		final PrintStream redirector = new StreamRedirector(oldSystemOut);
		System.setOut(redirector);

		oldSystemErr = System.err;
		System.setErr(new StreamRedirector(oldSystemErr));
	}

	private static Map<BaseTabLayout, java.util.List<String>> logBuffer = Collections
			.synchronizedMap(new WeakHashMap<>());
	private static Map<BaseTabLayout, LogLevel> logLevel = Collections.synchronizedMap(new WeakHashMap<>());
	private static Map<BaseTabLayout, java.util.List<String>> logIncludeFilters = Collections
			.synchronizedMap(new WeakHashMap<>());
	private static Map<BaseTabLayout, java.util.List<String>> logExcludeFilters = Collections
			.synchronizedMap(new WeakHashMap<>());

	final CTabItem tab;
	final Composite workArea;
	final Composite top;
	Table list;
	final List log;

	int listItemMargin = 2;

	private final CTabFolder tf;
	private Label infoLabel;

	// debounce the menu right click on OS X
	private static final long bounce = 50; //ms
	private long timeLastMenu;

	// filters for list output, column-based
	final Map<Integer, String> includeFilter = Collections.synchronizedMap(new HashMap<>());
	final Map<Integer, ArrayList<String>> excludeFilter = Collections.synchronizedMap(new HashMap<>());

	private String filenamePrefix = "";
	private String filenameSuffix;
	private String prevFilename;

	// Type params of array are <String[] String[], Object[]>
	private final java.util.List<Object[][]> itemBuffer = Collections.synchronizedList(new ArrayList<>());

	BaseTabLayout(final CTabFolder tf, final String tabTitle, final String info)
	{
		this(tf, tabTitle, info, true);
	}

	BaseTabLayout(final CTabFolder tf, final String tabTitle, final String info, final boolean showClose)
	{
		this.tf = tf;
		tab = new CTabItem(tf, showClose ? SWT.CLOSE : SWT.NONE);
		tab.setText(tabTitle);
		workArea = new Composite(tf, SWT.NONE);
		workArea.setLayout(new GridLayout());
		top = new Composite(workArea, SWT.NONE);
		final GridData gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
		top.setLayoutData(gridData);

		tab.setControl(workArea);
		tab.addDisposeListener(this::onDispose);
		tf.setSelection(tab);

		if (info != null) {
			infoLabel = new Label(top, SWT.WRAP);
			infoLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			setHeaderInfo(info);
		}
		initWorkAreaTop();
		final Composite splitted = new Composite(workArea, SWT.NONE);
		splitted.setLayout(new FormLayout());
		splitted.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		final Sash sash = new Sash(splitted, SWT.HORIZONTAL | SWT.SMOOTH);
		final FormData sashData = new FormData();
		sashData.top = new FormAttachment(70);
		sashData.left = new FormAttachment(0);
		sashData.right = new FormAttachment(100);
		sash.setLayoutData(sashData);
		sash.addListener(SWT.Selection, e -> {
			final int numerator = Math.round(e.y * 100.0f / splitted.getBounds().height);
			sashData.top = new FormAttachment(numerator);
			splitted.layout();
		});

		list = newTable(splitted, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL, sash);
		list.addSelectionListener(defaultSelected(e -> {
			if (e.item.getData("internal") == null)
				onListItemSelected(e);
		}));
		initTableBottom(splitted, sash);
		log = createLogView(splitted, sash);
		logBuffer.put(this, Collections.synchronizedList(new ArrayList<>()));
		workArea.layout();
	}

	static Table newTable(final Composite parent, final int style, final Sash bottomSash)
	{
		final Table table = new Table(parent, style);
		final FormData tableData = new FormData();
		tableData.top = new FormAttachment(0);
		tableData.bottom = new FormAttachment(bottomSash);
		tableData.left = new FormAttachment(0);
		tableData.right = new FormAttachment(100);
		table.setLayoutData(tableData);
		table.setLinesVisible(false);
		table.setHeaderVisible(true);
		table.setFont(Main.font);
		return table;
	}

	/**
	 * Override in subtypes.
	 */
	protected void initWorkAreaTop()
	{
		final GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		top.setLayout(layout);
	}

	/**
	 * Override in subtypes.
	 *
	 * @param parent parent composite
	 * @param sash sash delimiter on bottom of table
	 */
	protected void initTableBottom(final Composite parent, final Sash sash)
	{}

	protected void initFilterMenu()
	{
		list.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(final MenuDetectEvent e)
			{
				final long now = e.time & 0xFFFFFFFFL;
				if (timeLastMenu + bounce >= now)
					return;
				timeLastMenu = now;

				final int index = list.getSelectionIndex();
				if (index == -1)
					return;
				final TableItem item = list.getItem(index);
				final int c = getColumn(e, item);
				if (c == -1)
					return;
				final String content = item.getText(c);

				final Menu menu = new Menu(list.getShell(), SWT.POP_UP);
				final MenuItem mi1 = new MenuItem(menu, SWT.PUSH);
				mi1.setText("Show only " + content);
				mi1.setData("column", c);
				mi1.setData("pattern", content);
				final MenuItem mi2 = new MenuItem(menu, SWT.PUSH);
				mi2.setText("Exclude " + content);
				mi2.setData("column", c);
				mi2.setData("pattern", content);

				final SelectionAdapter selection = new SelectionAdapter() {
					@Override
					public void widgetSelected(final SelectionEvent e)
					{
						final Integer col = (Integer) mi1.getData("column");
						final String pattern = (String) mi1.getData("pattern");
						if (e.widget == mi1)
							includeFilter.put(col, pattern);
						else if (e.widget == mi2) {
							ArrayList<String> patterns = excludeFilter.get(col);
							if (patterns == null) {
								patterns = new ArrayList<String>();
								excludeFilter.put(col, patterns);
							}
							patterns.add(pattern);
						}
						asyncAddLog("add filter on column " + list.getColumn(col).getText()
								+ " for \"" + pattern + "\"");
					}
				};
				mi1.addSelectionListener(selection);
				mi2.addSelectionListener(selection);

				final Point pt = new Point(e.x, e.y);
				menu.setLocation(pt);
				menu.setVisible(true);
			}

			private int getColumn(final MenuDetectEvent event, final TableItem item)
			{
				final Point pt = list.toControl(event.x, event.y);
				for (int i = 0; i < list.getColumnCount(); i++) {
					final Rectangle rect = item.getBounds(i);
					if (rect.contains(pt))
						return i;
				}
				return -1;
			}
		});
	}

	protected boolean applyFilter(final String[] item)
	{
		for (int i = 0; i < item.length; i++) {
			final String s = item[i];
			final String include = includeFilter.get(i);
			if (include != null && !include.equals(s))
				return true;

			final ArrayList<String> exclude = excludeFilter.get(i);
			if (exclude != null && exclude.contains(s))
				return true;
		}
		return false;
	}

	/**
	 * Override in subtypes.
	 *
	 * @param e dispose event
	 */
	protected void onDispose(final DisposeEvent e)
	{}

	/**
	 * Sets some textual information (help) into the empty list control.
	 *
	 * @param info info text to set
	 */
	protected final void setListBanner(final String info)
	{
		if (list.getItemCount() > 0)
			return;
		if (list.getColumnCount() == 0)
			new TableColumn(list, SWT.LEFT);
		final TableItem item = new TableItem(list, SWT.NONE);
		item.setText(info);
		item.setData("internal", "");
	}

	protected final void setHeaderInfo(final String info)
	{
		if (infoLabel.isDisposed())
			return;
		infoLabel.setText(info);
		top.layout();
	}

	protected final void setHeaderInfo(final ConnectArguments connect, final String phase)
	{
		setHeaderInfo(headerInfo(connect, phase));
	}

	static final String headerInfo(final ConnectArguments connect, final String status)
	{
		return status + " " + uniqueId(connect) + (connect.useNat() ? " (using NAT)" : "");
	}

	static String uniqueId(final ConnectArguments connect)
	{
		if (!connect.knxAddress.isEmpty())
			return (connect.knxAddress.split("\\.").length < 3 ? "line " : "device ") + connect.knxAddress;

		if (connect.remote != null)
			return "interface " + connect.remote + ":" + connect.port;
		return "interface " + connect.port;
	}

	/**
	 * Override in subtypes.
	 *
	 * @param e table selection event
	 */
	protected void onListItemSelected(final SelectionEvent e)
	{}

	protected final void setLogLevel(final LogLevel level)
	{
		logLevel.put(this, level);
	}

	protected final void addLogIncludeFilter(final String... regex)
	{
		logIncludeFilters.computeIfAbsent(this, k -> new ArrayList<>()).addAll(Arrays.asList(regex));
	}

	protected final void addLogExcludeFilter(final String... regex)
	{
		logExcludeFilters.computeIfAbsent(this, k -> new ArrayList<>()).addAll(Arrays.asList(regex));
	}

	/**
	 * Adds a log strings of the log buffer asynchronously to the log list.
	 */
	private void asyncAddLog()
	{
		Main.asyncExec(() -> {
			if (log.isDisposed())
				return;
			final java.util.List<String> buf = logBuffer.get(this);
			final LogLevel level = logLevel.computeIfAbsent(this, k -> LogLevel.INFO);
			final java.util.List<String> include = logIncludeFilters.getOrDefault(this, Collections.emptyList());
			final java.util.List<String> exclude = logExcludeFilters.getOrDefault(this, Collections.emptyList());
			if (buf != null) {
				// we only scroll to show the newest item if the log is completely scrolled down
				final int items = log.getItemCount();
				final int first = log.getTopIndex();
				final Rectangle area = log.getClientArea();
				final int height = log.getItemHeight();
				final int visible = (area.height + height - 1) / height;
				final int last = first + visible;
				final boolean atEnd = last >= items;

				synchronized (buf) {
					buf.stream().filter(s -> matches(s, level, include, exclude)).map(BaseTabLayout::expandTabs)
							.forEach(log::add);
					buf.clear();
				}
				if (log.getItemCount() > items && atEnd)
					log.setTopIndex(log.getItemCount() - 1);
			}
		});
	}

	private static String expandTabs(final String s)
	{
		return s.replace("\t", "    ");
	}

	@SuppressWarnings("fallthrough")
	private static boolean matches(final String logMessage, final LogLevel level, final java.util.List<String> include,
		final java.util.List<String> exclude)
	{
		if (logMessage.startsWith("> "))
			return true;
		boolean match = false;
		switch (level) {
		case TRACE:
			match = true;
			break;
		case DEBUG:
			match |= logMessage.contains(LogLevel.DEBUG.name());
		case INFO:
			match |= logMessage.contains(LogLevel.INFO.name());
		case WARN:
			match |= logMessage.contains(LogLevel.WARN.name());
		case ERROR:
			match |= logMessage.contains(LogLevel.ERROR.name());
		}
		if (!match)
			return false;

		final boolean included = include.isEmpty() || include.stream().anyMatch(logMessage::matches);
		final boolean excluded = exclude.stream().anyMatch(logMessage::matches);
		return included && !excluded;
	}

	void asyncLogAddAll(final Collection<String> c) {
		final java.util.List<String> buf = logBuffer.get(this);
		if (buf != null) {
			buf.addAll(c);
			asyncAddLog();
		}
	}

	/**
	 * Adds a log string asynchronously to the log list.
	 */
	protected final void asyncAddLog(final String s)
	{
		final java.util.List<String> buf = logBuffer.get(this);
		if (buf != null) {
			buf.add("> " + s);
			asyncAddLog();
		}
	}

	/**
	 * Adds a log string asynchronously to the log list.
	 */
	protected void asyncAddLog(final Throwable t)
	{
		asyncAddLog("Error: " + t.toString());
		final java.util.List<StackTraceElement> trace = Arrays.asList(t.getStackTrace());
		trace.stream().filter(e -> e.getClassName().startsWith("tuwien")).limit(3).map(e -> "\t" + e).forEach(this::asyncAddLog);
		for (Throwable i = t; i.getCause() != null && i != i.getCause(); i = i.getCause())
			asyncAddLog("\t" + i.getCause().toString());
	}

	/**
	 * Adds a log string asynchronously to the log list.
	 */
	protected void asyncAddLog(final String msg, final Throwable t)
	{
		asyncAddLog("Error " + msg + ": " + t.toString());
		for (Throwable i = t; i.getCause() != null && i != i.getCause(); i = i.getCause())
			asyncAddLog("\t" + i.getCause().toString());
	}

	/**
	 * Adds a list item asynchronously to the list.
	 *
	 * @param itemText list item text of each column
	 * @param keys key entry of the item for each column, might be <code>null</code>
	 * @param data data entry of the item for each column, might be <code>null</code> if <code>keys</code> is
	 *        <code>null</code>
	 */
	protected void asyncAddListItem(final String[] itemText, final String[] keys, final String[] data)
	{
		itemBuffer.add(new String[][] { itemText, keys, data });
		// TODO Runnables might be executed with delay, because SWT enforces a minimum inter-arrival
		// time. We create a runnable for every new item, and even though addListItems
		// finished adding all items to the list, remaining runnables that piled up get executed.
		// Maybe replace with a timed execution every x ms.
		Main.asyncExec(this::addListItems);
	}

	// this method must be invoked from the GUI thread only
	private void addListItems()
	{
		if (list.isDisposed())
			return;

		// we only scroll to show the newest item if the list is completely scrolled down
		// hence, check what items are shown currently
		final int first = list.getTopIndex();
		final Rectangle area = list.getClientArea();
		final int height = list.getItemHeight();
		final int headerHeight = list.getHeaderHeight();
		final int visible = (area.height - headerHeight + height - 1) / height;
		final int last = first + visible;
		final int total = list.getItemCount();
		final boolean atEnd = last >= total;

		list.setRedraw(false);
		int added = 0;
		while (itemBuffer.size() > 0 && added < 500) {
			final Object[][] e = itemBuffer.remove(0);
			final String[] itemText = (String[]) e[0];
			final String[] keys = (String[]) e[1];
			final Object[] data = e[2];
			// add item
			final TableItem item = new TableItem(list, SWT.NONE);
			item.setText(itemText);
			if (keys != null)
				for (int i = 0; i < keys.length; i++)
					item.setData(keys[i], data[i]);
			added++;
		}

		if (atEnd)
			list.showItem(list.getItem(list.getItemCount() - 1));
		list.setRedraw(true);
	}

	// this method must be invoked from the GUI thread only
	void addListItem(final String[] itemText, final String[] keys, final Object[] data)
	{
		itemBuffer.add(new Object[][] { itemText, keys, data });
		addListItems();
	}

	/**
	 * Enables automatic list column adjusting for the main list.
	 */
	protected void enableColumnAdjusting()
	{
		list.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(final ControlEvent e)
			{
				final TableColumn[] cols = list.getColumns();
				if (cols.length == 0)
					return;
				final Rectangle old = (Rectangle) list.getData();
				final Rectangle area = list.getClientArea();
				int oldWidth = 0;
				if (old != null) {
					oldWidth = old.width;
					if (oldWidth == area.width)
						return;
				}
				else {
					// init with width of all columns
					for (final TableColumn c : cols)
						oldWidth += c.getWidth();
				}
				list.setData(area);
				resizeColumns(oldWidth, area.width, 0);
			}
		});
		for (final TableColumn c : list.getColumns()) {
			c.addControlListener(new ControlAdapter() {
				@Override
				public void controlResized(final ControlEvent e)
				{
					final TableColumn c = (TableColumn) e.widget;
					if (c.getData("resized") != null) {
						c.setData("resized", null);
						return;
					}
					final TableColumn[] cols = list.getColumns();
					int newWidth = list.getClientArea().width;
					int colIndex = 0;
					for (; colIndex < cols.length; ++colIndex) {
						newWidth -= cols[colIndex].getWidth();
						if (cols[colIndex] == c)
							break;
					}
					float oldWidth = 0;
					for (int i = colIndex + 1; i < cols.length; ++i)
						oldWidth += cols[i].getWidth();
					resizeColumns((int) oldWidth, newWidth, colIndex + 1);
				}
			});
		}
		// init with width of all columns
		int oldWidth = 0;
		for (final TableColumn c : list.getColumns())
			oldWidth += c.getWidth();
		resizeColumns(oldWidth, list.getClientArea().width, 0);
	}

	/**
	 * Returns the tab folder control used for this tab.
	 *
	 * @return tab folder control
	 */
	protected CTabFolder getTabFolder()
	{
		return tf;
	}

	protected void addResetAndExport(final String exportNameSuffix)
	{
		addResetAndExport(true, exportNameSuffix);
	}

	protected void addResetAndExport(final boolean resetFilterButton, final String exportNameSuffix)
	{
		filenameSuffix = exportNameSuffix;

		((GridLayout) top.getLayout()).numColumns = 4;
		final Label spacer = new Label(top, SWT.NONE);
		spacer.setLayoutData(new GridData(SWT.NONE, SWT.CENTER, true, false));
		if (resetFilterButton) {
			final Button resetFilter = new Button(top, SWT.NONE);
			resetFilter.setFont(Main.font);
			resetFilter.setText("Reset filter");
			resetFilter.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(final SelectionEvent e)
				{
					includeFilter.clear();
					excludeFilter.clear();
					asyncAddLog("reset output filter (all subsequent events will be shown)");
				}
			});
		}
		final Button export = new Button(top, SWT.NONE);
		export.setFont(Main.font);
		export.setText("Export data...");
		export.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final FileDialog dlg = new FileDialog(Main.shell, SWT.SAVE);
				dlg.setText("Export data as CSV");
				dlg.setOverwrite(true);

				// provide a default filename with time stamp, but allow overruling by user
				final String filename;
				if (prevFilename != null)
					filename = prevFilename;
				else {
					// ISO 8601 would be yyyyMMddTHHmmss, but its not really readable.
					final String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
					filename = filenamePrefix + timestamp + filenameSuffix;
				}
				dlg.setFileName(filename.replace(':', '.')); // avoid illegal char with USB vendor:product
				final String resource = dlg.open();
				if (resource == null)
					return;
				if (!filename.equals(dlg.getFileName()))
					prevFilename = dlg.getFileName();

				saveAs(resource);
			}
		});
	}

	protected void setExportName(final String prefix, final String suffix)
	{
		filenamePrefix = prefix;
		filenameSuffix = suffix;
	}

	protected void saveAs(final String resource)
	{
		asyncAddLog("Export data in CSV format to " + resource);
		try {
			final char comma = ',';
			final char quote = '\"';
			final char delim = '\n';

			final Writer w = Files.newBufferedWriter(Paths.get(resource), StandardCharsets.UTF_8);
			// write list header
			w.append(list.getColumn(0).getText());
			for (int i = 1; i < list.getColumnCount(); i++)
				w.append(comma).append(list.getColumn(i).getText());
			w.write(delim);

			// write list
			for (int i = 0; i < list.getItemCount(); i++) {
				final TableItem ti = list.getItem(i);
				w.append(quote).append(ti.getText(0)).append(quote);
				for (int k = 1; k < list.getColumnCount(); k++)
					w.append(comma).append(quote).append(ti.getText(k)).append(quote);
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

	SelectionListener defaultSelected(final Consumer<SelectionEvent> onSelection)
	{
		return new SelectionAdapter() {
			@Override
			public void widgetDefaultSelected(final SelectionEvent e)
			{
				try {
					onSelection.accept(e);
				}
				catch (final RuntimeException rte) {
					asyncAddLog(rte.getMessage());
				}
			}
		};
	}

	SelectionListener selected(final Consumer<SelectionEvent> onSelection)
	{
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					onSelection.accept(e);
				}
				catch (final RuntimeException rte) {
					asyncAddLog(rte.getMessage());
				}
			}
		};
	}

	private static List createLogView(final Composite parent, final Sash sash)
	{
		final List l = new List(parent, SWT.MULTI | SWT.V_SCROLL);
		l.setFont(Main.font);
		final FormData logData = new FormData();
		logData.top = new FormAttachment(sash);
		logData.bottom = new FormAttachment(100);
		logData.left = new FormAttachment(0);
		logData.right = new FormAttachment(100);
		l.setLayoutData(logData);
		l.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(final KeyEvent e)
			{
				if ((e.stateMask == SWT.COMMAND || e.stateMask == SWT.CTRL) && e.keyCode == 'c') {
					final String[] selection = ((List) e.widget).getSelection();
					final String textData = Arrays.asList(selection).stream().collect(Collectors.joining("\n"));
					if (textData.length() > 0) {
						final TextTransfer textTransfer = TextTransfer.getInstance();
						final Clipboard cb = new Clipboard(Main.display);
						cb.setContents(new Object[] { textData }, new Transfer[] { textTransfer });
						cb.dispose();
						e.doit = false;
					}
				}
			}
		});
		return l;
	}

	private void resizeColumns(final int oldWidth, final int newWidth, final int startColumn)
	{
		if (oldWidth == newWidth)
			return;
		final TableColumn[] cols = list.getColumns();
		list.setLayoutDeferred(true);
		int used = 0;
		// because of column width rounding we have to handle
		// last column as special case to minimize flickering of horizontal scroll bar
		for (int i = startColumn; i < cols.length - 1; ++i) {
			final int width = Math.round((float) newWidth * cols[i].getWidth() / oldWidth);
			used += width;
			cols[i].setData("resized", "");
			cols[i].setWidth(width);
		}
		cols[cols.length - 1].setData("resized", "");
		cols[cols.length - 1].setWidth(newWidth - used);
		list.setLayoutDeferred(false);
	}
}

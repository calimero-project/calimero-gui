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

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import tuwien.auto.calimero.log.LogLevel;
import tuwien.auto.calimero.log.LogWriter;

/**
 * @author B. Malinowsky
 */
class BaseTabLayout
{
	final CTabItem tab;
	final Composite workArea;
	final Composite top;
	final Table list;
	final List log;
	final LogWriter logWriter;

	int listItemMargin = 2;

	private final CTabFolder tf;
	private Label infoLabel;

	// debounce the menu right click on OS X
	private static final long bounce = 50; //ms
	private long timeLastMenu;

	// filters for list output, column-based
	final Map<Integer, String> includeFilter = Collections
			.synchronizedMap(new HashMap<Integer, String>());
	final Map<Integer, ArrayList<String>> excludeFilter = Collections
			.synchronizedMap(new HashMap<Integer, ArrayList<String>>());

	private String filenameSuffix;
	private String prevFilename;

	BaseTabLayout(final CTabFolder tf, final String tabTitle, final String info)
	{
		this(tf, tabTitle, info, true);
	}

	BaseTabLayout(final CTabFolder tf, final String tabTitle, final String info,
		final boolean showClose)
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
		tab.addDisposeListener(new DisposeListener()
		{
			public void widgetDisposed(final DisposeEvent e)
			{
				onDispose(e);
			}
		});
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
		sash.addListener(SWT.Selection, new Listener()
		{
			public void handleEvent(final Event e)
			{
				sashData.top = new FormAttachment(Math.round(e.y * 100.0f
					/ splitted.getBounds().height));
				splitted.layout();
			}
		});

		list = new Table(splitted, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
		final FormData tableData = new FormData();
		tableData.top = new FormAttachment(splitted);
		tableData.bottom = new FormAttachment(sash);
		tableData.left = new FormAttachment(0);
		tableData.right = new FormAttachment(100);
		list.setLayoutData(tableData);
		list.setLinesVisible(false);
		list.setHeaderVisible(true);
		list.setFont(Main.font);
		list.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetDefaultSelected(final SelectionEvent e)
			{
				final TableItem i = (TableItem) e.item;
				if (i.getData("internal") == null)
					onListItemSelected(e);
			}
		});
		initTableBottom(splitted, sash);
		log = createLogView(splitted, sash);
		logWriter = new LogWriter()
		{
			@Override
			public void close()
			{}

			@Override
			public void flush()
			{}

			@Override
			public void write(final String logService, final LogLevel level, final String msg)
			{
				write(logService, level, msg, null);
			}

			@Override
			public void write(final String logService, final LogLevel level, final String msg,
				final Throwable t)
			{
				String s = "[" + level.toString() + "] " + msg;
				if (t != null && t.getMessage() != null)
					s += " (" + t.getMessage() + ")";
				asyncAddLog(s);
				if (t != null) {
					final StackTraceElement[] ste = t.getStackTrace();
					asyncAddLog("Error trace:");
					for (final StackTraceElement e : ste)
						asyncAddLog("    " + e.toString());
				}
			}
		};
		workArea.layout();
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

	protected void setHeaderInfo(final String info)
	{
		infoLabel.setText(info);
	}

	/**
	 * Override in subtypes.
	 *
	 * @param e table selection event
	 */
	protected void onListItemSelected(final SelectionEvent e)
	{}

	/**
	 * Adds a log string asynchronously to the log list.
	 *
	 * @param s log text
	 */
	protected final void asyncAddLog(final String s)
	{
		Main.asyncExec(new Runnable()
		{
			public void run()
			{
				if (log.isDisposed())
					return;
				log.add(s);
				log.setTopIndex(log.getItemCount() - 1);
			}
		});
	}

	/**
	 * Adds a list item asynchronously to the list.
	 *
	 * @param itemText list item text of each column
	 * @param keys key entry of the item for each column, might be <code>null</code>
	 * @param data data entry of the item for each column, might be <code>null</code> if
	 *        <code>keys</code> is <code>null</code>
	 */
	protected void asyncAddListItem(final String[] itemText, final String[] keys,
		final String[] data)
	{
		Main.asyncExec(new Runnable()
		{
			public void run()
			{
				addListItem(itemText, keys, data);
			}
		});
	}

	// this method must be invoked from the GUI thread only
	void addListItem(final String[] itemText, final String[] keys, final String[] data)
	{
		if (list.isDisposed())
			return;

		// we only scroll to show the new item if the list is completely scrolled down
		// hence, check what items are shown currently
		final int first = list.getTopIndex();
		final Rectangle area = list.getClientArea();
		final int height = list.getItemHeight();
		final int headerHeight = list.getHeaderHeight();
		final int visible = (area.height - headerHeight + height - 1) / height;
		final int last = first + visible;
		final int total = list.getItemCount();
		final boolean atEnd = last >= total;

		// add item
		final TableItem item = new TableItem(list, SWT.NONE);
		item.setText(itemText);
		if (keys != null)
			for (int i = 0; i < keys.length; i++)
				item.setData(keys[i], data[i]);

		if (atEnd)
			list.showItem(item);
	}

	/**
	 * Enables automatic list column adjusting for the main list.
	 */
	protected void enableColumnAdjusting()
	{
		list.addControlListener(new ControlAdapter()
		{
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
			c.addControlListener(new ControlAdapter()
			{
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
	 * @return tab folder control
	 */
	protected CTabFolder getTabFolder()
	{
		return tf;
	}

	protected void addResetAndExport(final String exportNameSuffix)
	{
		filenameSuffix = exportNameSuffix;

		((GridLayout) top.getLayout()).numColumns = 4;
		final Label spacer = new Label(top, SWT.NONE);
		spacer.setLayoutData(new GridData(SWT.NONE, SWT.CENTER, true, false));
		final Button resetFilter = new Button(top, SWT.NONE);
		resetFilter.setFont(Main.font);
		resetFilter.setText("Reset filter");
		resetFilter.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				includeFilter.clear();
				excludeFilter.clear();
				asyncAddLog("reset output filter (all subsequent events will be shown)");
			}
		});
		final Button export = new Button(top, SWT.NONE);
		export.setFont(Main.font);
		export.setText("Export data...");
		export.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final FileDialog dlg = new FileDialog(Main.shell, SWT.SAVE);
				dlg.setText("Export data to CSV");
				dlg.setOverwrite(true);

				// provide a default filename with time stamp, but allow overruling by user
				final String filename;
				if (prevFilename != null)
					filename = prevFilename;
				else {
					// ISO 8601 would be yyyyMMddTHHmmss, but its not really readable.
					final String timestamp = new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss")
							.format(new Date());
					filename = timestamp + filenameSuffix;
				}
				dlg.setFileName(filename);
				final String resource = dlg.open();
				if (resource == null)
					return;
				if (!filename.equals(dlg.getFileName()))
					prevFilename = dlg.getFileName();

				saveAs(resource);
			}
		});
	}

	protected void saveAs(final String resource)
	{
		asyncAddLog("Export data in CSV format to " + resource);
		try {
			final char comma = ',';
			final char quote = '\"';
			final char delim = '\n';

			final FileWriter w = new FileWriter(resource);
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

	private List createLogView(final Composite parent, final Sash sash)
	{
		final List l = new List(parent, SWT.MULTI | SWT.V_SCROLL);
		l.setBackground(new Color(Main.display, 255, 255, 255));
		l.setFont(Main.font);
		final FormData logData = new FormData();
		logData.top = new FormAttachment(sash);
		logData.bottom = new FormAttachment(100);
		logData.left = new FormAttachment(0);
		logData.right = new FormAttachment(100);
		l.setLayoutData(logData);
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

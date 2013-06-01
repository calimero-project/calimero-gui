/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006-2012 B. Malinowsky

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
*/

package tuwien.auto.calimero.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Listener;
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
	final Table list;
	final List log;
	final LogWriter logWriter;

	int listItemMargin = 2;

	private final CTabFolder tf;
	private Label infoLabel;

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
			infoLabel = new Label(workArea, SWT.WRAP);
			infoLabel.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
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
//				if (t != null) {
//					final StackTraceElement[] ste = t.getStackTrace();
//					asyncAddLog("Error trace:");
//					for (final StackTraceElement e : ste)
//						asyncAddLog("    " + e.toString());
//				}
			}
		};
		workArea.layout();
	}

	/**
	 * Override in subtypes.
	 */
	protected void initWorkAreaTop()
	{}

	/**
	 * Override in subtypes.
	 * 
	 * @param parent parent composite
	 * @param sash sash delimiter on bottom of table
	 */
	protected void initTableBottom(final Composite parent, final Sash sash)
	{}

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
		final TableItem item = new TableItem(list, SWT.NONE);
		item.setText(itemText);
		if (keys != null)
			for (int i = 0; i < keys.length; i++)
				item.setData(keys[i], data[i]);
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

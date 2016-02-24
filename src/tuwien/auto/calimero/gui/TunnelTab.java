/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2016 B. Malinowsky

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TableColumn;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.datapoint.DatapointMap;
import tuwien.auto.calimero.dptxlator.DPT;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.dptxlator.TranslatorTypes.MainType;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.exception.KNXFormatException;
import tuwien.auto.calimero.exception.KNXIllegalArgumentException;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.process.ProcessEvent;
import tuwien.auto.calimero.tools.ProcComm;
import tuwien.auto.calimero.xml.KNXMLException;
import tuwien.auto.calimero.xml.XMLFactory;
import tuwien.auto.calimero.xml.XMLReader;

/**
 * @author B. Malinowsky
 */
class TunnelTab extends BaseTabLayout
{
	private final class ProcCommWrapper extends ProcComm
	{
		private ProcCommWrapper(final String[] args) throws KNXException
		{
			super(args, logWriter);
			LogManager.getManager().addWriter("tools", logWriter);
		}

		private void read(final Datapoint dp)
		{
			try {
				pc.read(dp);
			}
			catch (final KNXException e) {
				asyncAddLog(e.getMessage());
			}
			catch (final KNXIllegalArgumentException e) {
				asyncAddLog(e.getMessage());
			}
			catch (final InterruptedException e) {
				asyncAddLog(e.getMessage());
			}
		}

		void write(final Datapoint dp, final String value)
		{
			try {
				pc.write(dp, value);
			}
			catch (final KNXException e) {
				asyncAddLog(e.getMessage());
			}
		}

		@Override
		protected void onGroupEvent(final ProcessEvent e)
		{
			final Datapoint dp = model.get(e.getDestination());
			final String svc = e.getServiceCode() == 0x00 ? "read request"
					: e.getServiceCode() == 0x40 ? "read response" : "write";
			try {
				final byte[] asdu = e.getASDU();
				String value = "[empty]";
				if (asdu.length > 0)
					value = dp != null ? asString(asdu, dp.getMainNumber(), dp.getDPT()) : "n/a";

				final String now = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance()
						.getTime());
				final String[] item = new String[] { "" + ++eventCounter,
					"" + eventCounterFiltered, now, e.getSourceAddr().toString(),
					e.getDestination().toString(), svc, DataUnitBuilder.toHex(asdu, " "), value };
				if (applyFilter(item))
					return;
				// increment filtered counter after filter
				++eventCounterFiltered;
				asyncAddListItem(item, null, null);
			}
			catch (final KNXException e1) {
				asyncAddLog("error: " + e1.getMessage());
			}
			catch (final KNXIllegalArgumentException e1) {
				asyncAddLog("error: " + e1.getMessage());
			}
		}

		@Override
		protected void onCompletion(final Exception thrown, final boolean canceled)
		{
			super.onCompletion(thrown, canceled);
			// we might lose the last log output from ProcComm (i.e., not dispatched yet)
			LogManager.getManager().removeWriter(null, logWriter);
		}
	}

	private Composite editArea;
	private ProcCommWrapper pc;
	private Combo points;
	private DatapointMap model = new DatapointMap();

	private long eventCounter;
	private long eventCounterFiltered = 1;
	private final ConnectArguments connect;

	TunnelTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, (args.protocol + " connection to " + args.name), "Connecting"
				+ (args.remote == null ? "" : " to " + args.remote) + " on port " + args.port
				+ (args.useNat() ? ", using NAT" : ""));
		connect = args;

		list.setLinesVisible(true);
		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(30);
		final TableColumn cntf = new TableColumn(list, SWT.RIGHT);
		cntf.setText("# (Filtered)");
		cntf.setWidth(40);
		final TableColumn time = new TableColumn(list, SWT.RIGHT);
		time.setText("Time");
		time.setWidth(35);
		final TableColumn src = new TableColumn(list, SWT.LEFT);
		src.setText("Source");
		src.setWidth(40);
		final TableColumn dst = new TableColumn(list, SWT.LEFT);
		dst.setText("Destination");
		dst.setWidth(50);
		final TableColumn svc = new TableColumn(list, SWT.LEFT);
		svc.setText("Service");
		svc.setWidth(50);
		final TableColumn frame = new TableColumn(list, SWT.LEFT);
		frame.setText("ASDU (hex)");
		frame.setWidth(100);
		final TableColumn decoded = new TableColumn(list, SWT.LEFT);
		decoded.setText("Decoded ASDU");
		decoded.setWidth(100);
		enableColumnAdjusting();

		initFilterMenu();
		openGroupMonitor();
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		addResetAndExport("_tunnel.csv");
	}

	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#initTableBottom(
	 * org.eclipse.swt.widgets.Composite)
	 */
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
		row.spacing = 10;
//		row.fill = true;
		row.center = true;
		editArea.setLayout(row);

		final Button load = new Button(editArea, SWT.NONE);
		load.setText("Load datapoints ...");
		load.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final org.eclipse.swt.events.SelectionEvent e)
			{
				loadDatapoints();
			}
		});
		points = new Combo(editArea, SWT.DROP_DOWN);
		setFieldSize(points, 15);

		final Button read = new Button(editArea, SWT.NONE);
		read.setText("Read");
		read.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					final Datapoint dp = model.get(new GroupAddress(points.getText()));
					if (dp != null)
						pc.read(dp);
					else
						asyncAddLog("datapoint " + points.getText() + " not loaded");
				}
				catch (final KNXFormatException e1) {
					asyncAddLog(e1.getMessage());
				}
			}
		});

		final Button write = new Button(editArea, SWT.NONE);
		write.setText("Write");
		final Combo value = new Combo(editArea, SWT.DROP_DOWN);
		setFieldSize(value, 15);
		final Label unit = new Label(editArea, SWT.NONE);

		write.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					final Datapoint dp = model.get(new GroupAddress(points.getText()));
					if (dp != null)
						pc.write(dp, value.getText());
					else
						asyncAddLog("datapoint " + points.getText() + " not loaded");
				}
				catch (final KNXFormatException e1) {
					asyncAddLog(e1.getMessage());
				}
			}
		});
		points.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					value.removeAll();
					final Datapoint dp = model.get(new GroupAddress(points.getText()));
					if (dp == null)
						return;
					final MainType t = TranslatorTypes.getMainType(dp.getMainNumber());
					if (t != null) {
						if (t.getSubTypes().containsKey(dp.getDPT())) {
							final DPT dpt = (DPT) t.getSubTypes().get(dp.getDPT());
							value.add(dpt.getLowerValue());
							value.add(dpt.getUpperValue());
							unit.setText(dpt.getUnit());
						}
					}
				}
				catch (final KNXException e1) {
					asyncAddLog(e1.getMessage());
				}
				unit.pack(true);
			}
		});

		for (final Control c : editArea.getChildren())
			c.setEnabled(false);
	}

	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#onDispose(
	 * org.eclipse.swt.events.DisposeEvent)
	 */
	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (pc != null)
			pc.quit();
	}

	private void setFieldSize(final Combo field, final int columns)
	{
		final GC gc = new GC(field);
		final FontMetrics fm = gc.getFontMetrics();
		final int width = columns * fm.getAverageCharWidth();
		gc.dispose();
		field.setLayoutData(new RowData(field.computeSize(width, 0).x, SWT.DEFAULT));
	}

	private void openGroupMonitor()
	{
		// setup tool argument array
		final java.util.List<String> args = new ArrayList<String>();
		args.add("-verbose");
		args.addAll(connect.getArgs(true));
		args.add("monitor");
		asyncAddLog("Using command line: " + String.join(" ", args));

		// thread for connecting, it quits as soon communicator is running
		new Thread() {
			@Override
			public void run()
			{
				try {
					pc = new ProcCommWrapper(args.toArray(new String[args.size()]));
					pc.start(null);
					Main.asyncExec(new Runnable() {
						@Override
						public void run()
						{
							if (editArea.isDisposed())
								return;
							for (final Control c : editArea.getChildren())
								c.setEnabled(true);
							setHeaderInfo("Connected"
									+ (connect.remote == null ? "" : " to " + connect.remote)
									+ " on port " + connect.port
									+ (connect.useNat() ? ", using NAT" : ""));
						}
					});
				}
				catch (final Exception e) {
					asyncAddLog(e.getMessage());
					if (pc != null)
						pc.quit();
				}
			}
		}.start();
	}

	private void loadDatapoints()
	{
		final String systemID = new FileDialog(Main.shell, SWT.OPEN).open();
		if (systemID == null)
			return;
		model = new DatapointMap();
		try {
			final XMLReader r = XMLFactory.getInstance().createXMLReader(systemID);
			model.load(r);
			r.close();
			asyncAddLog("datapoints loaded from " + systemID);
		}
		catch (final KNXMLException e) {
			asyncAddLog("failed to load datapoints from " + systemID + ", " + e.getMessage()
					+ ", line " + e.getLineNumber() + ", item " + e.getBadItem());
		}
		points.removeAll();
		@SuppressWarnings("unchecked")
		final Collection<Datapoint> datapoints = model.getDatapoints();
		for (final Datapoint dp : datapoints)
			points.add(dp.getMainAddress().toString());
	}
}

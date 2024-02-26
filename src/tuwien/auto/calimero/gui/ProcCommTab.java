/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2024 B. Malinowsky

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
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

import tuwien.auto.calimero.DetachEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.KNXAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.datapoint.DatapointMap;
import tuwien.auto.calimero.datapoint.StateDP;
import tuwien.auto.calimero.dptxlator.DPT;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.dptxlator.TranslatorTypes.MainType;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.internal.Executor;
import tuwien.auto.calimero.process.LteProcessEvent;
import tuwien.auto.calimero.process.ProcessEvent;
import tuwien.auto.calimero.process.ProcessListener;
import tuwien.auto.calimero.tools.ProcComm;
import tuwien.auto.calimero.xml.KNXMLException;
import tuwien.auto.calimero.xml.XmlInputFactory;
import tuwien.auto.calimero.xml.XmlOutputFactory;
import tuwien.auto.calimero.xml.XmlReader;
import tuwien.auto.calimero.xml.XmlWriter;

/**
 * @author B. Malinowsky
 */
class ProcCommTab extends BaseTabLayout
{
	private final class ProcCommWrapper extends ProcComm
	{
		private ProcCommWrapper(final String[] args)
		{
			super(args);
		}

		private void read(final Datapoint dp)
		{
			try {
				pc.read(dp);
			}
			catch (KNXException | InterruptedException | RuntimeException e) {
				asyncAddLog(e.getMessage());
			}
		}

		private void write(final Datapoint dp, final String value)
		{
			try {
				pc.write(dp, value);
			}
			catch (KNXException | RuntimeException e) {
				asyncAddLog(e.getMessage());
			}
		}

		@Override
		protected void onGroupEvent(final ProcessEvent e) {
			final int sc = e.getServiceCode();
			final String svc = sc == 0x00 ? "read"
					: sc == 0b1111101000 ? "LTE read" : sc == 0x40 ? "read response" : sc == 0b1111101001
							? "LTE read response"
							: sc == 0b1111101010 ? "LTE write" : sc == 0b1111101011 ? "LTE info report" : "write";
			try {
				final byte[] asdu = e.getASDU();
				String value = "[empty]";
				if (asdu.length > 0) {
					if ((sc & 0b1111111100) == 0b1111101000) {
						// group property service
						value = decodeLteFrame((LteProcessEvent) e);
					}
					else {
						final Datapoint dp = model.get(e.getDestination());
						value = dp != null && dp.getDPT() != null ? asString(asdu, dp.getMainNumber(), dp.getDPT())
								: "n/a";
					}
				}

				final Instant now = Instant.now();
				final String date = dateFormatter.format(now);
				final String time = timeFormatter.format(now);
				final String dst;
				if (e instanceof LteProcessEvent)
					dst = lteTag(((LteProcessEvent) e).extFrameFormat(), e.getDestination());
				else
					dst = e.getDestination().toString();

				final String[] item = new String[] { "" + ++eventCounter, "" + eventCounterFiltered, date, time,
					e.getSourceAddr().toString(), dst, svc, HexFormat.ofDelimiter(" ").formatHex(asdu),
					value };
				if (applyFilter(item))
					return;
				// increment filtered counter after filter
				++eventCounterFiltered;
				asyncAddListItem(item, null, null);
			}
			catch (KNXException | RuntimeException e1) {
				asyncAddLog(e1);
			}
		}
	}

	private Composite editArea;
	private ProcCommWrapper pc;
	private Combo points;
	private DatapointMap<Datapoint> model = new DatapointMap<>();
	private boolean userLoadedDatapoints;

	private long eventCounter;
	private long eventCounterFiltered = 1;

	private final DateTimeFormatter dateFormatter;
	private final DateTimeFormatter timeFormatter;

	ProcCommTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, (args.access().protocol() + " connection to " + args.access().name()), "Connecting to", false, args);

		list.setLinesVisible(true);
		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(30);
		final TableColumn cntf = new TableColumn(list, SWT.RIGHT);
		cntf.setText("# (Filtered)");
		cntf.setWidth(40);
		final TableColumn date = new TableColumn(list, SWT.RIGHT);
		date.setText("Date");
		date.setWidth(35);
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

		addLogIncludeFilter(".*" + Pattern.quote(filter()) + ".*");
		addLogExcludeFilter(".*Discoverer.*", ".*DevMgmt.*", ".*calimero\\.mgmt\\..*");

		DateTimeFormatter dfmt = DateTimeFormatter.ISO_LOCAL_DATE;
		DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
		// check optional config file for user-specific date/time formats
		try {
			final Path config = Paths.get(".calimero-gui.config");
			if (Files.exists(config)) {
				final Map<String, String> formats = Files.lines(config).filter(s -> s.startsWith("monitor")).collect(Collectors
						.toMap((final String s) -> s.substring(0, s.indexOf("=")), (final String s) -> s.substring(s.indexOf("=") + 1)));
				dfmt = Optional.ofNullable(formats.get("monitor.dateFormat")).map(DateTimeFormatter::ofPattern).orElse(dfmt);
				tfmt = Optional.ofNullable(formats.get("monitor.timeFormat")).map(DateTimeFormatter::ofPattern).orElse(tfmt);
			}
		}
		catch (IOException | RuntimeException e) {
			asyncAddLog(e);
		}
		dateFormatter = dfmt.withZone(ZoneId.systemDefault());
		timeFormatter = tfmt.withZone(ZoneId.systemDefault());

		initFilterMenu();
		openGroupMonitor();

		final String filename = defaultDatapointsFilename();
		if (Files.isReadable(Path.of(filename)))
			loadDatapoints(filename);
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		addResetAndExport("_groupmon.csv");
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
		row.spacing = 10;
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
		setFieldSize(points, 50);

		final Button read = new Button(editArea, SWT.NONE);
		read.setText("Read");
		final Button write = new Button(editArea, SWT.NONE);
		write.setText("Write");
		final Combo value = new Combo(editArea, SWT.DROP_DOWN);
		setFieldSize(value, 15);
		final Label unit = new Label(editArea, SWT.NONE);

		// list of all supported DPTs by main number
		final Combo dpt = new Combo(editArea, SWT.DROP_DOWN | SWT.SIMPLE | SWT.READ_ONLY);
		dpt.add("");
		final Map<Integer, MainType> allMainTypes = TranslatorTypes.getAllMainTypes();
		allMainTypes.forEach((i, main) -> {
			dpt.add(main.getDescription());
			dpt.setData(main.getDescription(), new Object[] { main, null });
			try {
				new TreeMap<>(main.getSubTypes()).forEach((id, sub) -> {
					final boolean noUnit = sub.getUnit().isEmpty();
					final String s = "    " + id + " - " + sub.getDescription()
							+ (noUnit ? "" : " [" + sub.getUnit() + "]");
					dpt.add(s);
					dpt.setData(s, new Object[] { main, sub });
				});
			}
			catch (final KNXException e) {}
		});
		dpt.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				final Object[] data = (Object[]) dpt.getData(dpt.getText());
				if (data != null && data[1] != null) {
					value.removeAll();
					final DPT sub = (DPT) data[1];
					value.add(sub.getLowerValue());
					value.add(sub.getUpperValue());
					unit.setText(sub.getUnit());
					unit.pack(true);
				}
			}
		});

		read.addSelectionListener(selected(event -> {
			try {
				final String selectedDpt = dpt.getText();
				pc.read(fetchDatapoint(selectedDpAddress(), (Object[]) dpt.getData(selectedDpt)));
			}
			catch (final KNXException e1) {
				asyncAddLog(e1.getMessage());
			}
		}));
		write.addSelectionListener(selected(event -> {
			try {
				final String selected = dpt.getText();
				final var dp = fetchDatapoint(selectedDpAddress(), (Object[]) dpt.getData(selected));
				if (!selected.isEmpty())
					pc.write(dp, value.getText());
				else
					asyncAddLog("writing a datapoint requires a datapoint type and value");
			}
			catch (final KNXException e1) {
				asyncAddLog(e1.getMessage());
			}
		}));

		points.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				try {
					points.setToolTipText(points.getText());
					value.removeAll();
					dpt.select(0);
					final Datapoint dp = model.get(selectedDpAddress());
					if (dp == null)
						return;
					final MainType t = TranslatorTypes.getMainType(dp.getMainNumber());
					if (t != null) {
						final String[] items = dpt.getItems();
						for (int i = 0; i < items.length; i++) {
							final String item = items[i];
							final Object[] data = (Object[]) dpt.getData(item);
							if (data == null)
								continue;
							if (data[0] == t && data[1] != null && ((DPT) data[1]).getID().equals(dp.getDPT())) {
								dpt.select(i);
								break;
							}
						}
						if (t.getSubTypes().containsKey(dp.getDPT())) {
							final DPT dpt = t.getSubTypes().get(dp.getDPT());
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

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (pc != null)
			pc.quit();
		saveDatapoints();
	}

	private static void setFieldSize(final Combo field, final int columns)
	{
		final GC gc = new GC(field);
		final FontMetrics fm = gc.getFontMetrics();
		final int width = (int) (columns * fm.getAverageCharacterWidth());
		gc.dispose();
		field.setLayoutData(new RowData(field.computeSize(width, 0).x, SWT.DEFAULT));
	}

	private void openGroupMonitor()
	{
		// setup tool argument array
		final java.util.List<String> args = new ArrayList<>(connect.getArgs(true));
		args.add("--lte");
		args.add("monitor");
		asyncAddLog("Using command line: " + String.join(" ", args));

		// quits as soon communicator is running
		final Runnable connector = () -> {
			try {
				pc = new ProcCommWrapper(args.toArray(new String[0]));
				final var listener = new ProcessListener() {
					@Override
					public void groupWrite(final ProcessEvent e) {}

					@Override
					public void groupReadResponse(final ProcessEvent e) {}

					@Override
					public void groupReadRequest(final ProcessEvent e) {}

					@Override
					public void detached(final DetachEvent e) {
						Main.asyncExec(() -> {
							if (editArea.isDisposed())
								return;
							for (final Control c : editArea.getChildren()) {
								if (c instanceof Button) {
									final var text = ((Button) c).getText();
									if (text.equals("Read") || text.equals("Write"))
										c.setEnabled(false);
								}
							}
							setHeaderInfoPhase("Disconnected from");
						});
					}
				};
				pc.start(listener);
				Main.asyncExec(() -> {
					if (editArea.isDisposed())
						return;
					for (final Control c : editArea.getChildren())
						c.setEnabled(true);
					setHeaderInfoPhase("Connected to");
				});
			}
			catch (final Exception e) {
				asyncAddLog(e);
				if (pc != null)
					pc.quit();
			}
		};
		Executor.execute(connector, "Connector for " + connect.access().name());
	}

	private void loadDatapoints()
	{
		final String systemID = new FileDialog(Main.shell, SWT.OPEN).open();
		if (systemID == null)
			return;
		loadDatapoints(systemID);
		userLoadedDatapoints = true;
	}

	private void loadDatapoints(final String systemId) {
		userLoadedDatapoints = false;
		model = new DatapointMap<>();
		try (XmlReader r = XmlInputFactory.newInstance().createXMLReader(systemId)) {
			model.load(r);
			asyncAddLog("datapoints loaded from " + systemId);
		}
		catch (final KNXMLException e) {
			asyncAddLog("failed to load datapoints from " + systemId + ", " + e.getMessage() + ", line "
					+ e.getLineNumber() + ", item " + e.getBadItem());
		}
		points.removeAll();
		points.setToolTipText("");

		final TreeSet<Datapoint> set = new TreeSet<>(
				Comparator.comparingInt(dp -> dp.getMainAddress().getRawAddress()));
		set.addAll(model.getDatapoints());
		for (final Datapoint dp : set)
			points.add(dp.getMainAddress().toString() + "\t" + dp.getName());
	}

	private void saveDatapoints() {
		if (userLoadedDatapoints || model.getDatapoints().isEmpty())
			return;
		final String fileName = defaultDatapointsFilename();
		try (XmlWriter w = XmlOutputFactory.newInstance().createXMLWriter(fileName)) {
			model.save(w);
		}
		catch (final KNXMLException e) {
			asyncAddLog("saving datapoint information to " + fileName, e);
		}
	}

	private String defaultDatapointsFilename() {
		final String fileName = ".datapoints_" + connect.access().serialNumber() + ".xml";
		return fileName.replaceAll(":", "-");
	}

	private Datapoint fetchDatapoint(final GroupAddress main, final Object[] dptData)
		throws KNXException {
		if (!model.contains(main)) {
			final StateDP dp = new StateDP(main, "-");
			model.add(dp);
			points.add(main.toString());
		}

		if (dptData != null) {
			final Datapoint dp = model.get(main);
			final int current = dp.getMainNumber();
			final MainType mt = (MainType) dptData[0];
			final DPT dpt = (DPT) dptData[1];
			if (mt.getMainNumber() != current || (dpt != null && !dpt.getID().equals(dp.getDPT())))
				dp.setDPT(mt.getMainNumber(),
						dpt != null ? dpt.getID() : mt.getSubTypes().entrySet().iterator().next().getValue().getID());
		}
		return model.get(main);
	}

	private GroupAddress selectedDpAddress() throws KNXFormatException
	{
		final String text = points.getText();
		int endIndex  = text.indexOf('\t');
		if (endIndex == -1)
			endIndex = text.length();
		return new GroupAddress(text.substring(0, endIndex));
	}

	private static String lteTag(final int extFormat, final KNXAddress dst) {
		// LTE-HEE bits 1 and 0 contain the extension of the group address
		final int ext = extFormat & 0b11;
		final int rawAddress = dst.getRawAddress();
		if (rawAddress == 0)
			return "broadcast";

		// geographical tags: Apartment/Room/...
		if (ext <= 1) {
			final int aptFloor = (ext << 6) | ((rawAddress & 0b1111110000000000) >> 10);
			final int room = (rawAddress & 0b1111110000) >> 4;
			final int subzone = rawAddress & 0b1111;
			return (aptFloor == 0 ? "*" : aptFloor) + "/" + (room == 0 ? "*" : room) + "/"
					+ (subzone == 0 ? "*" : subzone);
		}
		// application specific tags
		if (ext == 2) {
			final int domain = rawAddress & 0xf000;
			if (domain == 0) {
				// TODO improve output format for domain 0
				final int mapping = (rawAddress >> 5);
				final int producer = (rawAddress >> 5) & 0xf;
				final int zone = rawAddress & 0x1f;
				if (mapping < 7) {
					// distribution (segments or zones)
					final String[] zones = { "", "D HotWater", "D ColdWater", "D Vent", "DHW", "Outside", "Calendar" };
					return zone + " (Z HVAC " + zones[mapping] + ")";
				}
				// producers and their zones
				if ((mapping & 0x70) == 0x10)
					return producer + "/" + zone + " (P/Z HVAC HotWater)";
				if ((mapping & 0x70) == 0x20)
					return producer + "/" + zone + " (P/Z HVAC ColdWater)";

				final String s = String.format("%8s", Integer.toBinaryString(rawAddress & 0xfff)).replace(' ', '0');
				return "0b" + s + " (HVAC)";
			}
			return domain + "/0x" + Integer.toHexString(rawAddress & 0xfff) + " (app)";
		}
		// ext = 3, unassigned (peripheral) tags & broadcast
		return "0x" + Integer.toHexString(rawAddress & 0xfff) + " (?)";
	}
}

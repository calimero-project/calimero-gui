/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006, 2022 B. Malinowsky

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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.widgets.TableColumn;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.KNXIllegalArgumentException;
import tuwien.auto.calimero.cemi.CEMIBusMon;
import tuwien.auto.calimero.datapoint.DatapointMap;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.internal.Executor;
import tuwien.auto.calimero.link.MonitorFrameEvent;
import tuwien.auto.calimero.link.medium.RFLData;
import tuwien.auto.calimero.link.medium.RawFrame;
import tuwien.auto.calimero.link.medium.RawFrameBase;
import tuwien.auto.calimero.tools.NetworkMonitor;
import tuwien.auto.calimero.xml.KNXMLException;
import tuwien.auto.calimero.xml.XmlInputFactory;
import tuwien.auto.calimero.xml.XmlReader;

/**
 * @author B. Malinowsky
 */
class MonitorTab extends BaseTabLayout
{
	private NetworkMonitor m;
	private long eventCounter;
	private long eventCounterFiltered = 1;
	private final ConnectArguments connect;

	private final DateTimeFormatter dateFormatter;
	private final DateTimeFormatter timeFormatter;

	private final DatapointMap<?> datapoints = new DatapointMap<>();

	MonitorTab(final CTabFolder tf, final ConnectArguments args)
	{
		super(tf, "Monitor for " + args.name, headerInfo(ignoreRoutingAndRemoteAddress(args), "Open monitor on"));
		connect = args;

		final TableColumn cnt = new TableColumn(list, SWT.RIGHT);
		cnt.setText("#");
		cnt.setWidth(25);
		final TableColumn cntf = new TableColumn(list, SWT.RIGHT);
		cntf.setText("# (Filtered)");
		cntf.setWidth(45);
		final TableColumn date = new TableColumn(list, SWT.RIGHT);
		date.setText("Date");
		date.setWidth(40);
		final TableColumn time = new TableColumn(list, SWT.RIGHT);
		time.setText("Time");
		time.setWidth(40);
		final TableColumn timestamp = new TableColumn(list, SWT.RIGHT);
		timestamp.setText("Timestamp");
		timestamp.setWidth(50);
		final TableColumn status = new TableColumn(list, SWT.LEFT);
		status.setText("Sequence / status");
		status.setWidth(80);
		final TableColumn raw = new TableColumn(list, SWT.LEFT);
		raw.setText("Raw frame");
		raw.setWidth(150);
		final TableColumn decoded = new TableColumn(list, SWT.LEFT);
		decoded.setText("Decoded raw frame");
		decoded.setWidth(200);
		final TableColumn apci = new TableColumn(list, SWT.LEFT);
		apci.setText("TPCI / APCI");
		apci.setWidth(100);
		final TableColumn asdu = new TableColumn(list, SWT.LEFT);
		asdu.setText("ASDU");
		asdu.setWidth(50);
		final TableColumn decodedAsdu = new TableColumn(list, SWT.LEFT);
		decodedAsdu.setText("Decoded ASDU");
		decodedAsdu.setWidth(60);

		enableColumnAdjusting();

		final String filter = args.remote == null ? args.port : args.remote;
		addLogIncludeFilter(".*" + Pattern.quote(filter) + ".*");
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
		final String filename = defaultDatapointsFilename();
		if (Files.isReadable(Path.of(filename)))
			loadDatapoints(filename);
		startMonitor();
	}

	private void startMonitor()
	{
		final java.util.List<String> args = new ArrayList<String>();
		args.addAll(connect.getArgs(true));
		asyncAddLog("Using command line: " + String.join(" ", args));

		final class Monitor extends NetworkMonitor
		{
			Monitor(final String[] args)
			{
				super(args);
			}

			@Override
			public void start() throws KNXException, InterruptedException
			{
				super.start();
				Main.asyncExec(() -> setHeaderInfo(headerInfo(connect, "Monitoring on")));
			}

			@Override
			protected void onCompletion(final Exception thrown, final boolean canceled)
			{
				if (thrown != null)
					asyncAddLog(thrown);
				asyncAddLog("network monitor closed " + (canceled ? "(canceled)" : ""));
			}

			@Override
			public void onIndication(final FrameEvent e)
			{
				final java.util.List<String> item = new ArrayList<String>();
				// monitor event counters
				item.add(Long.toString(++eventCounter));
				item.add(Long.toString(eventCounterFiltered));
				// date/time
				final Instant now = Instant.now();
				item.add(dateFormatter.format(now));
				item.add(timeFormatter.format(now));
				// timestamp
				item.add(Long.toString(((CEMIBusMon) e.getFrame()).getTimestamp()));
				final String s = e.getFrame().toString();
				// status / sequence
				final String status = "seq ";
				final String rawFrame = ": ";
				item.add(s.substring(s.indexOf(status) + status.length(), s.indexOf(rawFrame)));
				// raw frame
				item.add(s.substring(s.indexOf(rawFrame) + rawFrame.length()));
				final RawFrame raw = ((MonitorFrameEvent) e).getRawFrame();
				if (raw != null) {
					// decoded raw frame
					item.add(raw.toString());
					if (raw instanceof RawFrameBase) {
						final RawFrameBase f = (RawFrameBase) raw;
						// tpci, apci
						item.add(DataUnitBuilder.decode(f.getTPDU(), f.getDestination()));
						// asdu
						final byte[] asdu = DataUnitBuilder.extractASDU(f.getTPDU());
						item.add(DataUnitBuilder.toHex(asdu, " "));

						// let's see if we can decode a group-addressed asdu based on datapoint information
						final var dst = f.getDestination();
						if (dst instanceof GroupAddress && datapoints.contains((GroupAddress) dst)) {
							final var datapoint = datapoints.get((GroupAddress) dst);
							try {
								final var translator = TranslatorTypes.createTranslator(datapoint.getDPT(), asdu);
								item.add(translator.getValue());
							}
							catch (KNXIllegalArgumentException | KNXException ignore) {}
						}
					}
					else if (raw instanceof RFLData) {
						final RFLData rf = (RFLData) raw;
						try {
							final String bibat = NetworkMonitor.decodeBibat(rf);
							if (!bibat.isEmpty()) {
								item.add(""); // leave tpci/apci column empty
								item.add(bibat);
							}
							else {
								item.add(DataUnitBuilder.decode(rf.getTpdu(), rf.getDestination()));
								item.add(NetworkMonitor.decodeLteFrame(rf));
							}
						}
						catch (final Exception ex) {
							asyncAddLog("decoding RF frame", ex);
						}
					}
				}
				final String[] sa = item.toArray(new String[0]);
				if (applyFilter(sa))
					return;
				// increment filtered counter after filter
				++eventCounterFiltered;
				asyncAddListItem(sa, null, null);
			}
		}

		try {
			m = new Monitor(args.toArray(new String[0]));
			Executor.execute(m);
		}
		catch (final RuntimeException e) {
			log.add("error: " + e.getMessage());
		}
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();
		addResetAndExport("_monitor.csv");
	}

	@Override
	protected void onDispose(final DisposeEvent e)
	{
		if (m != null)
			m.quit();
	}

	private String defaultDatapointsFilename() {
		final String fileName = ".datapoints_" + connect.serialNumber + ".xml";
		return fileName.replaceAll(":", "-");
	}

	private void loadDatapoints(final String systemId) {
		try (XmlReader r = XmlInputFactory.newInstance().createXMLReader(systemId)) {
			datapoints.load(r);
			asyncAddLog("datapoints loaded from " + systemId);
		}
		catch (final KNXMLException e) {
			asyncAddLog("failed to load datapoints from " + systemId + ", " + e.getMessage() + ", line "
					+ e.getLineNumber() + ", item " + e.getBadItem());
		}
	}

	// routing is not supported with netmon, remote address not used
	private static ConnectArguments ignoreRoutingAndRemoteAddress(final ConnectArguments config) {
		config.ignoreRoutingProtocol();
		config.knxAddress = "";
		return config;
	}
}

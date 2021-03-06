/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2020, 2021 B. Malinowsky

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

import static tuwien.auto.calimero.DataUnitBuilder.toHex;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import tuwien.auto.calimero.DataUnitBuilder;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.KNXException;
import tuwien.auto.calimero.KNXFormatException;
import tuwien.auto.calimero.baos.BaosService;
import tuwien.auto.calimero.baos.BaosService.ErrorCode;
import tuwien.auto.calimero.baos.BaosService.Item;
import tuwien.auto.calimero.baos.BaosService.Property;
import tuwien.auto.calimero.dptxlator.DPT;
import tuwien.auto.calimero.dptxlator.TranslatorTypes;
import tuwien.auto.calimero.dptxlator.TranslatorTypes.MainType;
import tuwien.auto.calimero.gui.ConnectDialog.ConnectArguments;
import tuwien.auto.calimero.tools.BaosClient;

class BaosTab extends BaseTabLayout {
	private final class BaosClientTool extends BaosClient {
		private BaosClientTool(final String[] args) { super(args); }

		@Override
		protected void executeBaosCommand(final String cmd) throws KNXException, InterruptedException {
			super.executeBaosCommand(cmd);
		}

		@Override
		protected void onBaosEvent(final BaosService svc) {
			super.onBaosEvent(svc);
			addBaosEvent(svc);
			if (svc.error() != ErrorCode.NoError) {
				asyncAddLog(svc.toString());
				return;
			}

			Main.asyncExec(() -> {
				if (editArea.isDisposed())
					return;
				if (svc.subService() == BaosService.GetServerItem) {
					for (final var item : svc.items()) {
						@SuppressWarnings("unchecked")
						final var propertyItem = (Item<Property>) item;
						if (!updatePropertyValue(propertyItem))
							addProperty(propertyItem);
					}
				}
			});
		}

		@Override
		protected void onCompletion(final Exception thrown, final boolean canceled) {
			if (thrown != null) {
				asyncAddLog(thrown);
			}
			Main.asyncExec(() -> {
				if (cancel.isDisposed())
					return;
				setHeaderInfo(statusInfo(2));
				cancel.setEnabled(false);
			});
		}
	}

	private final ConnectArguments connect;
	private final BaosClientTool tool;

	private Composite editArea;
	private SashForm sashForm;
	private Table properties;
	private TableEditor editor;
	private Combo dpCommand;
	private Combo dpDpt;
	private Button cancel;

	private Thread toolThread;
	private final BlockingQueue<String> commands = new LinkedBlockingDeque<>();
	private int eventCount;


	BaosTab(final CTabFolder tf, final ConnectArguments args) {
		super(tf, "BAOS view for " + uniqueId(args), headerInfo(adjustPreferRoutingConfig(args), "Connecting to"));
		connect = args;

		final String prefix = "knx-baos_" + deviceName() + "_";
		final String suffix = ".csv";
		setExportName(prefix, suffix);

		final int numberWidth = 20;
		list.setLinesVisible(true);
		final var index = new TableColumn(list, SWT.RIGHT);
		index.setText("#");
		index.setWidth(numberWidth);
		final var svc = new TableColumn(list, SWT.LEFT);
		svc.setText("Service");
		svc.setWidth(75);
		final var items = new TableColumn(list, SWT.LEFT);
		items.setText("Items");
		items.setWidth(60);
		final var error = new TableColumn(list, SWT.LEFT);
		error.setText("Error");
		error.setWidth(80);
		final TableColumn string = new TableColumn(list, SWT.LEFT);
		string.setText("String");
		string.setWidth(300);

		enableColumnAdjusting();

		addPropertyView();
		addTableEditor(properties);

		workArea.layout(true, true);

		final List<String> toolArgs = connect.getArgs(false);
		asyncAddLog("Using command line: " + String.join(" ", toolArgs));
		tool = new BaosClientTool(toolArgs.toArray(String[]::new));
		startBaosClient();
	}

	@Override
	protected void initWorkAreaTop() {
		super.initWorkAreaTop();
		addCancelButton();
		addResetAndExport(false, ".csv");
	}

	private void addCancelButton() {
		((GridLayout) top.getLayout()).numColumns = 3;
		cancel = new Button(top, SWT.NONE);
		cancel.setFont(Main.font);
		cancel.setText("Cancel");
		cancel.addSelectionListener(adapt(e -> {
			toolThread.interrupt();
			asyncAddLog("Cancel BAOS access");
			cancel.setEnabled(false);
		}));
	}

	@Override
	protected void initTableBottom(final Composite parent, final Sash sash) {
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

		addPropertyViewOption();

		Label spacer = new Label(editArea, SWT.NONE);
		var rowData = new RowData();
		rowData.height = 20;
		rowData.width = 150;
		spacer.setLayoutData(rowData);

		final Button send = new Button(editArea, SWT.NONE);
		send.setText("Send");

		final Label dpIdLabel = new Label(editArea, SWT.NONE);
		dpIdLabel.setText("DP #: ");
		final var dpId = new Text(editArea, SWT.NONE);
		dpId.setTextLimit(4);
		dpId.addVerifyListener(BaosTab::acceptDigitsOnly);

		final Label itemLabel = new Label(editArea, SWT.NONE);
		itemLabel.setText("items: ");
		final var items = new Text(editArea, SWT.NONE);
		items.addVerifyListener(BaosTab::acceptDigitsOnly);
		items.setTextLimit(2);
		items.setMessage("1");
		rowData = new RowData();
		rowData.width = 20;
		items.setLayoutData(rowData);

		dpCommand = comboBox(editArea, "");
		dpCommand.add("Read");
		dpCommand.select(0);
		for (final var cmd : BaosService.DatapointCommand.values())
			if (cmd.ordinal() > 0)
				dpCommand.add(cmd.toString().replaceAll("([A-Z])", " $1"));
		setFieldSize(dpCommand, 15);

		dpDpt = comboBox(editArea, "");
		rowData = new RowData();
		rowData.width = 150;
		dpDpt.setLayoutData(rowData);

		final var dpValue = new Combo(editArea, SWT.DROP_DOWN);
		rowData = new RowData();
		rowData.width = 100;
		dpValue.setLayoutData(rowData);

		send.addSelectionListener(adapt(e -> {
			final String id = dpId.getText();
			if (id.isEmpty())
				return;
			final String elems = items.getText();
			if (dpCommand.getSelectionIndex() == 0)
				executeCommand("get value " + id + " " + elems);
			else {
				String value = dpValue.getText();
				final var dpt = selectedDpt();
				if (dpt.isPresent())
					value = dpt.get().getID() + " " + value;
				executeCommand("set value " + id + " 1 " + dpCommand.getSelectionIndex() + " " + value);
			}
		}));

		final Map<Integer, MainType> allMainTypes = TranslatorTypes.getAllMainTypes();
		allMainTypes.forEach((i, main) -> {
			dpDpt.add(main.getDescription());
			dpDpt.setData(main.getDescription(), new Object[] { main, null });
			try {
				new TreeMap<>(main.getSubTypes()).forEach((id, sub) -> {
					final boolean noUnit = sub.getUnit().isEmpty();
					final String s = "    " + id + " - " + sub.getDescription()
							+ (noUnit ? "" : " [" + sub.getUnit() + "]");
					dpDpt.add(s);
					dpDpt.setData(s, new Object[] { main, sub });
				});
			}
			catch (final KNXException e) {}
		});
		dpDpt.addSelectionListener(adapt(e -> {
			final var dpt = selectedDpt();
			if (dpt.isPresent()) {
				dpValue.removeAll();
				dpValue.add(dpt.get().getLowerValue());
				dpValue.add(dpt.get().getUpperValue());
			}
		}));

		spacer = new Label(editArea, SWT.SEPARATOR);
		rowData = new RowData();
		rowData.height = send.getSize().y;
		rowData.width = 150;
		spacer.setLayoutData(rowData);

		final var clearMonitor = new Button(editArea, SWT.NONE);
		clearMonitor.setText("Clear monitor");
		clearMonitor.addSelectionListener(adapt(__ -> resetTable()));

		for (final Control c : editArea.getChildren())
			c.setEnabled(false);
	}

	@Override
	protected void onDispose(final DisposeEvent e) {
		if (toolThread != null)
			toolThread.interrupt();
	}

	private void addPropertyView() {
		sashForm = new SashForm(list.getParent(), SWT.HORIZONTAL);
		sashForm.moveAbove(list.getParent());
		final FormData sashData = new FormData();
		sashData.top = new FormAttachment(0);
		sashData.bottom = ((FormData) list.getLayoutData()).bottom;
		sashData.left = new FormAttachment(0);
		sashData.right = new FormAttachment(100);
		sashForm.setLayoutData(sashData);
		properties = new Table(sashForm, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
		properties.setHeaderVisible(true);
		properties.setFont(Main.font);

		properties.setLinesVisible(true);
		final var id = new TableColumn(properties, SWT.RIGHT);
		id.setText("Id");
		id.setWidth(30);

		final var name = new TableColumn(properties, SWT.LEFT);
		name.setText("Property");
		name.setWidth(160);

		final var value = new TableColumn(properties, SWT.LEFT);
		value.setText("Value");
		value.setWidth(140);

		final var readOnly = new TableColumn(properties, SWT.LEFT);
		readOnly.setText("Read-only");
		readOnly.setWidth(40);

		list.setParent(sashForm);

		sashForm.setWeights(new int[] { 1, 2 });
	}

	private void addPropertyViewOption() {
		final var propertyView = new Button(editArea, SWT.TOGGLE);
		propertyView.setText("Hide properties");
		propertyView.setSelection(true);
		propertyView.setToolTipText("BAOS Properties");
		propertyView.addSelectionListener(adapt(this::togglePropertyView));

		final var readAll = new Button(editArea, SWT.PUSH);
		readAll.setText("Read all");
		readAll.setToolTipText("Read all BAOS properties");
		readAll.addSelectionListener(adapt(this::readAllProperties));
	}

	private void addTableEditor(final Table table) {
		editor = new TableEditor(table);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;
		table.addListener(SWT.MouseDown, this::onMouseDown);
	}

	private void togglePropertyView(final SelectionEvent e) {
		final Button propview = (Button) e.widget;
		asyncAddLog(propview.getToolTipText());
		if (propview.getSelection()) {
			sashForm.setMaximizedControl(null);
		}
		else {
			propview.setText("Show properties");
			showFullTable();
		}
	}

	private void showFullTable() {
		properties.deselectAll();
		sashForm.setMaximizedControl(list);
	}

	private void readAllProperties(final SelectionEvent e) {
		for (final var property : Property.values()) {
			if (property != Property.Invalid)
				executeCommand("get property " + property.id());
		}
	}

	private static void setFieldSize(final Combo field, final int columns) {
		final GC gc = new GC(field);
		final FontMetrics fm = gc.getFontMetrics();
		final int width = (int) (columns * fm.getAverageCharacterWidth());
		gc.dispose();
		field.setLayoutData(new RowData(field.computeSize(width, 0).x, SWT.DEFAULT));
	}

	private void addProperty(final Item<Property> item) {
		final var tableItem = new TableItem(properties, SWT.NONE);
		final var property = item.info();
		final String readOnly = property.readOnly() ? "R" : "R/W";
		tableItem.setText(new String[] { "" + property.id(), property.friendlyName(), formatted(property, item.data()),
			readOnly });
		tableItem.setData("property", property);
	}

	private boolean updatePropertyValue(final Item<Property> update) {
		for (int i = 0; i < properties.getItemCount(); i++) {
			final TableItem item = properties.getItem(i);
			final var property = (Property) item.getData("property");
			if (property == update.info()) {
				item.setText(2, formatted(property, update.data()));
				return true;
			}
		}
		return false;
	}

	private Optional<DPT> selectedDpt() {
		final Object[] data = (Object[]) dpDpt.getData(dpDpt.getText());
		if (data != null && data[1] != null)
			return Optional.of((DPT) data[1]);
		return Optional.empty();
	}

	private void addBaosEvent(final BaosService svc) {
		final var items = svc.items();
		asyncAddListItem(new String[] { "" + eventCount++, "" + svc.subService(), "" + items.size(), "" + svc.error(),
			svc.toString() }, null, null);
	}

	private void resetTable() {
		list.setVisible(true);
		list.removeAll();
		eventCount = 0;
	}

	private static void acceptDigitsOnly(final VerifyEvent e) {
		final String string = e.text;
		final char[] chars = new char[string.length()];
		string.getChars(0, chars.length, chars, 0);
		for (final char c : chars) {
			if ('0' > c || c > '9') {
				e.doit = false;
				return;
			}
		}
	}

	private void onMouseDown(final Event event) {
		final Table table = properties;
		final Rectangle clientArea = table.getClientArea();
		final Point pt = new Point(event.x, event.y);

		int index = table.getTopIndex();
		while (index < table.getItemCount()) {
			boolean visible = false;
			final TableItem item = table.getItem(index);
			final int columnIndex = 2;
			final Rectangle rect = item.getBounds(columnIndex);
			final var property = (Property) item.getData("property");
			if (!property.readOnly() && rect.contains(pt)) {
				final var editCtrl = controlForEditing(property, item.getText(columnIndex));
				@SuppressWarnings("fallthrough")
				final Listener textListener = e -> {
					if (e.type == SWT.FocusOut) {
						editCtrl.dispose();
					}
					else if (e.type == SWT.Traverse) {
						switch (e.detail) {
						case SWT.TRAVERSE_RETURN:
							@SuppressWarnings("unchecked")
							final var formatter = (Supplier<String>) editCtrl.getData();
							try {
								final String text = formatter.get();
								if (!text.isEmpty()) {
									executeCommand("set property " + property.id() + " " + text);
									executeCommand("get property " + property.id());
								}
							}
							catch (final RuntimeException rte) {
								asyncAddLog(rte);
							}
							// fall through
						case SWT.TRAVERSE_ESCAPE:
							editCtrl.dispose();
							e.doit = false;
						default: // nop
						}
					}
				};
				editCtrl.setFont(Main.font);
				editCtrl.addListener(SWT.FocusOut, textListener);
				editCtrl.addListener(SWT.Traverse, textListener);
				editor.setEditor(editCtrl, item, columnIndex);
				editCtrl.setFocus();
				return;
			}
			if (!visible && rect.intersects(clientArea))
				visible = true;
			if (!visible)
				return;
			index++;
		}
	}

	private Control controlForEditing(final Property p, final String currentValue) {
		switch (p) {
		case Baudrate: {
			final var baudrate = comboBox(properties, currentValue, "19200 Bd", "115200 Bd");
			final Supplier<String> formatter = () -> {
				final int idx = baudrate.getSelectionIndex();
				return String.format("%02x", idx + 1);
			};
			baudrate.setData(formatter);
			return baudrate;
		}

		case CurrentBufferSize:
			return digitsOnlyTextBox(properties, true, currentValue);

		case ProgrammingMode:
		case IndicationSending:
		case TunnelingEnabled:
		case BaosBinaryEnabled:
		case BaosWebEnabled:
		case BaosRestEnabled:
		case HttpFileEnabled:
		case SearchRequestEnabled:
		case MenuEnabled:
		case EnableSuspend:
			return comboBox(properties, currentValue, "disabled", "enabled");

		case IndividualAddress: {
			final var ia = textBox(properties, currentValue);
			final Supplier<String> formatter = () -> {
				try {
					return toHex(new IndividualAddress(ia.getText()).toByteArray(), "");
				}
				catch (final KNXFormatException e) {
					asyncAddLog(e);
					return "";
				}
			};
			ia.setData(formatter);
			return ia;
		}

		case DeviceFriendlyName:
			final var text = textBox(properties, currentValue);
			text.setTextLimit(30);
			return text;

		case IpAssignment:
			return comboBox(properties, currentValue, "DHCP", "Manual");

		case IpAddress:
		case SubnetMask:
		case DefaultGateway:
			final var ip = textBox(properties, currentValue);
			final Supplier<String> ipFormatter = () -> {
				try {
					return toHex(InetAddress.getByName(ip.getText()).getAddress(), "");
				}
				catch (final UnknownHostException e) {
					asyncAddLog(e);
					return "";
				}
			};
			ip.setData(ipFormatter);
			return ip;

		case TimeSinceResetUnit:
			final var unit = comboBox(properties, currentValue, "ms", "seconds", "minutes", "hours");
			final char[] setting = { 'x', 's', 'm', 'h' };
			final Supplier<String> formatter = () -> String.format("%02x", (int) setting[unit.getSelectionIndex()]);
			unit.setData(formatter);
			return unit;

		case SystemTime:
			return dateTimeControl(properties);

		case SystemTimezoneOffset:
			return spinner(properties, -12, 14, currentValue);

		default:
			return null;
		}
	}

	private Spinner spinner(final Composite parent, final int min, final int max, final String currentValue) {
		final var spinner = new Spinner(properties, SWT.NONE);
		spinner.setMinimum(min);
		spinner.setMaximum(max);
		spinner.setIncrement(1);
		try {
			spinner.setSelection(Integer.parseInt(currentValue));
		}
		catch (final NumberFormatException e) {}
		final Supplier<String> formatter = () -> String.format("%02x", Integer.parseInt(spinner.getText()));
		spinner.setData(formatter);
		return spinner;
	}

	private DateTime dateTimeControl(final Composite parent) {
		final var dateTime = new DateTime(parent, SWT.TIME | SWT.BORDER);
		final Supplier<String> formatter = () -> {
			final var localTime = LocalTime.of(dateTime.getHours(), dateTime.getMinutes(), dateTime.getSeconds());
			final var localDateTime = LocalDateTime.of(LocalDate.now(), localTime);
			final var formatted = DateTimeFormatter.ISO_DATE_TIME.format(localDateTime).replace('T', ' ');
			return toHex(formatted.getBytes(StandardCharsets.ISO_8859_1), " ");
		};
		dateTime.setData(formatter);
		return dateTime;
	}

	private Text textBox(final Composite parent, final String currentValue) {
		final var text = new Text(parent, SWT.SIMPLE);
		text.setText(currentValue);
		text.selectAll();
		final Supplier<String> formatter = () -> {
			final byte[] bytes = text.getText().getBytes(StandardCharsets.UTF_8);
			return DataUnitBuilder.toHex(bytes, " ");
		};
		text.setData(formatter);
		return text;
	}

	private Combo comboBox(final Composite parent, final String currentValue, final String... values) {
		final var combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
		combo.setItems(values);
		for (int i = 0; i < values.length; i++) {
			if (currentValue.startsWith(values[i])) {
				combo.select(i);
				break;
			}
		}
		final Supplier<String> formatter = () -> String.format("%02x", combo.getSelectionIndex());
		combo.setData(formatter);
		combo.setBounds(0, -1, 0, 20);
		return combo;
	}

	private Text digitsOnlyTextBox(final Composite parent, final boolean twoByteHex,
			final String currentValue) {
		final var text = textBox(parent, currentValue);
		text.addVerifyListener(BaosTab::acceptDigitsOnly);
		final Supplier<String> formatter = () -> {
			final long l = Long.parseUnsignedLong(text.getText());
			var hex = Long.toUnsignedString(l, 16);
			if (twoByteHex && hex.length() <= 2)
				hex = "00" + hex;
			return hex.length() % 2 == 0 ? hex : "0" + hex;
		};
		text.setData(formatter);
		return text;
	}

	private void executeCommand(final String cmd) {
		if (!toolThread.isAlive()) {
			startBaosClient();
			cancel.setEnabled(true);
		}
		commands.offer(cmd);
	}

	private void startBaosClient() {
		setHeaderInfo(statusInfo(0));

		toolThread = new Thread(() -> {
			try {
				tool.start();
				Main.asyncExec(() -> {
					if (editArea.isDisposed())
						return;
					for (final Control c : editArea.getChildren())
						c.setEnabled(true);
					setHeaderInfo(statusInfo(1));
				});

				while (true) {
					final String command = commands.take();
					asyncAddLog(command);
					try {
						tool.executeBaosCommand(command);
					}
					catch (KNXException | RuntimeException e) {
						asyncAddLog(e.toString());
					}
				}
			}
			catch (final Exception e) {
				asyncAddLog(e);
			}
			finally {
				tool.quit();
			}

		}, "Calimero BAOS tool");
		toolThread.start();
	}

	// phase: 0=connecting, 1=reading, 2=completed, x=unknown
	private String statusInfo(final int phase) {
		final String status = phase == 0 ? "Connecting to"
				: phase == 1 ? "Connected to" : phase == 2 ? "Completed BAOS access to device" : "Unknown";
		final String device = connect.knxAddress.isEmpty() ? "" : " " + connect.knxAddress;
		return status + device + (connect.remote == null ? "" : " " + connect.remote) + " port " + connect.port
				+ (connect.useNat() ? " (using NAT)" : "");
	}

	private SelectionListener adapt(final Consumer<SelectionEvent> c) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				try {
					c.accept(e);
				}
				catch (final RuntimeException rte) {
					asyncAddLog(rte);
				}
			}
		};
	}

	private static String formatted(final Property p, final byte[] data) {
		switch (p) {
		// hex values
		case Invalid:
		case HardwareType:
			return toHex(data, " ");

		// version
		case HardwareVersion:
		case FirmwareVersion:
		case ApplicationVersion:
		case ProtocolVersionBinary:
		case ProtocolVersionWeb:
		case ProtocolVersionRest:
			return ((data[0] >> 4) & 0xf) + "." + (data[0] & 0xf);

		// manufacturer
		case Manufacturer:
		case ManufacturerApp:
			final int mf = (int) unsigned(data);
			return String.format("%s (%d)", BaosClient.manufacturer(mf), mf);

		// unsigned values
		case ApplicationId:
		case MaxBufferSize:
		case LengthOfDescriptionString:
		case CurrentBufferSize:
		case MaxManagementClients:
		case ConnectedManagementClients:
		case MaxTunnelingClients:
		case ConnectedTunnelingClients:
		case MaxBaosUdpClients:
		case ConnectedBaosUdpClients:
		case MaxBaosTcpClients:
		case ConnectedBaosTcpClients:
		case MaxDatapoints:
		case ConfiguredDatapoints:
		case MaxParameterBytes:
		case DownloadCounter:
			return "" + unsigned(data);

		case Baudrate:
			return baudRate(data);

		case SerialNumber:
			return knxSerialNumber(data);

		case TimeSinceReset:
			final long ms = unsigned(data);
			final var d = Duration.ofMillis(ms);
			return String.format("%d:%02d:%02d (%d ms)", d.toHours(), d.toMinutesPart(), d.toSecondsPart(), ms);

		case ConnectionState:
			return (data[0] & 0x01) != 0  ? "connected" : "disconnected";

		// enabled/disabled
		case ProgrammingMode:
		case IndicationSending:
			return (data[0] & 0x01) != 0  ? "enabled" : "disabled";

		// yes/no
		case TunnelingEnabled:
		case BaosBinaryEnabled:
		case BaosWebEnabled:
		case BaosRestEnabled:
		case HttpFileEnabled:
		case SearchRequestEnabled:
		case StructuredDatabase:
		case MenuEnabled:
		case EnableSuspend:
			return (data[0] & 0x01) != 0 ? "yes" : "no";

		case IndividualAddress:
			return new IndividualAddress(data).toString();

		case MacAddress:
			return toHex(data, ":");

		case DeviceFriendlyName:
			return string(data);

		case IpAssignment:
			return (data[0] & 0xff) == 0 ? "DHCP" : "Manual";

		case IpAddress:
		case SubnetMask:
		case DefaultGateway:
			return ipAddress(data);

		case TimeSinceResetUnit:
			return timeSinceResetUnit(data);

		case SystemTime:
			return new String(data, StandardCharsets.ISO_8859_1);

		case SystemTimezoneOffset:
			return "" + data[0];
		}
		return toHex(data, " ");
	}

	private static String knxSerialNumber(final byte[] data) {
		final var hex = toHex(data, "");
		return hex.substring(0, 4) + ":" + hex.substring(4);
	}

	private static String ipAddress(final byte[] data) {
		try {
			return InetAddress.getByAddress(data).getHostAddress();
		}
		catch (final UnknownHostException e) {}
		return "n/a";
	}

	private static String string(final byte[] data) {
		return new String(data, StandardCharsets.ISO_8859_1);
	}

	private static long unsigned(final byte[] data) {
		long value = 0;
		for (final byte b : data)
			value = value << 8 | (b & 0xff);
		return value;
	}

	private static String timeSinceResetUnit(final byte[] data) {
		switch (data[0] & 0xff) {
		case 'x': return "ms";
		case 's': return "seconds";
		case 'm': return "minutes";
		case 'h': return "hours";
		default: return toHex(data, "") + " (unknown)";
		}
	}

	private static String baudRate(final byte[] data) {
		final int v = data[0] & 0xff;
		return baudrate(v) + " (" + v + ")";
	}

	private static String baudrate(final int v) {
		switch (v) {
		case 0:  return "unknown";
		case 1:  return "19200 Bd";
		case 2:  return "115200 Bd";
		default: return "invalid";
		}
	}

	private String deviceName() {
		if (connect.knxAddress != null && !connect.knxAddress.isEmpty())
			return connect.knxAddress;
		if (connect.remote != null && !connect.remote.isEmpty())
			return connect.remote;
		return connect.port;
	}
}

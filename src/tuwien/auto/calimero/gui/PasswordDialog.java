/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2018, 2024 B. Malinowsky

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

import java.net.InetAddress;
import java.nio.file.Path;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

class PasswordDialog {
	private final Shell shell;
	private boolean canceled;

	private final Label passwordLabel;

	private String groupKey;

	private String userId;
	private String userPassword;
	private String deviceAuthCode;

	private char[] keyringPassword;

	public static PasswordDialog forKeyring(final Path keyringResource) {
		return new PasswordDialog(keyringResource);
	}

	public static PasswordDialog forProject(final Path project) {
		return new PasswordDialog(project);
	}

	PasswordDialog(final String interfaceName, final boolean secureUnicast) {
		this(interfaceName);

		passwordLabel.setText("Commissioning password");

		shell.setLayout(new GridLayout(1, false));
		final Composite userComp = new Composite(shell, SWT.NONE | SWT.TRANSPARENT);
		userComp.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		userComp.setLayout(new GridLayout(3, false));

		final Text passwordData = new Text(userComp, SWT.BORDER);
		passwordData.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		passwordData.setFont(Main.font);
		final GridData gridData = new GridData(SWT.FILL, SWT.TOP, true, false);
		passwordData.setLayoutData(gridData);

		final Label userLabel = new Label(userComp, SWT.NONE);
		userLabel.setText("User");

		final Text user = new Text(userComp, SWT.RIGHT | SWT.BORDER);
		final GridData gridData2 = new GridData(SWT.LEFT, SWT.TOP, false, false);
		gridData2.widthHint = 30;
		user.setLayoutData(gridData2);
		user.setTextLimit(3);
		user.setText("1");
		user.addVerifyListener((final VerifyEvent e) -> {
			boolean valid = true;
			for (final char c : e.text.toCharArray())
				valid &= Character.isDigit(c);
			e.doit = valid;
		});

		final Label authLabel = new Label(shell, SWT.NONE);
		authLabel.setLayoutData(new GridData(SWT.LEFT, SWT.NONE, false, false));
		authLabel.setText("Device authentication code (optional)");
		authLabel.setFont(Main.font);

		final Text authCode = new Text(shell, SWT.BORDER);
		authCode.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		authCode.setFont(Main.font);

		passwordData.setFocus();

		addDialogButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				userId = user.getText();
				if (userId.isEmpty())
					userId = "1";
				userPassword = passwordData.getText();
				deviceAuthCode = authCode.getText();
				shell.dispose();
			}
		});
	}

	PasswordDialog(final String interfaceName, final InetAddress multicastGroup) {
		this(interfaceName);

		passwordLabel.setText("Group/backbone key for multicast group " + multicastGroup.getHostAddress());

		final Text groupKeyData = new Text(shell, SWT.BORDER);
		groupKeyData.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		groupKeyData.setFont(Main.font);
		groupKeyData.setFocus();

		addDialogButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				groupKey = groupKeyData.getText();
				shell.dispose();
			}
		});
	}

	private PasswordDialog(final Path keyringResource) {
		// TODO init code copied over from private ctor
		shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.PRIMARY_MODAL | SWT.SHEET);
		shell.setLayout(new GridLayout());
		shell.setText("KNX Keyring Password");

		final Label connection = new Label(shell, SWT.NONE);
		final FontData fontData = connection.getFont().getFontData()[0];
		final Font bold = new Font(Main.display, new FontData(fontData.getName(), fontData.getHeight(), SWT.BOLD));
		connection.setFont(bold);
		connection.setText("Password for '" + keyringResource.getFileName() + "'");

		passwordLabel = new Label(shell, SWT.NONE);
		passwordLabel.setFont(Main.font);
		passwordLabel.setText(keyringResource.toString());

		final Text keyringPwdInput = new Text(shell, SWT.BORDER | SWT.PASSWORD);
		keyringPwdInput.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		keyringPwdInput.setFont(Main.font);
		keyringPwdInput.setFocus();

		addDialogButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				keyringPassword = keyringPwdInput.getTextChars();
				shell.dispose();
			}
		});
	}

	private PasswordDialog(final String interfaceName) {
		shell = new Shell(Main.shell, SWT.DIALOG_TRIM | SWT.PRIMARY_MODAL | SWT.SHEET);
		shell.setLayout(new GridLayout());
		shell.setText("KNX IP Secure");

		final Label connection = new Label(shell, SWT.NONE);
		final FontData fontData = connection.getFont().getFontData()[0];
		final Font bold = new Font(Main.display, new FontData(fontData.getName(), fontData.getHeight(), SWT.BOLD));
		connection.setFont(bold);
		connection.setText("Secure communication with '" + interfaceName + "'");

		passwordLabel = new Label(shell, SWT.NONE);
		passwordLabel.setFont(Main.font);
	}

	private void addDialogButtons(final SelectionAdapter onConnect) {
		final Composite buttons = new Composite(shell, SWT.NONE | SWT.TRANSPARENT);
		buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, true));
		final RowLayout row = new RowLayout(SWT.HORIZONTAL);
		row.fill = true;
		row.spacing = 10;
		row.wrap = false;
		buttons.setLayout(row);

		final Button connect = new Button(buttons, SWT.NONE);
		connect.setText("OK");
		connect.setLayoutData(new RowData());
		connect.addSelectionListener(onConnect);

		final Button cancel = new Button(buttons, SWT.NONE);
		cancel.setLayoutData(new RowData());
		cancel.setText("Cancel");
		cancel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				canceled = true;
				shell.dispose();
			}
		});

		shell.setDefaultButton(connect);

		shell.pack();
		final Point size = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		shell.setMinimumSize(size);
		shell.setSize(size);
	}

	boolean show() {
		shell.open();
		while (!shell.isDisposed()) {
			if (!Main.display.readAndDispatch()) {
				Main.display.sleep();
			}
		}
		return !canceled;
	}

	String groupKey() {
		return groupKey;
	}

	String user() {
		return userId;
	}

	boolean isUserPasswordHash() {
		return isHash(userPassword);
	}

	String userPassword() {
		return userPassword;
	}

	boolean isDeviceAuthHash() {
		return isHash(deviceAuthCode);
	}

	String deviceAuthCode() {
		return deviceAuthCode;
	}

	char[] keyringPassword() { return keyringPassword; }

	char[] password() { return keyringPassword; }

	private static boolean isHash(final String s) {
		return s.length() == 32 && s.matches("[0-9a-fA-F]+");
	}
}

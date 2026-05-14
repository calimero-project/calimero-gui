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

import java.io.IOException;
import java.util.StringJoiner;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import io.calimero.internal.Manifest;

/**
 * @author B. Malinowsky
 */
class About {
	private static final String title = "About Calimero";
	private static final String features = """
			Process communication
			Group && network monitoring
			Device scan && diagnostics
			KNX property && memory editor
			KNX IP Secure && Data Secure""";
	private static final String access = "KNXnet/IP · FT1.2 · USB · TP-UART · KNX RF";

	private static final String repositoryLink = "https://github.com/calimero-project/calimero-gui";
	private static final String repository = "<a href=\"" + repositoryLink + "\">GitHub</a>";
	private static final String sfDiscussionLink = "<a href=\"https://sourceforge.net/p/calimero/discussion/\">SourceForge</a>";
	private static final String projectEmail = "<a href=\"mailto:calimero.project@gmail.com\">calimero.project@gmail.com</a>";

	private static final String contact = repository + "  ·  " + sfDiscussionLink + "  ·  " + projectEmail;

	private static final String license = "Licensed under the GPL with the Classpath Exception";
	private static final String copyright = "© 2006–2026 Boris Malinowsky";

	private static final String swtLink = "https://www.eclipse.org/swt/";
	private static final String swtInfo = "Uses the <A href=\"" + swtLink + "\">Standard Widget Toolkit (SWT)</A>";


	About(final Shell parent) {
		final var shell = new Shell(parent, SWT.CLOSE);
		shell.setText(title);

		final var shellLayout = new GridLayout();
		shellLayout.marginWidth = 20;
		shellLayout.marginHeight = 16;
		shellLayout.verticalSpacing = 12;
		shell.setLayout(shellLayout);

		final var c = new Composite(shell, SWT.NONE);
		final var cLayout = new GridLayout();
		cLayout.marginWidth = 0;
		cLayout.marginHeight = 0;
		cLayout.verticalSpacing = 12;
		c.setLayout(cLayout);

		final var split = new Composite(c, SWT.NONE);
		split.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		final var splitLayout = new GridLayout(2, false);
		splitLayout.marginWidth = 0;
		splitLayout.marginHeight = 0;
		splitLayout.horizontalSpacing = 35;
		split.setLayout(splitLayout);

		final Label logo = new Label(split, SWT.NONE);
		logo.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

		try (var is = getClass().getResourceAsStream("/logo-small.png")) {
			if (is != null) {
				final var img = new Image(Main.display, is);
				logo.setImage(img);
				logo.addDisposeListener(e -> img.dispose());
			}
		}
		catch (final IOException ignore) {}

		final var info = new Composite(split, SWT.NONE);
		info.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		final var infoLayout = new GridLayout();
		infoLayout.marginWidth = 0;
		infoLayout.marginHeight = 0;
		infoLayout.marginBottom = 20;
		infoLayout.verticalSpacing = 15;
		info.setLayout(infoLayout);

		final var buildInfo = Manifest.buildInfo(About.class);

		final var header = new Composite(info, SWT.NONE);
		header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		final var topGL = new GridLayout();
		topGL.marginWidth = 0;
		topGL.verticalSpacing = 2;
		header.setLayout(topGL);

		final Label app = new Label(header, SWT.NONE);
		app.setText("Calimero " + buildInfo.version());

		final FontData[] appFD = app.getFont().getFontData();
		appFD[0].setHeight(appFD[0].getHeight() + 3);
		appFD[0].setStyle(SWT.BOLD);
		final Font titleFont = new Font(Main.display, appFD);
		app.setFont(titleFont);
		app.addDisposeListener(e -> titleFont.dispose());

		final FontData[] detailFD = shell.getFont().getFontData();
		detailFD[0].setHeight(detailFD[0].getHeight() - 2);
		final Font detailFont = new Font(Main.display, detailFD);
		shell.addDisposeListener(e -> detailFont.dispose());

		final var joiner = new StringJoiner("   ·   ");
		buildInfo.revision().ifPresent(rev -> joiner.add("#" + rev));
		buildInfo.buildDate().ifPresent(date -> joiner.add("Built " + date.replaceAll(":\\d{2} UTC$", " UTC")));
		if (joiner.length() > 0) {
			final Label build = new Label(header, SWT.NONE);
			build.setText(joiner.toString());
			build.setFont(detailFont);
			build.setCursor(Main.display.getSystemCursor(SWT.CURSOR_HAND));
			build.setToolTipText("Click to copy");

			final var clipboard = new Clipboard(Main.display);
			shell.addDisposeListener(__ -> clipboard.dispose());
			build.addListener(SWT.MouseDown, __ -> clipboard.setContents(new Object[] { build.getText() },
					new Transfer[] { TextTransfer.getInstance() }));
		}

		final Label features = new Label(info, SWT.WRAP);
		features.setText(About.features);
		features.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final Label protocols = new Label(info, SWT.WRAP);
		protocols.setText(About.access);
		protocols.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		final SelectionAdapter openLink = new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				openLinkInBrowser(e.text);
			}
		};

		final var rightAlignedBlock = new Composite(c, SWT.NONE);
		final var rightAlignedGL = new GridLayout(1, false);
		rightAlignedGL.marginWidth = 0;
		rightAlignedBlock.setLayout(rightAlignedGL);
		rightAlignedBlock.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

		final Link contribLinks = new Link(rightAlignedBlock, SWT.NONE);
		contribLinks.setText(contact);
		contribLinks.addSelectionListener(openLink);

		final var footer = new Composite(rightAlignedBlock, SWT.NONE);
		footer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		final var footerLayout = new GridLayout();
		footerLayout.marginWidth = 0;
		footerLayout.verticalSpacing = 2;
		footer.setLayout(footerLayout);

		final Label licenseLabel = new Label(footer, SWT.WRAP);
		licenseLabel.setFont(detailFont);
		licenseLabel.setText(license);
		licenseLabel.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final Label copyright = new Label(footer, SWT.NONE);
		copyright.setFont(detailFont);
		copyright.setText(About.copyright);
		copyright.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final int swtVersion = SWT.getVersion();
		final int major = swtVersion / 1000;
		final int minor = swtVersion % 1000;

		final Link swtUsage = new Link(rightAlignedBlock, SWT.NONE);
		swtUsage.setFont(detailFont);
		swtUsage.setText(swtInfo + " version " + major + "." + minor);
		swtUsage.setToolTipText(swtLink);
		swtUsage.addSelectionListener(openLink);

		shell.pack();
		shell.open();
	}

	private static void openLinkInBrowser(final String href) {
		Program.launch(href);
	}
}

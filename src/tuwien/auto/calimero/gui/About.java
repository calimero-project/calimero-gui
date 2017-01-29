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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import tuwien.auto.calimero.Settings;

/**
 * @author B. Malinowsky
 */
public class About
{
	private static final String title = "Graphical User Interface for KNX Access";

	private static final String about = "KNX Process Communication * Group && Network Monitoring * "
			+ "KNXnet/IP Discovery * Device Scan * Device Information\n"
			+ "Supports KNXnet/IP, KNX IP, FT1.2 (BCU2), USB, TP-UART, KNX RF USB.";

	private static final String repositoryLink = "https://github.com/calimero-project/calimero-gui";
	private static final String asgLink = "https://www.auto.tuwien.ac.at";
	private static final String asgKnxLink = "https://auto.tuwien.ac.at/index.php/alab-home";

	private static final String developer = "Automation Systems Group, "
			+ "Vienna University of Technology\nA-Lab: <A>" + asgKnxLink + "</A>";

	private static final String repository = "Software repository: <A>" + repositoryLink + "</A>";
	private static final String ghIssuesLink = "https://github.com/calimero-project/calimero-gui/issues";
	private static final String sfDiscussionLink = "https://sourceforge.net/p/calimero/discussion/";
	private static final String contrib = "Issues/feature requests:\n\ton Github \u2013 <A>" + ghIssuesLink
			+ "</A>\n\ton SourceForge \u2013 <A>" + sfDiscussionLink + "</A>";

	private static final String license = "The Calimero library, tools, GUI, and documentation "
			+ "are licensed under\nthe GPL, with the Classpath Exception.";
	private static final String copyright = "Copyright \u00A9 2006, 2017.";

	private static final String swtLink = "http://www.eclipse.org/swt/";
	private static final String swtInfo = "This GUI uses the <A href=\"" + swtLink
			+ "\">Standard Widget Toolkit (SWT)</A>.";

	About(final Shell parent)
	{
		final Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setMinimumSize(350, 340);
		shell.setLayout(new GridLayout());
		shell.setBackgroundMode(SWT.INHERIT_FORCE);
		shell.setText(title);

		final Label top = new Label(shell, SWT.NONE | SWT.WRAP | SWT.CENTER);
		top.setFont(Main.font);
		top.setText(about);
		top.setLayoutData(new GridData(SWT.CENTER, SWT.NONE, true, false));

		final Composite c = new Composite(shell, SWT.NONE | SWT.TOP);
		c.setLayout(new GridLayout());
		c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		final Color white = new Color(Main.display, 255, 255, 255);
		c.setBackground(white);

		final Composite split = new Composite(c, SWT.NO_BACKGROUND | SWT.NO_FOCUS);
		final GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.marginLeft = 0;
		gridLayout.marginWidth = 0;
		split.setLayout(gridLayout);

		final SelectionAdapter openLink = new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				openLinkInBrowser(e.text);
			}
		};

		final Link link = new Link(split, SWT.NONE);
		link.setFont(Main.font);
		link.setText("\u00A9 Boris Malinowsky, <A href=\"mailto:malinowsky@auto.tuwien.ac.at\">"
				+ "malinowsky@auto.tuwien.ac.at</A>\n" + developer);
		link.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		link.addSelectionListener(openLink);

		final Label asg = new Label(split, SWT.NONE);
		asg.setLayoutData(new GridData(SWT.END, SWT.TOP, false, false));
		try (final InputStream is = getClass().getResourceAsStream("/asg-small.png")) {
			if (is != null) {
				final Image img = new Image(Main.display, is);
				asg.setImage(img);
			}
		}
		catch (final IOException e) {}
		asg.setToolTipText(asgLink);

		final Link contribLinks = new Link(c, SWT.NONE);
		contribLinks.setFont(Main.font);
		contribLinks.setText(repository + "\n" + contrib);
		contribLinks.addSelectionListener(openLink);

		final Label licenseLabel = new Label(c, SWT.NONE);
		licenseLabel.setFont(Main.font);
		licenseLabel.setText(license + " " + copyright);

		final Link swtUsage = new Link(c, SWT.NONE);
		swtUsage.setFont(Main.font);
		swtUsage.setText(swtInfo);
		swtUsage.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		swtUsage.setToolTipText(swtLink);
		swtUsage.addSelectionListener(openLink);

		final Label bar = new Label(shell, SWT.HORIZONTAL | SWT.SEPARATOR);
		bar.setLayoutData(new GridData(GridData.FILL, SWT.NONE, false, false));

		long modified = 0;
		try {
			final Class<? extends About> cl = getClass();
			final URL url = cl.getClassLoader().getResource(cl.getCanonicalName().replace('.', '/') + ".class");
			if (url.getProtocol().equals("file"))
				modified = new File(url.toURI()).lastModified();
			else if (url.getProtocol().equals("jar")) {
				final String path = url.getPath();
				modified = new File(path.substring(5, path.indexOf("!"))).lastModified();
			}
		}
		catch (URISyntaxException | RuntimeException ignore) {}
		String compiled = "";
		if (modified != 0)
			compiled = ", build date " + Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDate();

		new Label(shell, SWT.NONE).setText("Compiled with " + Settings.getLibraryHeader(false) + compiled);

		// allow scrolling for library configuration details
		final ScrolledComposite scroll = new ScrolledComposite(shell, SWT.V_SCROLL);
		scroll.setExpandVertical(true);
		scroll.setExpandHorizontal(true);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		final Font mono = new Font(parent.getDisplay(), "Courier", 12, SWT.NONE);
		final Label lib = new Label(scroll, SWT.NONE);
		lib.setFont(mono);
		lib.setText(Settings.getBundleListing().replace("+ ", ""));
		lib.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		Point size = lib.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		lib.setSize(size);

		scroll.setContent(lib);
		scroll.setMinSize(size);
		size.y /= 2;
		scroll.setSize(size);

		final Button close = new Button(shell, SWT.NONE);
		close.setLayoutData(new GridData(SWT.TRAIL, SWT.BOTTOM, false, false));
		close.setFont(Main.font);
		close.setText("Close");
		close.setFocus();
		close.addSelectionListener(new SelectionAdapter() {
			/**
			 * @param e
			 */
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				shell.dispose();
			}
		});

		size = shell.computeSize(450, SWT.DEFAULT);
		size.y -= 150;
		shell.setSize(size);
		shell.open();
	}

	private void openLinkInBrowser(final String href)
	{
		final Program p = Program.findProgram(".html");
		if (p != null)
			p.execute(href);
	}
}

/*
    Calimero GUI - A graphical user interface for the Calimero 2 tools
    Copyright (c) 2006-2013 B. Malinowsky

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
import java.io.InputStream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
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
	private static final String title = "About Calimero GUI";

	private static final String catchPhrase = "Calimero 2 - A Java library for KNX access";

	private static final String about = "Graphical user interface for Calimero 2, " +
			"using the Calimero 2 tools and library for KNX access. The library design " +
			"supports the Java 2 Platform, and Java ME CDC technology with the Foundation Profile.";

	private static final String universityLink = "http://www.tuwien.ac.at";
	private static final String asgLink = "https://www.auto.tuwien.ac.at";
	private static final String asgKnxLink = "https://www.auto.tuwien.ac.at/a-lab/knx_eib.html";

	private static final String developer = "Automation Systems Group,\n<A>" + asgKnxLink + "</A>"
			+ "\nVienna University of Technology, <A>" + universityLink + "</A>";

	private static final String license = "The Calimero library, tools, documentation, "
		+ "and this GUI are licensed under the GPL.";
	private static final String copyright = "Copyright (c) 2006-2013.";

	private static final String swtLink = "http://www.eclipse.org/swt/";
	private static final String swtInfo = "This GUI uses the "
			+ "Standard Widget Toolkit (SWT).\n<A>" + swtLink + "</A>";

	private static final Font headerFont;
	
	static {
		final FontData fd = Main.display.getSystemFont().getFontData()[0];
		fd.setHeight(fd.getHeight() + 4);
		fd.setStyle(SWT.BOLD);
		headerFont = new Font(Main.display, fd);
	}
	
	About(final Shell parent)
	{
		final Shell shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE);
		shell.setMinimumSize(300, 200);
		shell.setLayout(new GridLayout());
		shell.setText(title);

		final Label header = new Label(shell, SWT.NONE | SWT.WRAP);
		header.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		header.setFont(headerFont);
		header.setText(catchPhrase);

		final Label top = new Label(shell, SWT.NONE | SWT.WRAP);
		top.setFont(Main.font);
		top.setText(about);
		top.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final Composite c = new Composite(shell, SWT.NONE | SWT.TOP);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		final Color white = new Color(Main.display, 255, 255, 255);
		c.setBackground(white);

		final Link link = new Link(c, SWT.NONE);
		link.setFont(Main.font);
		link.setText("Author: B. Malinowsky, "
			+ "<A href=\"mailto:malinowsky@auto.tuwien.ac.at\">"
			+ "malinowsky@auto.tuwien.ac.at</A>\n\n" + developer + "\n\n"
			+ license + " " + copyright + "\n\n" + swtInfo);
		link.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));
		link.setBackground(white);
		link.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(final SelectionEvent e)
			{
				openLinkInBrowser(e.text);
			}
		});
		final Label asg = new Label(c, SWT.NONE);
		asg.setLayoutData(new GridData(SWT.END, SWT.TOP, false, false));
		final InputStream is = getClass().getResourceAsStream("asg-small.png");
		if (is != null) {
			try {
				final Image img = new Image(Main.display, is);
				asg.setImage(img);
			}
			finally {
				try {
					is.close();
				}
				catch (final IOException e) {}
			}
		}
		asg.setToolTipText(asgLink);

		final Label bar = new Label(shell, SWT.HORIZONTAL | SWT.SEPARATOR);
		bar.setLayoutData(new GridData(GridData.FILL, SWT.NONE, false, false));

		// allow scrolling for library configuration details
		final ScrolledComposite scroll = new ScrolledComposite(shell, SWT.V_SCROLL);
		scroll.setExpandVertical(true);
		scroll.setExpandHorizontal(true);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		final Label lib = new Label(scroll, SWT.NONE);
		lib.setText("Calimero 2 tools - Java package tuwien.auto.calimero.tools\n"
			+ Settings.getLibraryHeader(false) + "\n\n" + Settings.getBundleListing());
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
		close.addSelectionListener(new SelectionAdapter()
		{
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

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

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Scale;

import tuwien.auto.calimero.log.LogService.LogLevel;

/**
 * @author B. Malinowsky
 */
class LogTab extends BaseTabLayout
{
	private static final String[] levels = new String[] { "Error", "Warn", "Info", "Debug",
		"Trace" };

	private Scale scale;
	private Label loglevel;

	LogTab(final CTabFolder tf)
	{
		super(tf, "Logging", "Shows all current log output");
	}

	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#initTableBottom(
	 * org.eclipse.swt.widgets.Composite, org.eclipse.swt.widgets.Sash)
	 */
	@Override
	protected void initTableBottom(final Composite parent, final Sash sash)
	{
		list.dispose();
		((FormData) sash.getLayoutData()).top = new FormAttachment(0);
		sash.setEnabled(false);
	}

	@Override
	protected void initWorkAreaTop()
	{
		super.initWorkAreaTop();

		((GridLayout) top.getLayout()).numColumns = 3;
		((GridLayout) top.getLayout()).makeColumnsEqualWidth = true;

		loglevel = new Label(top, SWT.NONE);

		final Composite scaleArea = new Composite(top, SWT.NONE);
		final GridLayout layout = new GridLayout(3, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		scaleArea.setLayout(layout);
		scaleArea.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

		final Label logMin = new Label(scaleArea, SWT.NONE);
		logMin.setText(levels[0]);

		scale = new Scale(scaleArea, SWT.HORIZONTAL);
		scale.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));
		// trace, debug, info, warn, error
		scale.setMinimum(LogLevel.ERROR.ordinal());
		scale.setMaximum(LogLevel.TRACE.ordinal());
		scale.setIncrement(1);
		scale.setPageIncrement(1);
		scale.addListener(SWT.Selection, event -> adjustLogLevel(scale.getSelection()));

		final Label logMax = new Label(scaleArea, SWT.NONE);
		logMax.setText(levels[4]);

		scale.setSelection(LogLevel.DEBUG.ordinal());
		adjustLogLevel(LogLevel.DEBUG.ordinal());
		scaleArea.layout(true);
	}

	private void adjustLogLevel(final int level)
	{
		final String name = levels[level];
		loglevel.setText("Verbosity: " + name);
		scale.setToolTipText(name);
		setLogLevel(LogLevel.values()[level]);
		top.layout();
	}
}

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

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Sash;

import tuwien.auto.calimero.log.LogManager;

/**
 * @author B. Malinowsky
 */
class LogTab extends BaseTabLayout
{
	LogTab(final CTabFolder tf)
	{
		super(tf, "Logging", "Shows all current log output");
		LogManager.getManager().addWriter("", logWriter);
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
	
	/* (non-Javadoc)
	 * @see tuwien.auto.calimero.gui.BaseTabLayout#onDispose(
	 * org.eclipse.swt.events.DisposeEvent)
	 */
	@Override
	protected void onDispose(final DisposeEvent e)
	{
		LogManager.getManager().removeWriter("", logWriter);
	}
}

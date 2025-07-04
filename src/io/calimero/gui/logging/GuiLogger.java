/*
    Calimero GUI - A graphical user interface for the Calimero 3 tools
    Copyright (c) 2022, 2025 B. Malinowsky

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

package io.calimero.gui.logging;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class GuiLogger implements System.Logger {
	static final Set<LogNotifier> notifiables = ConcurrentHashMap.newKeySet();

	private final String name;
	private final Set<LogNotifier> subscribers;

	GuiLogger(final String name) {
		this.name = name;
		this.subscribers = Set.copyOf(notifiables);
	}

	@Override
	public String getName() { return name; }

	@Override
	public boolean isLoggable(final Level level) { return true; }

	@Override
	public void log(final Level level, final ResourceBundle bundle, final String msg, final Throwable thrown) {
		subscribers.forEach(s -> s.log(name, level, msg, thrown));
	}

	@Override
	public void log(final Level level, final ResourceBundle bundle, final String format, final Object... params) {
		subscribers.forEach(s -> s.log(name, level, format(bundle, format, params), null));
	}

	private static String format(final ResourceBundle bundle, final String msg, final Object... params) {
		final var s = resourceString(bundle, msg);
		if (params == null || params.length == 0)
			return s;
		return MessageFormat.format(s, params);
	}

	private static String resourceString(final ResourceBundle bundle, final String key) {
		if (bundle == null || key == null)
			return key;
		try {
			return bundle.getString(key);
		}
		catch (final MissingResourceException e) {
			return key;
		}
		catch (final ClassCastException e) {
			return bundle.getObject(key).toString();
		}
	}
}

package org.javastack.jentunnel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConfigData {
	public final List<Identity> identities;
	public final List<Connection> connections;
	public final List<Forward> forwards;

	public ConfigData() {
		this.identities = Collections.emptyList();
		this.connections = Collections.emptyList();
		this.forwards = Collections.emptyList();
	}

	public ConfigData(final Collection<Identity> identities, //
			final Collection<Connection> connections, //
			final Collection<Forward> forwards) {
		this.identities = new ArrayList<Identity>(identities);
		this.connections = new ArrayList<Connection>(connections);
		this.forwards = new ArrayList<Forward>(forwards);
	}

	public Set<Identity> identities() {
		return new CopyOnWriteArraySet<Identity>(identities);
	}

	public Set<Connection> connections() {
		return new CopyOnWriteArraySet<Connection>(connections);
	}

	public Set<Forward> forwards() {
		return new CopyOnWriteArraySet<Forward>(forwards);
	}

	public ConfigData updateHightWaterMark() {
		// find latest UID
		updateHighWatermarkUID(this.identities);
		updateHighWatermarkUID(this.connections);
		updateHighWatermarkUID(this.forwards);
		return this;
	}

	protected static final void updateHighWatermarkUID(final Collection<? extends AliasID> col) {
		for (final AliasID e : col) {
			UID.updateHighWatermark(e.getID());
		}
	}
}
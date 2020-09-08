package org.javastack.jentunnel;

import org.apache.sshd.common.util.net.SshdSocketAddress;

public class Connection implements Comparable<Connection>, AliasID {
	public static final Connection NULL = new Connection();
	public final String id;
	public final String alias;
	public final String address;
	public final int port;
	public final String identity;

	public final boolean isAutoStart;
	public final boolean isAutoReconnect;

	private transient SshdSocketAddress addr = null;

	Connection() {
		this(null, null, "", 0, "", false, false);
	}

	public Connection(final String id, final String alias, final String address, final int port,
			final String identity, //
			final boolean isAutoStart, final boolean isAutoReconnect) {
		this.id = (id == null ? UID.generate() : id);
		this.alias = alias;
		this.address = address;
		this.port = port;
		this.identity = identity;
		this.isAutoStart = isAutoStart;
		this.isAutoReconnect = isAutoReconnect;
	}

	public SshdSocketAddress getSocketAddress() {
		if (addr == null) {
			addr = new SshdSocketAddress(address, port);
		}
		return addr;
	}

	@Override
	public String toString() {
		return "id=" + id + " alias=" + alias //
				+ " address=" + address + ":" + port + " identity=" + identity;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Connection) {
			final Connection c = (Connection) obj;
			return (id.equals(c.id));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public int compareTo(final Connection o) {
		return id.compareTo(o.id);
	}

	@Override
	public String getID() {
		return id;
	}

	@Override
	public String getAlias() {
		return alias;
	}

	public AliasID getAliasFacade() {
		return new AliasID() {
			@Override
			public String getID() {
				return Connection.this.getID();
			}

			@Override
			public String getAlias() {
				return Connection.this.getAlias();
			}

			@Override
			public String toString() {
				return getAlias();
			}

			@Override
			public boolean equals(final Object obj) {
				if (obj instanceof AliasID) {
					final AliasID f = (AliasID) obj;
					return (getID().equals(f.getID()));
				}
				return false;
			}
		};
	}
}

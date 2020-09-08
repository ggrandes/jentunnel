package org.javastack.jentunnel;

import java.util.List;

import org.apache.sshd.common.util.net.SshdSocketAddress;

public abstract class Forward implements Comparable<Forward>, AliasID {
	public final String id;
	public final String alias;
	public final List<String> connections;

	Forward() {
		this(null, null, null);
	}

	public Forward(final String id, final String alias, final List<String> connections) {
		this.id = (id == null ? UID.generate() : id);
		this.alias = alias;
		this.connections = connections;
	}

	public abstract Type getType();

	public abstract SshdSocketAddress getLocalSocketAddress();

	public abstract SshdSocketAddress getRemoteSocketAddress();

	public static Forward valueOf(final Type type, //
			final String id, final String alias, final List<String> connections, //
			final String localHostname, final int localPort, //
			final String remoteHostname, final int remotePort) {
		switch (type) {
			case LOCAL:
				return new Forward.Local( //
						id, //
						alias, //
						connections, //
						localHostname, //
						localPort, //
						remoteHostname, //
						remotePort);
			case REMOTE:
				return new Forward.Remote( //
						id, //
						alias, //
						connections, //
						localHostname, //
						localPort, //
						remoteHostname, //
						remotePort);
			case DYNAMIC:
				return new Forward.Dynamic( //
						id, //
						alias, //
						connections, //
						localHostname, //
						localPort);
			default:
				throw new RuntimeException("Unknown type: " + type);
		}
	}

	@Override
	public String toString() {
		return "id=" + id + " alias=" + alias //
				+ " type=" + getType() + " connection=" + connections;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Forward) {
			final Forward f = (Forward) obj;
			return (id.equals(f.id));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public int compareTo(final Forward o) {
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

	/**
	 * Like ssh -R
	 */
	public static class Remote extends Forward {
		public final String localHostname;
		public final int localPort;
		public final String remoteBindHostname;
		public final int remoteBindPort;

		private transient SshdSocketAddress localAddr = null;
		private transient SshdSocketAddress remoteBindAddr = null;

		Remote() {
			this(null, null, null, null, 0, null, 0);
		}

		public Remote(final String id, final String alias, final List<String> connections, //
				final String localHostname, final int localPort, //
				final String remoteBindHostname, final int remoteBindPort) {
			super(id, alias, connections);
			//
			this.localHostname = localHostname;
			this.localPort = localPort;
			this.remoteBindHostname = remoteBindHostname;
			this.remoteBindPort = remoteBindPort;
		}

		@Override
		public SshdSocketAddress getLocalSocketAddress() {
			if (localAddr == null) {
				localAddr = new SshdSocketAddress(localHostname, localPort);
			}
			return localAddr;
		}

		@Override
		public SshdSocketAddress getRemoteSocketAddress() {
			if (remoteBindAddr == null) {
				remoteBindAddr = new SshdSocketAddress(remoteBindHostname, remoteBindPort);
			}
			return remoteBindAddr;
		}

		@Override
		public Type getType() {
			return Type.REMOTE;
		}

		@Override
		public String toString() {
			return super.toString() //
					+ " listen=" + remoteBindHostname + ":" + remoteBindPort //
					+ " connect=" + localHostname + ":" + localPort;
		}
	}

	/**
	 * Like ssh -L
	 */
	public static class Local extends Forward {
		public final String localBindHostname;
		public final int localBindPort;
		public final String remoteHostname;
		public final int remotePort;

		private transient SshdSocketAddress localBindAddr = null;
		private transient SshdSocketAddress remoteAddr = null;

		Local() {
			this(null, null, null, null, 0, null, 0);
		}

		public Local(final String id, final String alias, final List<String> connections, //
				final String localBindHostname, final int localBindPort, //
				final String remoteHostname, final int remotePort) {
			super(id, alias, connections);
			//
			this.localBindHostname = localBindHostname;
			this.localBindPort = localBindPort;
			this.remoteHostname = remoteHostname;
			this.remotePort = remotePort;
		}

		@Override
		public SshdSocketAddress getLocalSocketAddress() {
			if (localBindAddr == null) {
				localBindAddr = new SshdSocketAddress(localBindHostname, localBindPort);
			}
			return localBindAddr;
		}

		@Override
		public SshdSocketAddress getRemoteSocketAddress() {
			if (remoteAddr == null) {
				remoteAddr = new SshdSocketAddress(remoteHostname, remotePort);
			}
			return remoteAddr;
		}

		@Override
		public Type getType() {
			return Type.LOCAL;
		}

		@Override
		public String toString() {
			return super.toString() //
					+ " listen=" + localBindHostname + ":" + localBindPort //
					+ " connect=" + remoteHostname + ":" + remotePort;
		}
	}

	/**
	 * Like ssh -D
	 */
	public static class Dynamic extends Forward {
		private static final SshdSocketAddress ANY = new SshdSocketAddress("", 0);

		public final String localBindHostname;
		public final int localBindPort;

		private transient SshdSocketAddress localBindAddr = null;

		Dynamic() {
			this(null, null, null, null, 0);
		}

		public Dynamic(final String id, final String alias, final List<String> connections, //
				final String localBindHostname, final int localBindPort) {
			super(id, alias, connections);
			//
			this.localBindHostname = localBindHostname;
			this.localBindPort = localBindPort;
		}

		@Override
		public SshdSocketAddress getLocalSocketAddress() {
			if (localBindAddr == null) {
				localBindAddr = new SshdSocketAddress(localBindHostname, localBindPort);
			}
			return localBindAddr;
		}

		@Override
		public SshdSocketAddress getRemoteSocketAddress() {
			return ANY;
		}

		@Override
		public Type getType() {
			return Type.DYNAMIC;
		}

		@Override
		public String toString() {
			return super.toString() //
					+ " listen=" + localBindHostname + ":" + localBindPort //
					+ " connect=ANY";
		}
	}

	public static enum Type {
		REMOTE, //
		LOCAL, //
		DYNAMIC;
	}
}

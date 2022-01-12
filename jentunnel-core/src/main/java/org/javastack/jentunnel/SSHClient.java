/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.javastack.jentunnel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.config.hosts.HostConfigEntry;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ModifiedServerKeyAcceptor;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.forward.PortForwardingTracker;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.forward.DefaultForwarderFactory;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.io.input.NoCloseInputStream;
import org.apache.sshd.common.util.io.output.NoCloseOutputStream;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSHClient {
	public static final String IDENTITY_PROP_ID = SSHClient.class.getName() + ".identity.id"; // TODO:
																								 // Changeme

	private static final Logger log = LoggerFactory.getLogger(SSHClient.class);
	private static final long DEFAULT_MIN_RECONNECT_SLEEP_TIME_MILLIS = 1000;
	private static final long DEFAULT_MAX_RECONNECT_SLEEP_TIME_MILLIS = 30000;

	private final String configDirectory;
	private final ConfigUtils cfg;
	private final Set<Session> sessions;
	private final Set<Notify> notify;
	private Set<Identity> identities = null;
	private Set<Forward> forwards = null;
	private Set<Connection> connections = null;
	private HashMap<String, ScheduledExecutorService> thPool = null;
	private volatile boolean isDirtyConfig = false;
	private ServerKeyVerifier keyAcceptatorNew = null;
	private ModifiedServerKeyAcceptor keyAcceptatorModified = null;
	private FilePasswordProvider filePasswordProvider = null;

	public SSHClient(final String configDirectory) {
		this.configDirectory = configDirectory;
		this.cfg = new ConfigUtils(configDirectory);
		this.sessions = new CopyOnWriteArraySet<Session>();
		this.notify = new CopyOnWriteArraySet<Notify>();
	}

	public void init() throws IOException {
		log.info("System(parameters)={}", ManagementFactory.getRuntimeMXBean().getInputArguments());
		for (final String p : Arrays.asList( //
				"java.version", "java.vendor", "java.home", "java.io.tmpdir", //
				"os.arch", "os.name", "os.version", //
				"user.name", "user.home", "user.dir")) {
			log.info("System({})={}", p, System.getProperty(p));
		}
		//
		log.info("Directory configutarion: {}", configDirectory);
		final ConfigData data = cfg.load();
		this.identities = data.identities();
		this.forwards = data.forwards();
		this.connections = data.connections();
		log.info("Identities loaded={}", identities.size());
		log.info("Forwards loaded={}", forwards.size());
		log.info("Connections loaded={}", connections.size());
		//
		this.thPool = new HashMap<String, ScheduledExecutorService>();
		//
		final Thread cleanThread = new Thread(() -> stop());
		cleanThread.setName("ssh-clean");
		Runtime.getRuntime().addShutdownHook(cleanThread);
		//
		log.info("BouncyCastle supported={}", SecurityUtils.isBouncyCastleRegistered());
		log.info("EDDSA supported={}", SecurityUtils.isEDDSACurveSupported());
	}

	public void addNotify(final Notify notify) {
		this.notify.add(notify);
	}

	public void removeNotify(final Notify notify) {
		this.notify.remove(notify);
	}

	public void setFilePasswordProvider(final FilePasswordProvider filePasswordProvider) {
		this.filePasswordProvider = filePasswordProvider;
	}

	public void setKeyAcceptatorNew(final ServerKeyVerifier keyAcceptatorNew) {
		this.keyAcceptatorNew = keyAcceptatorNew;
	}

	public void setKeyAcceptatorModified(final ModifiedServerKeyAcceptor keyAcceptatorModified) {
		this.keyAcceptatorModified = keyAcceptatorModified;
	}

	public void stop() {
		if (sessions.isEmpty() && thPool.isEmpty()) {
			// Nothing to do
			return;
		}
		log.info("Stoping...");
		for (final Session s : sessions) {
			final String id = s.getConnectionID();
			submitTask("connection:" + s.getConnectionAlias(), () -> disconnect0(id));
		}
		for (final Entry<String, ScheduledExecutorService> e : thPool.entrySet()) {
			shutdownAndAwaitTermination(e.getKey(), e.getValue());
		}
		thPool.clear();
	}

	private void shutdownAndAwaitTermination(final String id, final ExecutorService pool) {
		log.info("ThreadPool shutdown: {}", id);
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(DEFAULT_MIN_RECONNECT_SLEEP_TIME_MILLIS * 2, TimeUnit.MILLISECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(500 //
						+ DEFAULT_MIN_RECONNECT_SLEEP_TIME_MILLIS //
						+ DEFAULT_MAX_RECONNECT_SLEEP_TIME_MILLIS //
						, TimeUnit.MILLISECONDS)) {
					log.error("ThreadPool did not terminate: {}", id);
				}
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	public void start() {
		for (final Connection c : connections) {
			if (!c.isAutoStart) {
				continue;
			}
			connect(c.id);
		}
	}

	public String getConfigDir() {
		return configDirectory;
	}

	public String relativeFile(final File f) {
		Path cfgDir = new File(configDirectory).toPath().toAbsolutePath();
		final String nativeSeparator = FileSystems.getDefault().getSeparator();
		final String relative = cfgDir.relativize(f.toPath().toAbsolutePath()).toString();
		return ("/".equals(nativeSeparator) ? relative : relative.replace(nativeSeparator, "/"));
	}

	private Future<?> submitTask(final String id, final Runnable task) {
		return submitTask(id, task, 100);
	}

	private Future<?> submitTask(final String id, final Runnable task, final long delay) {
		log.info("Submitting task id={} delay={}", id, delay);
		ScheduledExecutorService thPoolLocal = null;
		synchronized (thPool) {
			thPoolLocal = thPool.get(id);
			if (thPoolLocal == null) {
				thPoolLocal = Executors.newScheduledThreadPool(1, new ThreadFactory() {
					final AtomicInteger c = new AtomicInteger();

					@Override
					public Thread newThread(final Runnable r) {
						final Thread t = new Thread(r);
						t.setDaemon(true);
						t.setName(id + "-" + c.incrementAndGet());
						return t;
					}
				});
				thPool.put(id, thPoolLocal);
			}
		}
		return thPoolLocal.schedule(task, delay, TimeUnit.MILLISECONDS);
	}

	public void connect(final String id) {
		if ((id == null) || id.isEmpty()) {
			return;
		}
		submitTask("connection:" + getConnection(id).alias, () -> {
			disconnect0(id);
			connect0(id);
		});
	}

	private void connect0(final String id) {
		for (final Connection c : connections) {
			if (id.equals(c.id)) {
				final Identity i = findIdentity(c.identity);
				if ((i == null) || (i == Identity.NULL)) {
					log.error("Identity not found: {}", c.identity);
					continue;
				}
				final Session s = new Session(c.id, i.id);
				s.setNotify(notify);
				connect1(s);
			}
		}
	}

	private void connect1(final Session s) {
		final Connection c = s.getConnection();
		if (c == null || c == Connection.NULL) {
			log.error("Connection not found: {}", s.getConnectionID());
			return;
		}
		final Identity i = s.getIdentity();
		if (i == null || i == Identity.NULL) {
			log.error("Identity not found: {}", s.getIdentityID());
			return;
		}
		try {
			s.connect();
			s.map(filterByConnection(c.id));
			s.setEstablished(true);
			return;
		} catch (IOException e) {
			log.error("IOException: " + e);
		} catch (Exception e) {
			log.error("Exception: " + e, e);
		}
		if (s.isAutoReconnect()) {
			submitTask("connection:" + s.getConnectionAlias(), () -> {
				if (s.isAutoReconnect()) {
					connect1(s);
				}
			}, s.nextRetrySleep());
		}
	}

	public void disconnect(final String id) {
		if ((id == null) || id.isEmpty()) {
			return;
		}
		submitTask("connection:" + getConnection(id).alias, () -> disconnect0(id));
	}

	private void disconnect0(final String id) {
		final Iterator<Session> ite = sessions.iterator();
		while (ite.hasNext()) {
			final Session sess = ite.next();
			if (id.equals(sess.getConnection().id)) {
				log.info("Disconnecting {}: {}@{}", sess.getConnectionAlias(), sess.getIdentityUserName(),
						sess.getConnectionAddress());
				disconnect1(sess, true);
				sess.sessionClosed(sess.session);
			}
		}
	}

	private void disconnect1(final Session sess, final boolean gracefully) {
		sess.unmap();
		sess.disconnect(gracefully);
	}

	private Identity findIdentity(final String id) {
		for (final Identity i : identities) {
			if (i.id.equals(id)) {
				return i;
			}
		}
		return null;
	}

	private List<Forward> filterByConnection(final String id) {
		final List<Forward> ff = new ArrayList<Forward>();
		for (final Forward f : forwards) {
			for (final String connection : f.connections) {
				if (id.equals(connection)) {
					ff.add(f);
				}
			}
		}
		return Collections.unmodifiableList(ff);
	}

	public void resetIdle() {
		for (final Session s : sessions) {
			s.resetIdle();
		}
	}

	public static interface Notify {
		/**
		 * Establishing connection (transitory)
		 * 
		 * @param session of event
		 */
		public void notifyConnecting(final Session session);

		/**
		 * Connection is stablished (ok)
		 * 
		 * @param session of event
		 */
		public void notifyEstablished(final Session session);

		/**
		 * Broken connection (error)
		 * 
		 * @param session of event
		 */
		public void notifyFail(final Session session);

		/**
		 * Connection is closed (gracefully)
		 * 
		 * @param session of event
		 */
		public void notifyClosed(final Session session);
	}

	public class Session implements AutoCloseable, SessionListener {
		public static final long DEFAULT_CONNECT_TIMEOUT = 10000L; // 10 seconds
		public static final long DEFAULT_AUTH_TIMEOUT = 30000L; // 30 seconds

		private final String s_c;
		private final String s_i;

		private final List<PortForwardingTracker> trackers = new CopyOnWriteArrayList<PortForwardingTracker>();
		private SshClient client = null;
		private ClientSession session = null;
		private Set<Notify> listeners = null;

		private volatile long nextRetrysleep = DEFAULT_MIN_RECONNECT_SLEEP_TIME_MILLIS;
		private volatile boolean established = false;
		private volatile boolean disconnecting = false;
		private volatile ConnectionStatus state = ConnectionStatus.NOT_CONNECTED;

		public Session(final String c, final String i) {
			this.s_c = c;
			this.s_i = i;
		}

		public String getConnectionID() {
			return s_c;
		}

		public String getIdentityID() {
			return s_i;
		}

		public Connection getConnection() {
			return SSHClient.this.getConnection(s_c);
		}

		public Identity getIdentity() {
			return SSHClient.this.getIdentity(s_i);
		}

		public long nextRetrySleep() {
			final long n = Math.min(nextRetrysleep, DEFAULT_MAX_RECONNECT_SLEEP_TIME_MILLIS);
			if (nextRetrysleep < DEFAULT_MAX_RECONNECT_SLEEP_TIME_MILLIS) {
				nextRetrysleep *= 2;
			}
			return n;
		}

		public void setEstablished(final boolean established) {
			this.established = established;
			if (established) {
				this.nextRetrysleep = DEFAULT_MIN_RECONNECT_SLEEP_TIME_MILLIS;
			}
		}

		public boolean isAutoReconnect() {
			return (getConnection().isAutoReconnect && !disconnecting);
		}

		public String getConnectionAlias() {
			return getConnection().alias;
		}

		public ConnectionStatus getStatus() {
			return state;
		}

		public void setNotify(final Set<Notify> listeners) {
			this.listeners = listeners;
		}

		public void map(final List<Forward> forwards) {
			for (final Forward f : forwards) {
				try {
					map(f);
				} catch (Exception e) {
					log.error("Error mapping: {} error={}", f, e.getMessage(), e);
				}
			}
		}

		private void map(final Forward f) throws IOException {
			log.info("Mapping: {}", f);
			switch (f.getType()) {
				case REMOTE: {
					trackers.add(session.createRemotePortForwardingTracker(f.getRemoteSocketAddress(), //
							f.getLocalSocketAddress()));
					break;
				}
				case LOCAL: {
					trackers.add(session.createLocalPortForwardingTracker(f.getLocalSocketAddress(), //
							f.getRemoteSocketAddress()));
					break;
				}
				case DYNAMIC: {
					trackers.add(session.createDynamicPortForwardingTracker(f.getLocalSocketAddress()));
					break;
				}
			}
		}

		public void unmap() {
			for (final PortForwardingTracker m : trackers) {
				try {
					log.info("Unmapping: {}", m);
					m.close();
				} catch (Exception ign) {
				}
			}
			trackers.clear();
		}

		public void resetIdle() {
			if (session != null) {
				session.isOpen();
				session.resetIdleTimeout();
			}
		}

		@Override
		public void sessionEvent(org.apache.sshd.common.session.Session session, //
				SessionListener.Event event) {
			final Session self = this;
			if (event == SessionListener.Event.Authenticated) {
				state = ConnectionStatus.CONNECTED;
				if (self.listeners != null) {
					for (final Notify notify : self.listeners) {
						notify.notifyEstablished(self);
					}
				}
			}
		}

		@Override
		public void sessionClosed(org.apache.sshd.common.session.Session session) {
			final Session self = this;
			state = (disconnecting //
					? ConnectionStatus.NOT_CONNECTED //
					: ConnectionStatus.DISCONNECTED);
			if (!disconnecting) {
				disconnect(false);
			}
			if (self.listeners != null) {
				for (final Notify notify : self.listeners) {
					if (disconnecting) {
						notify.notifyClosed(self);
					} else {
						notify.notifyFail(self);
					}
				}
			}
			if (disconnecting) {
				sessions.remove(self);
			} else {
				if (self.isAutoReconnect() && self.established) {
					submitTask("connection:" + self.getConnectionAlias(), () -> {
						if (self.isAutoReconnect()) {
							connect1(self);
						}
					}, self.nextRetrySleep());
				}
			}
			self.setEstablished(false);
		}

		public void connect() throws IOException {
			final Session self = this;
			disconnecting = false;
			SshClient client = null;
			try {
				// Alternative with JSch http://www.jcraft.com/jsch/examples/PortForwardingR.java.html
				client = SshClient.setUpDefaultClient();
				// Used for caching private key passphrase in AskUserGUI#getPassword
				PropertyResolverUtils.updateProperty(client, IDENTITY_PROP_ID, getIdentity());
				// org.apache.sshd.client.ClientBuilder
				client.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE); // For RemotePortForwarding
				final File knownHost = new File(getConfigDir(), "known_hosts");
				if (!knownHost.exists()) {
					knownHost.createNewFile();
				}
				KnownHostsServerKeyVerifier hostVerifier = new KnownHostsServerKeyVerifier(
						((clientSession, remoteAddress, serverKey) -> {
							log.warn("Unknown server {} publickey [{}][{}] ({} [{}])", //
									remoteAddress, //
									KeyUtils.getFingerPrint(BuiltinDigests.sha256, serverKey), //
									KeyUtils.getFingerPrint(BuiltinDigests.md5, serverKey), //
									KeyUtils.getKeyType(serverKey), //
									KeyUtils.getKeySize(serverKey));
							return (keyAcceptatorNew == null) ? true
									: keyAcceptatorNew.verifyServerKey(clientSession, //
											remoteAddress, serverKey);
						}), knownHost.toPath());
				hostVerifier.setModifiedServerKeyAcceptor(
						(clientSession, remoteAddress, entry, expected, actual) -> {
							log.warn(
									"Known server {} changed publickey from [{}][{}] ({} [{}]) to [{}][{}] ({} [{}])",
									remoteAddress, //
									KeyUtils.getFingerPrint(BuiltinDigests.sha256, expected), //
									KeyUtils.getFingerPrint(BuiltinDigests.md5, expected), //
									KeyUtils.getKeyType(expected), //
									KeyUtils.getKeySize(expected), //
									KeyUtils.getFingerPrint(BuiltinDigests.sha256, actual), //
									KeyUtils.getFingerPrint(BuiltinDigests.md5, actual), //
									KeyUtils.getKeyType(actual), //
									KeyUtils.getKeySize(actual));
							return (keyAcceptatorModified == null) ? false
									: keyAcceptatorModified.acceptModifiedServerKey(clientSession, //
											remoteAddress, entry, expected, actual);
						});
				client.setServerKeyVerifier(hostVerifier);
				client.setForwarderFactory(new DefaultForwarderFactory());
				// https://github.com/apache/mina-sshd/blob/master/docs/client-setup.md
				// TODO: Nuevo PublicKey Auth?
				// client.setClientIdentityLoader(ClientIdentityLoader.DEFAULT);
				// For encrypted private keys
				client.setFilePasswordProvider((filePasswordProvider != null) //
						? filePasswordProvider //
						: FilePasswordProvider.EMPTY);
				// client.setKeyIdentityProvider(null);
				client.addSessionListener(this);
				sessions.add(self);
				state = ConnectionStatus.CONNECTING;
				if (self.listeners != null) {
					for (final Notify notify : self.listeners) {
						notify.notifyConnecting(self);
					}
				}
				CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(10));
				CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(client, Duration.ofSeconds(15));
				CoreModuleProperties.HEARTBEAT_REQUEST.set(client, "keepalive@openssh.com");

				// org.apache.sshd.client.session.ClientConnectionService
				// handleUnknownRequest(ClientConnectionService[ClientSessionImpl[test@/192.168.x.x:22]])
				// unknown global request: keepalive@openssh.com
				// client.setSessionHeartbeat(HeartbeatType.IGNORE, TimeUnit.SECONDS, 10);

				// apache.sshd.client.session.ClientSessionImpl
				// Disconnecting(ClientSessionImpl[test@/192.168.x.x:22]): SSH2_DISCONNECT_PROTOCOL_ERROR
				// - Detected IdleTimeout after 600215/600000 ms.
				// PropertyResolverUtils.updateProperty(client, FactoryManager.IDLE_TIMEOUT, 0L); // DISABLE

				// org.apache.sshd.client.session.ClientSessionImpl
				// exceptionCaught(ClientSessionImpl[test@/192.168.x.x:22])[state=Opened]
				// InterruptedByTimeoutException: null
				// PropertyResolverUtils.updateProperty(client, FactoryManager.NIO2_READ_TIMEOUT,
				// TimeUnit.DAYS.toMillis(0L)); // DISABLE
				client.start();
				log.info("Connecting to: {}@{}", getIdentityUserName(), getConnectionAddress());
				HostConfigEntry hostConfig = new HostConfigEntry(null, getConnectionAddress().getHostName(),
						getConnectionAddress().getPort(), getIdentityUserName());
				hostConfig.setIdentitiesOnly(true);
				// hostConfig.addIdentity("/tmp/sshd/id_rsa");
				final String key = getIdentityKey();
				if (!key.isEmpty()) {
					final Path idPath = Paths.get(configDirectory, key);
					if (idPath.toFile().exists()) {
						hostConfig.addIdentity(idPath);
					} else {
						log.error("Identity file not found: {}", idPath);
					}
				}
				ConnectFuture connect = client.connect(hostConfig);
				if (!connect.await(DEFAULT_CONNECT_TIMEOUT)) {
					throw new ConnectException("Unable to connect (timeout): " //
							+ getConnectionAddress());
				}
				if (!connect.isConnected()) {
					throw new ConnectException("Unable to connect (not connected): " //
							+ getConnectionAddress());
				}
				ClientSession session = connect.getClientSession();
				final String pass = getIdentityPassword();
				if (!pass.isEmpty()) {
					session.addPasswordIdentity(pass);
				}
				// session.addPublicKeyIdentity(getIdentityKey());
				session.auth().verify(DEFAULT_AUTH_TIMEOUT);
				log.info("Session established: {}@{}", getIdentityUserName(), getConnectionAddress());
				this.client = client;
				this.session = session;
			} catch (RuntimeException | IOException e) {
				state = ConnectionStatus.DISCONNECTED;
				disconnect(false);
				if (client != null) {
					try {
						client.close();
					} catch (IOException ign) {
					}
				}
				if (self.listeners != null) {
					for (final Notify notify : self.listeners) {
						notify.notifyFail(self);
					}
				}
				throw e;
			}
		}

		public void disconnect(final boolean gracefully) {
			if (gracefully) {
				disconnecting = true;
			}
			final boolean wantTrace = ((session != null) || (client != null));
			if (wantTrace) {
				log.info("Disconnecting from: {}@{}", getIdentityUserName(), getConnectionAddress());
			}
			if (session != null) {
				if (gracefully) {
					try {
						session.close(false).await(DEFAULT_CONNECT_TIMEOUT);
					} catch (Exception e) {
						session.close(true);
					}
				} else {
					trackers.clear();
					session.close(true);
				}
				session = null;
			}
			if (client != null) {
				try {
					client.stop();
				} catch (Exception ign) {
				}
				client = null;
			}
			if (wantTrace) {
				log.info("Disconnected: {}@{}", getIdentityUserName(), getConnectionAddress());
			}
		}

		private SshdSocketAddress getConnectionAddress() {
			return getConnection().getSocketAddress();
		}

		private String getIdentityUserName() {
			return getIdentity().username;
		}

		private String getIdentityPassword() {
			return getIdentity().getClearTextPassword();
		}

		private String getIdentityKey() {
			return getIdentity().keyfile;
		}

		@Override
		public void close() throws Exception {
			disconnect(false);
		}
	}

	public boolean aliasExistInIdentities(final String alias) {
		for (final Identity i : identities) {
			if (alias.equals(i.alias)) {
				return true;
			}
		}
		return false;
	}

	public boolean aliasExistInConnections(final String alias) {
		for (final Connection c : connections) {
			if (alias.equals(c.alias)) {
				return true;
			}
		}
		return false;
	}

	public boolean aliasExistInForwards(final String alias) {
		for (final Forward f : forwards) {
			if (alias.equals(f.alias)) {
				return true;
			}
		}
		return false;
	}

	public Set<Identity> getIdentities() {
		return Collections.unmodifiableSet(this.identities);
	}

	public AliasID[] getAliasedIdentities() {
		final int size = identities.size();
		AliasID[] arr = new AliasID[size];
		int i = 0;
		for (final Identity id : identities) {
			arr[i++] = id.getAliasFacade();
		}
		return arr;
	}

	public AliasID[] getAliasedConnections() {
		final int size = connections.size();
		AliasID[] arr = new AliasID[size];
		int i = 0;
		for (final Connection con : connections) {
			arr[i++] = con.getAliasFacade();
		}
		return arr;
	}

	public Set<Connection> getConnections() {
		return Collections.unmodifiableSet(this.connections);
	}

	public Set<Forward> getForwards() {
		return Collections.unmodifiableSet(this.forwards);
	}

	public Set<String> getIdentityUsage(final Identity item) {
		final String id = item.id;
		if ((id == null) || id.isEmpty()) {
			return Collections.emptySet();
		}
		final TreeSet<String> list = new TreeSet<String>();
		for (final Connection c : connections) {
			if (id.equals(c.identity)) {
				list.add(c.alias);
			}
		}
		return Collections.unmodifiableSet(list);
	}

	public Set<String> getForwardUsage(final Forward item) {
		if ((connections == null) || connections.isEmpty()) {
			return Collections.emptySet();
		}
		final TreeSet<String> list = new TreeSet<String>();
		for (final String connection : item.connections) {
			for (final Connection c : connections) {
				if (connection.equals(c.id)) {
					list.add(c.alias);
				}
			}
		}
		return Collections.unmodifiableSet(list);
	}

	public ConnectionStatus getStatus(final String id) {
		if ((id == null) || id.isEmpty()) {
			return ConnectionStatus.NOT_CONNECTED;
		}
		for (final Session sess : sessions) {
			final Connection conn = sess.getConnection();
			if (id.equals(conn.id)) {
				return sess.getStatus();
			}
		}
		return ConnectionStatus.NOT_CONNECTED;
	}

	public Identity getIdentity(final String id) {
		for (final Identity i : identities) {
			if (id.equals(i.id)) {
				return i;
			}
		}
		return Identity.NULL;
	}

	public void removeIdentity(final String id) {
		for (final Identity i : identities) {
			if (id.equals(i.id)) {
				identities.remove(i);
				setDirtyConfig(true);
			}
		}
	}

	public void setIdentity(final Identity identity) {
		final String id = identity.id;
		removeIdentity(id);
		identities.add(identity);
		setDirtyConfig(true);
	}

	public Connection getConnection(final String id) {
		for (final Connection c : connections) {
			if (id.equals(c.id)) {
				return c;
			}
		}
		return Connection.NULL;
	}

	public void removeConnection(final String id) {
		for (final Connection c : connections) {
			if (id.equals(c.id)) {
				connections.remove(c);
				setDirtyConfig(true);
			}
		}
	}

	public void setConnection(final Connection connection) {
		final String id = connection.id;
		removeConnection(id);
		connections.add(connection);
		setDirtyConfig(true);
	}

	public Forward getForward(final String id) {
		for (final Forward f : forwards) {
			if (id.equals(f.id)) {
				return f;
			}
		}
		return null;
	}

	public void removeForward(final String id) {
		for (final Forward f : forwards) {
			if (id.equals(f.id)) {
				forwards.remove(f);
				setDirtyConfig(true);
			}
		}
	}

	public void setForward(final Forward forward) {
		final String id = forward.id;
		removeForward(id);
		forwards.add(forward);
		setDirtyConfig(true);
	}

	private void setDirtyConfig(final boolean isDirty) {
		isDirtyConfig = isDirty;
	}

	public boolean isDirtyConfig() {
		return isDirtyConfig;
	}

	public void save() {
		save(null);
	}

	public void save(final ActionListener l) {
		submitTask("save", () -> {
			if (isDirtyConfig()) {
				try {
					log.info("Saving configuration...");
					synchronized (cfg) {
						cfg.save(new ConfigData(identities, connections, forwards));
					}
					log.info("Configuration saved.");
					setDirtyConfig(false);
					if (l != null) {
						l.actionPerformed(new ActionEvent(this, 1, "save", System.currentTimeMillis(), 0));
					}
				} catch (Exception e) {
					log.error("Unable to save configuration: {}", String.valueOf(e));
				}
			} else {
				log.info("Configuration already saved.");
			}
		});
	}

	private static final String cleanString(final String input) {
		final StringBuilder sb = new StringBuilder(input.length());
		boolean changed = false;
		// https://en.wikipedia.org/wiki/ISO/IEC_8859-1
		for (int i = 0; i < input.length(); i++) {
			final char c = input.charAt(i);
			if ((c >= ' ') && (c <= 0x7E)) {
				sb.append(c);
			} else {
				// Too low/high to be a good ASCII
				sb.append('_');
				changed = true;
			}
		}
		return (changed ? sb.toString() : input);
	}

	public boolean authorizePublicKey(final String id, final PublicKey pk, final String comment)
			throws IOException, GeneralSecurityException {
		// Find session
		final Iterator<Session> ite = sessions.iterator();
		ClientSession session = null;
		while (ite.hasNext()) {
			final Session sess = ite.next();
			if (id.equals(sess.getConnection().id)) {
				log.info("Session found {}: {}@{}", sess.getConnectionAlias(), sess.getIdentityUserName(),
						sess.getConnectionAddress());
				session = sess.session;
				break;
			}
		}
		if (session == null) {
			log.error("Session not found {}/{}", id, getConnection(id).alias);
			return false;
		}
		// Generate Public Key String
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(pk, cleanString(comment), os);
		final String key = new String(os.toByteArray(), 0, os.size(), StandardCharsets.ISO_8859_1);
		// Upload Public Key
		try (ClientChannel channel = session.createChannel(ClientChannel.CHANNEL_SHELL)) {
			final StringBuilder script = new StringBuilder();
			final long endTS = System.currentTimeMillis();
			script.append("exec /usr/bin/env - /bin/bash -- <<\"_SCRIPT_END_").append(endTS).append("\"")
					.append('\n');
			script.append("read TYPE KEY COMMENT <<\"_KEY_END_").append(endTS).append("\"").append('\n');
			script.append(key).append('\n');
			script.append("_KEY_END_").append(endTS).append('\n');
			script.append("umask 077").append('\n');
			script.append("cd ~").append('\n');
			script.append("[ ! -d .ssh ] && mkdir .ssh").append('\n');
			script.append("cd .ssh").append('\n');
			script.append("[ ! -f authorized_keys ] && touch authorized_keys").append('\n');
			script.append("grep -q \"$TYPE $KEY\" authorized_keys || ");
			script.append("echo \"$TYPE $KEY $COMMENT\" >> authorized_keys").append('\n');
			script.append(
					"grep -q \"$TYPE $KEY\" authorized_keys && echo '###' OK: Key configured || echo '###' ERROR: Key not configured")
					.append('\n');
			script.append("_SCRIPT_END_").append(endTS).append('\n');
			final ByteArrayOutputStream baos = new ByteArrayOutputStream(script.length() * 3);
			channel.setIn(new NoCloseInputStream(
					new ByteArrayInputStream(script.toString().getBytes(StandardCharsets.ISO_8859_1))));
			channel.setOut(new NoCloseOutputStream(baos));
			channel.setErr(new NoCloseOutputStream(System.err));
			channel.open();
			channel.waitFor(Collections.singleton(ClientChannelEvent.CLOSED), 0);
			final String[] lines = new String(baos.toByteArray(), 0, baos.size(), StandardCharsets.ISO_8859_1)
					.split("\n");
			for (final String line : lines) {
				if (line.startsWith("### OK:")) {
					log.info(line.trim());
					return true;
				} else if (line.startsWith("### ERROR:")) {
					for (final String error : lines) {
						if (error.startsWith("<") || error.startsWith(">")
								|| error.startsWith("### ERROR:")) {
							continue;
						}
						log.error(error.trim());
					}
					log.error(line.trim());
					return false;
				}
			}
			return false;
		}
	}

	public static void main(String[] args) throws Throwable {
		final Console con = System.console();
		final SSHClient client = new SSHClient("/tmp/sshd");
		client.init();
		client.start();
		if (con != null) {
			System.out.println("Press any key to exit");
			con.readLine();
		} else {
			System.out.println("Press CTRL+C to exit");
			while (true) {
				Thread.sleep(1000);
			}
		}
		client.stop();
	}
}

package org.javastack.jentunnel.workaround_bug_sshd1063;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Collection;

import org.apache.sshd.client.config.hosts.HostPatternsHolder;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.net.SshdSocketAddress;

/**
 * FIXME: Workaround Bug SSHD-1063
 */
public class CustomKnownHostsServerKeyVerifier extends KnownHostsServerKeyVerifier {
	public CustomKnownHostsServerKeyVerifier(final ServerKeyVerifier delegate, final Path file) {
		super(delegate, file);
	}

	@Override
	protected KnownHostEntry prepareKnownHostEntry(ClientSession clientSession, SocketAddress remoteAddress,
			PublicKey serverKey) throws Exception {
		Collection<SshdSocketAddress> patterns = resolveHostNetworkIdentities(clientSession, remoteAddress);
		if (GenericUtils.isEmpty(patterns)) {
			return null;
		}

		StringBuilder sb = new StringBuilder(Byte.MAX_VALUE);
		for (SshdSocketAddress hostIdentity : patterns) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			appendHostPattern(sb, hostIdentity.getHostName(), hostIdentity.getPort());
		}

		PublicKeyEntry.appendPublicKeyEntry(sb.append(' '), serverKey);
		return KnownHostEntry.parseKnownHostEntry(sb.toString());
	}

	// FIXME: Workaround Bug SSHD-1063
	private <A extends Appendable> A appendHostPattern(A sb, String host, int port) throws IOException {
		sb.append(HostPatternsHolder.NON_STANDARD_PORT_PATTERN_ENCLOSURE_START_DELIM);
		sb.append(host);
		sb.append(HostPatternsHolder.NON_STANDARD_PORT_PATTERN_ENCLOSURE_END_DELIM);
		sb.append(HostPatternsHolder.PORT_VALUE_DELIMITER);
		sb.append(Integer.toString(port));
		return sb;
	}
}

package org.javastack.jentunnel.workaround_bug_sshd1033;

import org.apache.sshd.common.forward.DefaultForwarderFactory;
import org.apache.sshd.common.forward.ForwardingFilter;
import org.apache.sshd.common.session.ConnectionService;

/**
 * FIXME: Workaround Bug SSHD-1033
 */
public class CustomForwarderFactory extends DefaultForwarderFactory {
	@Override
	public ForwardingFilter create(ConnectionService service) {
		ForwardingFilter forwarder = new CustomForwardingFilter(service);
		forwarder.addPortForwardingEventListenerManager(this);
		return forwarder;
	}
}

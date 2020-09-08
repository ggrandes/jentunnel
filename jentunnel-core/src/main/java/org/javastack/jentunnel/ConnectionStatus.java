package org.javastack.jentunnel;

public enum ConnectionStatus {
	/**
	 * Initial state and graceful shutdown
	 */
	NOT_CONNECTED,
	/**
	 * Trying to connect
	 */
	CONNECTING,
	/**
	 * Successful connection
	 */
	CONNECTED,
	/**
	 * Connection failed
	 */
	DISCONNECTED,
	//
	;

	public String getLabel() {
		final StringBuilder sb = new StringBuilder(name().length());
		final int len = name().length();
		boolean nextUpper = true;
		for (int i = 0; i < len; i++) {
			final char c = name().charAt(i);
			if (c >= 'A' && c <= 'Z') {
				if (nextUpper) {
					sb.append(c); // Upper
					nextUpper = false;
				} else {
					sb.append((char) (c - 'A' + 'a')); // toLower
				}
			} else if (c == '_') {
				sb.append(' ');
				nextUpper = true;
			}
		}
		return sb.toString();
	}
}

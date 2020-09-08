package org.javastack.jentunnel;

public class Identity implements Comparable<Identity>, AliasID {
	public static final Identity NULL = new Identity();
	public final String id;
	public final String alias;
	public final String username;
	public final String password;
	public final String keyfile;

	private transient String keyFilePassword = null;

	Identity() {
		this(null, null, "", "", "");
	}

	public Identity(final String id, final String alias, final String username, final String password,
			final String keyfile) {
		this.id = (id == null ? UID.generate() : id);
		this.alias = alias;
		this.username = username;
		this.password = fromClearTextPassword(password);
		this.keyfile = keyfile;
	}

	private static final String fromClearTextPassword(final String clearText) {
		return PasswordEncoder.encode(clearText);
	}

	public String getClearTextPassword() {
		return PasswordEncoder.decode(password);
	}

	@Override
	public String toString() {
		return "id=" + id + " alias=" + alias //
				+ " username=" + username;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof Identity) {
			final Identity i = (Identity) obj;
			return (id.equals(i.id));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public int compareTo(final Identity o) {
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
				return Identity.this.getID();
			}

			@Override
			public String getAlias() {
				return Identity.this.getAlias();
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

	public void cacheIdentityPrivateKeyPassword(final String keyFilePassword) {
		if (this != NULL) {
			this.keyFilePassword = keyFilePassword;
		}
	}

	public String cacheIdentityPrivateKeyPassword() {
		if (this != NULL) {
			return this.keyFilePassword;
		}
		return null;
	}
}

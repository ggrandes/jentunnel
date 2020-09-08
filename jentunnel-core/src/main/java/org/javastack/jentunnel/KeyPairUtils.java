package org.javastack.jentunnel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.keys.BuiltinIdentities;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyEncryptionContext;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.io.resource.PathResource;
import org.apache.sshd.common.util.security.SecurityUtils;

public class KeyPairUtils {
	public static KeyPair loadKeyPair(final Path privateKey, final FilePasswordProvider pass)
			throws GeneralSecurityException, IOException {
		try (final InputStream in = Files.newInputStream(privateKey)) {
			final Iterable<KeyPair> keyIter = SecurityUtils.loadKeyPairIdentities(null, //
					new PathResource(privateKey), in, pass);
			if (keyIter == null) {
				return null;
			}
			return keyIter.iterator().next();
		}
	}

	public static KeyPair saveKeyPair(final KeyPair kp, final File directory, final String baseName,
			final String passphrase) throws GeneralSecurityException, IOException {
		if ((baseName == null) || baseName.isEmpty()) {
			throw new NoSuchFileException("Invalid filename (null/empty)");
		}
		final File priFile = new File(directory, baseName);
		final File pubFile = new File(directory, baseName + ".pub");
		final File priOldFile = new File(directory, baseName + ".old");
		final File pubOldFile = new File(directory, baseName + ".pub.old");
		priOldFile.delete();
		pubOldFile.delete();
		priFile.renameTo(priOldFile);
		pubFile.renameTo(pubOldFile);
		try (FileOutputStream os = new FileOutputStream(priFile)) {
			OpenSSHKeyEncryptionContext options = null;
			if ((passphrase != null) && !passphrase.isEmpty()) {
				options = new OpenSSHKeyEncryptionContext();
				options.setPassword(passphrase);
				options.setCipherName("AES");
				options.setCipherMode("CTR");
				options.setCipherType("128");
			}
			OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(kp, null, options, os);
		}
		try (final FileOutputStream os = new FileOutputStream(pubFile)) {
			OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(kp.getPublic(), null, os);
		}
		return kp;
	}

	public static KeyPair generateKeyPair(final KeyType keyType) throws GeneralSecurityException {
		final KeyPair kp;
		switch (keyType) {
			case RSA_2048:
			case RSA_4096:
			case RSA_8192: {
				kp = KeyUtils.generateKeyPair(KeyPairProvider.SSH_RSA, keyType.keySize);
				break;
			}
			case ECDSA_NIST_P256:
			case ECDSA_NIST_P384:
			case ECDSA_NIST_P521: {
				final ECCurves curve = ECCurves.fromCurveSize(keyType.keySize);
				kp = KeyUtils.generateKeyPair(curve.getKeyType(), curve.getKeySize());
				break;
			}
			case ED25519: {
				final KeyPairGenerator g = SecurityUtils.getKeyPairGenerator(SecurityUtils.EDDSA);
				kp = g.generateKeyPair();
				break;
			}
			default:
				throw new InvalidKeyException("Unsupported key type: " + keyType);
		}
		return kp;
	}

	public static enum KeyType {
		/**
		 * RSA, 2048bits
		 */
		RSA_2048(BuiltinIdentities.Constants.RSA, 2048),
		/**
		 * RSA, 4096bits
		 */
		RSA_4096(BuiltinIdentities.Constants.RSA, 4096),
		/**
		 * RSA, 8192bits
		 */
		RSA_8192(BuiltinIdentities.Constants.RSA, 8192),
		/**
		 * EcDSA nist-P256, 256bits
		 */
		ECDSA_NIST_P256(BuiltinIdentities.Constants.ECDSA, ECCurves.nistp256.getKeySize()),
		/**
		 * EcDSA nist-P384, 384bits
		 */
		ECDSA_NIST_P384(BuiltinIdentities.Constants.ECDSA, ECCurves.nistp384.getKeySize()),
		/**
		 * EcDSA nist-P521, 521bits
		 */
		ECDSA_NIST_P521(BuiltinIdentities.Constants.ECDSA, ECCurves.nistp521.getKeySize()),
		/**
		 * EdDSA, 256bits
		 */
		ED25519(BuiltinIdentities.Constants.ED25519, 256),
		//
		;

		public final String keyType;
		public final int keySize;

		KeyType(final String keyType, final int keySize) {
			this.keyType = keyType;
			this.keySize = keySize;
		}
	}

	public static void main(String[] args) throws Throwable {
		final File dir = new File("/tmp/");
		saveKeyPair(generateKeyPair(KeyType.RSA_2048), dir, "id_rsa", null);
		saveKeyPair(generateKeyPair(KeyType.ECDSA_NIST_P256), dir, "id_ecdsa", "changeit");
		saveKeyPair(generateKeyPair(KeyType.ED25519), dir, "id_ed25519", null);
		loadKeyPair(new File(dir, "id_ecdsa").toPath(), FilePasswordProvider.of("changeit"));
	}
}

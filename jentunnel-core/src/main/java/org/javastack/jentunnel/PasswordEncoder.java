package org.javastack.jentunnel;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.NoSuchPaddingException;

import org.apache.commons.codec.binary.Base64;
import org.javastack.packer.Packer;
import org.javastack.packer.Packer.AutoExtendPolicy;
import org.javastack.packer.Packer.InvalidInputDataException;

public class PasswordEncoder {
	private static final String TAG_PLAIN = "$plain$";
	private static final String TAG_BASE64 = "$b64$";
	private static final String TAG_PACKERv0 = "$pack$0$";
	private static final String DEFAULT_STATIC_KEY = "cryXgs2S-mVCQaCm8oyw+q4C2Kc54Cnx";

	public static String encode(final String clearText) {
		if (clearText == null || clearText.isEmpty()) {
			return "";
		}
		// Simple encryption against basic tampering
		try {
			final Packer p = new Packer(64);
			p.setAutoExtendPolicy(AutoExtendPolicy.AUTO);
			p.useAESGCM(DEFAULT_STATIC_KEY);
			p.putString(clearText);
			return TAG_PACKERv0 + p.outputStringBase64URLSafe();
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeySpecException e) {
			throw new RuntimeException(e);
		}
		// Do simple encoding for furtive eyes, not secure at all
		// return TAG_BASE64 + Base64.encodeBase64URLSafeString(clearText.getBytes(StandardCharsets.UTF_8));
	}

	public static String decode(final String password) {
		if (password == null || password.isEmpty()) {
			return "";
		}
		if (password.startsWith(TAG_BASE64)) {
			final String coded = password.substring(TAG_BASE64.length());
			return new String(Base64.decodeBase64(coded), StandardCharsets.UTF_8);
		} else if (password.startsWith(TAG_PACKERv0)) {
			final String coded = password.substring(TAG_PACKERv0.length());
			try {
				final Packer p = new Packer(64);
				p.setAutoExtendPolicy(AutoExtendPolicy.AUTO);
				p.useAESGCM(DEFAULT_STATIC_KEY);
				p.loadStringBase64URLSafe(coded);
				return p.getString();
			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeySpecException
					| InvalidInputDataException e) {
				throw new RuntimeException(e);
			}
		} else if (password.startsWith(TAG_PLAIN)) {
			return password.substring(TAG_PLAIN.length());
		}
		throw new RuntimeException(new UnsupportedEncodingException());
	}
}

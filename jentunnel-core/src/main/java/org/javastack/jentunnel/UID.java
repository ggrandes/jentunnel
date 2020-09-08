package org.javastack.jentunnel;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UID {
	private static final Logger log = LoggerFactory.getLogger(UID.class);
	private static final String HEADER = "i-";
	private static final AtomicLong seq = new AtomicLong();

	private UID() {
	}

	public static String generate() {
		return HEADER + seq.incrementAndGet();
	}

	public static void updateHighWatermark(final String extSeq) {
		if ((extSeq == null) //
				|| (extSeq.length() < (HEADER.length() + 1)) //
				|| (extSeq.length() > (HEADER.length() + 19)) //
				|| (!extSeq.startsWith(HEADER))) {
			// Ignore
			log.warn("Ignore update UID {}", extSeq);
			return;
		}
		try {
			// 9223372036854775807
			final long maxId = Long.parseLong(extSeq.substring(HEADER.length()));
			if (maxId > seq.get()) {
				seq.set(maxId);
			}
		} catch (NumberFormatException e) {
			// Ignore
			log.warn("Ignore update UID {} error={}", extSeq, String.valueOf(e));
		}
	}
}

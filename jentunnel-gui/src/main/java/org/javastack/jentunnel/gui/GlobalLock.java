package org.javastack.jentunnel.gui;

import java.io.File;
import java.io.RandomAccessFile;

class GlobalLock {
	private static RandomAccessFile rafLock;

	static boolean tryGetLockFile() {
		try {
			final File lock = new File(System.getProperty("java.io.tmpdir", "."),
					"." + getBaseFileName() + ".lock");
			if (!lock.exists()) {
				lock.createNewFile();
			}
			rafLock = new RandomAccessFile(lock, "rw");
			return (rafLock.getChannel().tryLock() != null);
		} catch (Exception e) {
			return false;
		}
	}

	private static String getBaseFileName() {
		return Resources.appInfo.iam.toLowerCase() + "." + System.getProperty("user.name", "unknown");
	}
}

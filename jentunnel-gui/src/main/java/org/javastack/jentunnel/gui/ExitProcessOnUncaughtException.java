package org.javastack.jentunnel.gui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ExitProcessOnUncaughtException implements UncaughtExceptionHandler {
	public static void register() {
		Thread.setDefaultUncaughtExceptionHandler(new ExitProcessOnUncaughtException());
	}

	private ExitProcessOnUncaughtException() {
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		try {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			System.out.println("Uncaught exception caught" + " in thread: " + t);
			System.out.flush();
			System.out.println();
			System.err.println(writer.getBuffer().toString());
			System.err.flush();
			printFullCoreDump();
		} finally {
			Runtime.getRuntime().halt(1);
		}
	}

	public static void printFullCoreDump() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		System.out.println("\n" + sdf.format(System.currentTimeMillis()) + "\n" + "All Stack Trace:\n"
				+ getAllStackTraces() + "\nHeap\n" + getHeapInfo() + "\n");
	}

	public static String getAllStackTraces() {
		String ret = "";
		Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();

		for (Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet())
			ret += getThreadInfo(entry.getKey(), entry.getValue()) + "\n";
		return ret;
	}

	public static String getHeapInfo() {
		String ret = "";
		List<MemoryPoolMXBean> memBeans = ManagementFactory.getMemoryPoolMXBeans();
		for (MemoryPoolMXBean mpool : memBeans) {
			MemoryUsage usage = mpool.getUsage();

			String name = mpool.getName();
			long used = usage.getUsed();
			long max = usage.getMax();
			int pctUsed = (int) (used * 100 / max);
			ret += " " + name + " total: " + (max / 1000) + "K, " + pctUsed + "% used\n";
		}
		return ret;
	}

	public static String getThreadInfo(Thread thread, StackTraceElement[] stack) {
		String ret = "";
		ret += "\n\"" + thread.getName() + "\"";
		if (thread.isDaemon())
			ret += " daemon";
		ret += " prio=" + thread.getPriority() + " tid=" + String.format("0x%08x", thread.getId());
		if (stack.length > 0)
			ret += " in " + stack[0].getClassName() + "." + stack[0].getMethodName() + "()";
		ret += "\n   java.lang.Thread.State: " + thread.getState() + "\n";
		ret += getStackTrace(stack);
		return ret;
	}

	public static String getStackTrace(StackTraceElement[] stack) {
		String ret = "";
		for (StackTraceElement element : stack)
			ret += "\tat " + element + "\n";
		return ret;
	}
}
package org.javastack.jentunnel.gui;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class WatchablePrintStream extends PrintStream {
	// private static final String LINE_SEP = System.getProperty("line.separator");
	private Deque<String> internalQueue = new LinkedList<String>();
	private final AtomicReference<QueueEventListener> watcher = new AtomicReference<QueueEventListener>();
	private final PrintStream other;
	private final int maxSize;

	public static WatchablePrintStream getSystemWatchablePrintStream() {
		if (System.out instanceof WatchablePrintStream) {
			return ((WatchablePrintStream) System.out);
		}
		if (System.err instanceof WatchablePrintStream) {
			return ((WatchablePrintStream) System.out);
		}
		return null;
	}

	public WatchablePrintStream(final PrintStream ps, final int maxSize) {
		super(ps);
		this.other = ps;
		this.maxSize = maxSize;
	}

	public void print(final String s) {
		other.print(s);
		queueLog(s);
	}

	public void println(final String s) {
		other.println(s);
		queueLog(s);
	}

	public void println(final Object o) {
		other.println(o);
		queueLog(o);
	}

	private final void queueLog(final Object obj) {
		final String v = String.valueOf(obj);
		synchronized (internalQueue) {
			internalQueue.add(v);
			if (internalQueue.size() > maxSize) {
				internalQueue.pollFirst();
			}
		}
		final QueueEventListener w = watcher.get();
		if (w != null) {
			w.newElement(v);
		}
	}

	public List<String> getQueuedEvents() {
		synchronized (internalQueue) {
			return Collections.unmodifiableList(new ArrayList<String>(internalQueue));
		}
	}

	public WatchablePrintStream watchEvents(final QueueEventListener l) {
		watcher.set(l);
		return this;
	}

	public WatchablePrintStream unwatchEvents() {
		watcher.set(null);
		return this;
	}

	public static interface QueueEventListener extends EventListener {
		/**
		 * Invoked when new element in the queue.
		 * 
		 * @param str with text
		 */
		public void newElement(final String str);
	}
}

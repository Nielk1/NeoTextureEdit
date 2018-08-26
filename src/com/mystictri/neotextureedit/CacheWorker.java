package com.mystictri.neotextureedit;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CacheWorker {

	public boolean stop = false;
	
	private final Object monitor = new Object();
	//private final Lock worklock = new ReentrantLock();
	private final BlockingQueue<CacheWorkerTask> queue = new LinkedBlockingQueue<CacheWorkerTask>();
	
	private Thread work;
	
	public CacheWorker(String name) {
		work = (new Thread(name) {
			public void run() {
				while(!stop) {
			        try {
			        	synchronized(monitor) {
			        		monitor.wait();
			        	}
						
						CacheWorkerTask tsk = null;
						while((tsk = queue.poll()) != null) {
							if(tsk.poison) continue;
							tsk.c.channelChanged(tsk.channel);
							tsk.channel.threadUpdatePending = false;
				        }
					} catch (InterruptedException e) {
						e.printStackTrace();
						stop = true;
						if(ChannelUtils.useCache == UseCache.Thread) {
							ChannelUtils.useCache = UseCache.Yes;
						}
					}
				}
			}
		});
		work.start();
	}
	
	/*public void exit() {
		stop = true;
		monitor.notifyAll();
	}*/ 
	
	public synchronized void AddTask(CacheWorkerTask task) {
		synchronized(monitor) {
			queue.add(task);
			monitor.notifyAll();
		}
	}

	public void purgeQueue() {
		synchronized(monitor) {
			queue.clear();
			monitor.notifyAll();
		}
	}
}
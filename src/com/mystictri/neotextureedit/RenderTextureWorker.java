package com.mystictri.neotextureedit;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import engine.graphics.synthesis.texture.Channel;

public class RenderTextureWorker {

	public boolean stop = false;
	
	private final Object monitor = new Object();
	//private final Lock worklock = new ReentrantLock();
	private final Map<String,BlockingQueue<RenderTextureWorkerTask>> queue = new HashMap<String,BlockingQueue<RenderTextureWorkerTask>>();
	private final Map<String,BlockingQueue<RenderTextureWorkerTask>> shadersReadyToUpdate = new HashMap<String,BlockingQueue<RenderTextureWorkerTask>>(); 
	
	private Thread work;
	
	public RenderTextureWorker(String name) {
		work = (new Thread(name) {
			public void run() {
				while(!stop) {
			        try {
			        	synchronized(monitor) {
			        		monitor.wait();
			        	}
						
			        	List<String> queueSet = new LinkedList<String>();
			        	synchronized(queue) {
			        		Set queueSetX = queue.keySet();
			        		for(Object key : queueSetX) {
			        			queueSet.add((String)key);
			        		}
			        	}
			        	
			        	for(Object key : queueSet) {
				        	RenderTextureWorkerTask tsk = null;
							while((tsk = queue.get(key).poll()) != null) {
								if(tsk.poison) continue;
								BlockingQueue<RenderTextureWorkerTask> updatesQueue = null;
								synchronized(shadersReadyToUpdate) {
									updatesQueue = shadersReadyToUpdate.get(tsk.textureType);
								}
								switch(tsk.textureType) {
									case "Diffuse": tsk.shader.GetBufferedImageDiffuse(tsk.channel); updatesQueue.add(tsk); break;
									case "Normal": tsk.shader.GetBufferedImageNormal(tsk.channel); updatesQueue.add(tsk); break;
									case "SpecWeight": tsk.shader.GetBufferedImageSpecWeight(tsk.channel); updatesQueue.add(tsk); break;
									case "Heightmap": tsk.shader.GetBufferedImageHeightmap(tsk.channel); updatesQueue.add(tsk); break;
									case "Emissive": tsk.shader.GetBufferedImageEmissive(tsk.channel); updatesQueue.add(tsk); break;
								}
					        }
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
	
	public synchronized RenderTextureWorkerTask Get(String textureType) {
		synchronized(shadersReadyToUpdate) {
			return shadersReadyToUpdate.get(textureType).poll();
		}
	}
	
	public synchronized void AddTask(RenderTextureWorkerTask task) {
		synchronized(monitor) {
			addTextureType(task.textureType);
			queue.get(task.textureType).add(task);
			monitor.notifyAll();
		}
	}

	private void addTextureType(String textureType) {
		synchronized(queue) {
			if(!queue.keySet().contains(textureType)) {
				queue.put(textureType, new LinkedBlockingQueue<RenderTextureWorkerTask>());
				shadersReadyToUpdate.put(textureType, new LinkedBlockingQueue<RenderTextureWorkerTask>());
			}
		}
	}

	public void purgeQueue(String textureType) {
		synchronized(monitor) {
			addTextureType(textureType);
			queue.get(textureType).clear();
			monitor.notifyAll();
		}
	}
}
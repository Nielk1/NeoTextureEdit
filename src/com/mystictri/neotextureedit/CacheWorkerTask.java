package com.mystictri.neotextureedit;

import engine.graphics.synthesis.texture.Channel;
import engine.graphics.synthesis.texture.ChannelChangeListener;

public class CacheWorkerTask {
	public Channel channel;
	public ChannelChangeListener c;
	public boolean poison = false;
	
	public CacheWorkerTask(Channel channel, ChannelChangeListener c) {
		this.channel = channel;
		this.c = c;
	}
}

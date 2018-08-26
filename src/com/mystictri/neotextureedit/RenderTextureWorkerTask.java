package com.mystictri.neotextureedit;

import engine.graphics.synthesis.texture.Channel;
import engine.graphics.synthesis.texture.ChannelChangeListener;

public class RenderTextureWorkerTask {
	public AbstractShader shader;
	public String textureType;
	public Channel channel;
	public boolean poison = false;
	
	public RenderTextureWorkerTask(AbstractShader shader, String textureType, Channel channel) {
		this.shader = shader;
		this.textureType = textureType;
		this.channel = channel;
	}
}

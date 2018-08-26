package com.mystictri.neotextureedit;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Vector;

import javax.swing.JPopupMenu;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.AWTGLCanvas;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.Util;
import org.lwjgl.util.glu.GLU;

import engine.base.FMath;
import engine.base.Logger;
import engine.base.Utils;
import engine.base.Vector3;
import engine.graphics.synthesis.texture.Channel;
import engine.parameters.AbstractParam;
import engine.parameters.EnumParam;
import engine.parameters.FloatParam;
import engine.parameters.IntParam;
import engine.parameters.LocalParameterManager;
import engine.parameters.ParamChangeListener;

/**
 * This canvas is used to preview a texture in an OpenGL renderer. It is not implemented for performance but for
 * easy and flexible preview rendering (thus some textures are managed as individual textures even though they
 * may be combined with other textures).
 * @author Holger Dammertz
 *
 */
class OpenGLTextureRenderCanvas extends AWTGLCanvas implements Runnable, MouseListener, MouseWheelListener, MouseMotionListener, KeyListener, ParamChangeListener {
	private static final long serialVersionUID = -1713673512688807546L;
	
	static final boolean USE_THREAD = true; // This is a temp variable to experiment with a thread for rendering vs. selective repaint on mouse clicks (the second
											// method currently has the problem of disappearing render windows; the first has a higher cpu load
	

	boolean initialized = false;
	Thread renderThread;
	
	public boolean running = true;
	public boolean pause = true;
	
	

	
	int GLXres, GLYres;

	long timer = 0;
	long dtCount = 0;
	long frames = 0;
	
	JPopupMenu settingsPopupMenu;
	
	static class GLPreviewParameters extends LocalParameterManager {
		public EnumParam previewObject = CreateLocalEnumParam("Object", "Square,Cube,Cylinder"); // Sphere
		// TODO: Programaticly and dynamically set the enum's members
		public EnumParam shader = CreateLocalEnumParam("Shader", "Basic,Improved");
		
		//public FloatParam specularPower = CreateLocalFloatParam("Spec. Power", 20.0f, 0.f, 200.0f);
		//public FloatParam pomStrength = CreateLocalFloatParam("POM Strength", 0.25f, 0.f, 1.0f).setDefaultIncrement(0.125f);
		//public FloatParam ambient = CreateLocalFloatParam("Ambient", 0.5f, 0.f, 1.0f).setDefaultIncrement(0.125f);
		//public IntParam texScaleU = CreateLocalIntParam("TexScale U", 1, 1, 8);
		//public IntParam texScaleV = CreateLocalIntParam("TexScale V", 1, 1, 8);
		
		// hidden parameters
		public FloatParam rotX = CreateLocalFloatParam("CamRotX", 45.0f, 3.f, 89.0f);
		public FloatParam rotY = CreateLocalFloatParam("CamRotY", 0.0f, -1.f, 360.0f);
		public FloatParam camDist = CreateLocalFloatParam("CamDist", 2.5f, 0.5f, 8.0f);
		
		{
			rotX.hidden = true;
			rotY.hidden = true;
			camDist.hidden = true;
		}
	}
	
	GLPreviewParameters params = new GLPreviewParameters();

	int activeShader = 0;
	AbstractShader[] Shaders = new AbstractShader[] {
		new ShaderBasic(),
		new ShaderImproved()
	};

	

	
	
	

	
	
	

	
	
	
	public OpenGLTextureRenderCanvas(int xres, int yres, JPopupMenu settingsPopupMenu) throws LWJGLException {
		super();

		GLXres = xres;
		GLYres = yres;
		this.settingsPopupMenu = settingsPopupMenu;
		
		addMouseMotionListener(this);
		addMouseListener(this);
		addMouseWheelListener(this);
		addKeyListener(this);
		
		for (AbstractParam p : params.m_LocalParameters) {
			if (p.hidden) continue;
			p.addParamChangeListener(this); 
		}
	}
	
	public void startRenderThread() {
		if (!USE_THREAD) return;
		
		if (renderThread == null) {
			renderThread = new Thread(this);
			renderThread.start();
		} else {
			System.err.println("WARNING: Render Thread is already running");
		}
	}

	public void run() {
		while (renderThread.isAlive()) {
			repaint();
			try {
				Thread.sleep(1000/10);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
	}
	
	void initGLState() {
		GL11.glViewport(0, 0, getWidth(), getHeight());
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		//GL11.glClearColor(62.0f / 100.0f, 77.0f / 100.0f, 100.0f / 100.0f, 1.0f);
		GL11.glClearColor(64.0f / 255.0f, 64.0f / 255.0f, 64.0f / 255.0f, 1.0f);

		//attempting to add transparency
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GLU.gluPerspective(60.0f, (float)GLXres/(float)GLYres, 0.1f, 100.0f);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);

		for(int i = 0; i < Shaders.length; i++)
		{
			Shaders[i].initGLState();
		}
	}
	
	
	boolean requestUpdateDiffuse = false;
	boolean requestUpdateNormal = false;
	boolean requestUpdateSpecWeight = false;
	boolean requestUpdateHeightmap = false;
	boolean requestUpdateEmissive = false;
	Channel _updateDiffuse = null;
	Channel _updateNormal = null;
	Channel _updateSpecWeight = null;
	Channel _updateHeightmap = null;
	Channel _updateEmissive = null;
	
	public synchronized void updateDiffuseMap(Channel c) {
		if (c != null && !c.chechkInputChannels()) {
			Logger.logWarning(this, "Incomplete input channel in diffuse map.");
			_updateDiffuse = null;
		} else {
			_updateDiffuse = c;
		}
		requestUpdateDiffuse = true;
		repaint();
	}
	
	public synchronized void updateNormalMap(Channel c) {
		if (c != null && !c.chechkInputChannels()) {
			Logger.logWarning(this, "Incomplete input channel in normal map.");
			_updateNormal = null;
		} else {
			_updateNormal = c;
		}
		requestUpdateNormal = true;
		repaint();
	}
	
	public synchronized void updateSpecWeightMap(Channel c) {
		if (c != null && !c.chechkInputChannels()) {
			Logger.logWarning(this, "Incomplete input channel in specular map.");
			_updateSpecWeight = null;
		} else {
			_updateSpecWeight = c;
		}
		requestUpdateSpecWeight = true;
		repaint();
	}
	
	public synchronized void updateHeightMap(Channel c) {
		if (c != null && !c.chechkInputChannels()) {
			Logger.logWarning(this, "Incomplete input channel in height map.");
			_updateHeightmap = null;
		} else {
			_updateHeightmap = c;
		}
		requestUpdateHeightmap = true;
		repaint();
	}
	
	public synchronized void updateEmissiveMap(Channel c) {
		if (c != null && !c.chechkInputChannels()) {
			Logger.logWarning(this, "Incomplete input channel in emissive map.");
			_updateEmissive = null;
		} else {
			_updateEmissive = c;
		}
		requestUpdateEmissive = true;
		repaint();
	}
	
	public FloatBuffer m_CamONB = Utils.allocFloatBuffer(9);
	
	final Vector3 UP = new Vector3(0,0,1);
	void updateCamera() {
		final Vector3 u = new Vector3();
		final Vector3 v = new Vector3();
		final Vector3 w = new Vector3();
		final Vector3 eye = new Vector3();
		float t = (params.rotY.get()-90) * ((float) Math.PI / 180.0f);
		float p = (90 - params.rotX.get()) * ((float) Math.PI / 180.0f);

		w.set(FMath.cos(t) * FMath.sin(p), FMath.sin(t) * FMath.sin(p), FMath.cos(p));
		w.normalize();
		u.cross_ip(UP, w);
		u.normalize();
		v.cross_ip(w, u);

		m_CamONB.put(0, u.x);
		m_CamONB.put(1, u.y);
		m_CamONB.put(2, u.z);
		m_CamONB.put(3, v.x);
		m_CamONB.put(4, v.y);
		m_CamONB.put(5, v.z);
		m_CamONB.put(6, w.x);
		m_CamONB.put(7, w.y);
		m_CamONB.put(8, w.z);

		eye.mult_add_ip(params.camDist.get(), w);
		
		for(int i = 0; i < Shaders.length; i++)
		{
			Shaders[i].updateCamera(eye.x, eye.y, eye.z, m_CamONB);
		}
	}
	
	RenderTextureWorker worker = new RenderTextureWorker("OpenGLTextureRenderCanvas-RenderTextureWorker");
	
	synchronized void render() {
		// Process the requests made from another thread:
		
		if (requestUpdateDiffuse) {
			if (worker != null) {
				worker.purgeQueue("Diffuse");
			}
			for(int i = 0; i < Shaders.length; i++)
			{
				if(ChannelUtils.useCache == UseCache.Thread) {
					worker.AddTask(new RenderTextureWorkerTask(Shaders[i], "Diffuse", _updateDiffuse));
				} else {
					Shaders[i].UpdateDiffuse(_updateDiffuse);
				}
			}
			_updateDiffuse = null;
			requestUpdateDiffuse = false;
		}
		if (requestUpdateNormal) {
			if (worker != null) {
				worker.purgeQueue("Normal");
			}
			for(int i = 0; i < Shaders.length; i++)
			{
				if(ChannelUtils.useCache == UseCache.Thread) {
					worker.AddTask(new RenderTextureWorkerTask(Shaders[i], "Normal", _updateNormal));
				} else {
					Shaders[i].UpdateNormal(_updateNormal);
				}
			}
			_updateNormal = null;
			requestUpdateNormal = false;
		}
		if (requestUpdateSpecWeight) {
			if (worker != null) {
				worker.purgeQueue("SpecWeight");
			}
			for(int i = 0; i < Shaders.length; i++)
			{
				if(ChannelUtils.useCache == UseCache.Thread) {
					worker.AddTask(new RenderTextureWorkerTask(Shaders[i], "SpecWeight", _updateSpecWeight));
				} else {
					Shaders[i].UpdateSpecWeight(_updateSpecWeight);
				}
			}
			_updateSpecWeight = null;
			requestUpdateSpecWeight = false;
		}
		if (requestUpdateHeightmap) {
			if (worker != null) {
				worker.purgeQueue("Heightmap");
			}
			for(int i = 0; i < Shaders.length; i++)
			{
				if(ChannelUtils.useCache == UseCache.Thread) {
					worker.AddTask(new RenderTextureWorkerTask(Shaders[i], "Heightmap", _updateHeightmap));
				} else {
					Shaders[i].UpdateHeightmap(_updateHeightmap);
				}
			}
			_updateHeightmap = null;
			requestUpdateHeightmap = false;
		}
		if (requestUpdateEmissive) {
			if (worker != null) {
				worker.purgeQueue("Emissive");
			}
			for(int i = 0; i < Shaders.length; i++)
			{
				if(ChannelUtils.useCache == UseCache.Thread) {
					worker.AddTask(new RenderTextureWorkerTask(Shaders[i], "Emissive", _updateEmissive));
				} else {
					Shaders[i].UpdateEmissive(_updateEmissive);
				}
			}
			_updateEmissive = null;
			requestUpdateEmissive = false;
		}
		
		
		
		{
			RenderTextureWorkerTask tsk = null;
			while((tsk = worker.Get("Diffuse")) != null) {
				tsk.shader.UpdateDiffuse(tsk.channel);
			}
		}
		{
			RenderTextureWorkerTask tsk = null;
			while((tsk = worker.Get("Normal")) != null)
				tsk.shader.UpdateNormal(tsk.channel);
		}
		{
			RenderTextureWorkerTask tsk = null;
			while((tsk = worker.Get("SpecWeight")) != null)
				tsk.shader.UpdateSpecWeight(tsk.channel);
		}
		{
			RenderTextureWorkerTask tsk = null;
			while((tsk = worker.Get("Heightmap")) != null)
				tsk.shader.UpdateHeightmap(tsk.channel);
		}
		{
			RenderTextureWorkerTask tsk = null;
			while((tsk = worker.Get("Emissive")) != null)
				tsk.shader.UpdateEmissive(tsk.channel);
		}
		
		
		
		
		Shaders[activeShader].render1();
		updateCamera();
		Shaders[activeShader].render2();
		
		float[] vData;
		int[] idx;
		if (params.previewObject.getEnumPos() == 0) {
			vData = PlaneVertexData;
			idx = PlaneIndices;
		} else if (params.previewObject.getEnumPos() == 1) {
			vData = CubeVertexData;
			idx = CubeIndices;
		} else  {
			vData = CylinderVertexData;
			idx = CylinderIndices;
		}
		
		
		GL11.glBegin(GL11.GL_TRIANGLES);
		for (int i = 0; i < idx.length; i++) {
			int j = idx[i]*11;
			GL11.glNormal3f(vData[j+3],vData[j+4],vData[j+5]); 
			GL11.glTexCoord2f(vData[j+9],vData[j+10]); 
			GL20.glVertexAttrib3f(1, vData[j+6],vData[j+7],vData[j+8]);
			GL11.glVertex3f(vData[j+0],vData[j+1],vData[j+2]); 
		}
		GL11.glEnd();
		
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
	}
	
	
	
	
	protected void paintGL() {
		try {
			if (!initialized) {
				if (getContext() != null) {
					initGLState();
					timer = System.currentTimeMillis();
					initialized = true;
				} 
			} 
			
			if (initialized) {
				
				render();
				swapBuffers();
			}
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
	}

	
	int mouseOX = 0;
	int mouseOY = 0;
	int mouseButton;

	public void mouseDragged(MouseEvent e) {
		int dx = e.getX() - mouseOX;
		int dy = e.getY() - mouseOY;

		mouseOX = e.getX();
		mouseOY = e.getY();

    	if ((mouseButton == MouseEvent.BUTTON1 && e.isShiftDown())
          || mouseButton == MouseEvent.BUTTON3 ) {
			params.camDist.increment(dy/10.0f);
		} else if (mouseButton == MouseEvent.BUTTON1) {
			params.rotX.increment(dy);
			params.rotY.increment(-dx);
			
			if (params.rotY.get() >= 360) params.rotY.increment(-360);
			if (params.rotY.get() < 0) params.rotY.increment(360);
		} else if (mouseButton == MouseEvent.BUTTON2 ) {

		}
		
		repaint();
	}

	public void mouseMoved(MouseEvent e) {
	}

	public void keyPressed(KeyEvent e) {
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
		System.out.println(e);
	}

	public void mouseEntered(MouseEvent e) {
		requestFocus();
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		mouseOX = e.getX();
		mouseOY = e.getY();
		mouseButton = e.getButton();
		
		if (e.isPopupTrigger()) {
			settingsPopupMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	public void mouseReleased(MouseEvent e) {
	}
	
	public void mouseWheelMoved(MouseWheelEvent e) {
		float dr = (float)e.getPreciseWheelRotation();

		params.camDist.increment(dr/10.0f);

		repaint();
	}
	

	
	
	
	
	
	

	


	
	public void parameterChanged(AbstractParam source) {
		if(source.getName() == "Shader")
		{
			activeShader = ((EnumParam)source).getEnumPos();
		}
		repaint();
	}


	static final float CubeVertexData[] = { -1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 1.0f, -0.0f, 0.0f, 1.0f, 0.0f, -1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, -0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, -0.0f, 0.0f, -0.0f, 1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 1.0f, -0.0f, 0.0f, 0.0f, -0.0f, -1.0f, 1.0f, 1.0f, -1.0f, 0.0f, -0.0f, 0.0f, 1.0f, 0.0f, -0.0f, 1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 0.0f, -0.0f, 0.0f, 1.0f, 0.0f, 0.0f, -0.0f, -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, -0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, -1.0f, -1.0f, 1.0f, -1.0f, 0.0f, -0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -0.0f, -1.0f, -0.0f, -1.0f, 0.0f, -0.0f, -0.0f, 1.0f, -1.0f, -1.0f, -1.0f, -0.0f, -1.0f, -0.0f, -1.0f, 0.0f, 0.0f, 0.0f, -0.0f, 1.0f, -1.0f, -1.0f, -0.0f, -1.0f, -0.0f, -1.0f, 0.0f, -0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f, -0.0f, -1.0f, -0.0f, -1.0f, 0.0f, -0.0f, 1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -0.0f, 0.0f, -0.0f, -1.0f, 0.0f, -0.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -0.0f, 1.0f, 1.0f, -1.0f, 1.0f, -0.0f, 0.0f, -0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, -0.0f, 0.0f, -0.0f, -1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, -0.0f, 1.0f, 1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.0f, -0.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 1.0f, 0.0f, -1.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, -0.0f, 1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, -0.0f, -1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, };
	static final int CubeIndices[] = { 0 , 1 , 2 , 0 , 2 , 3 , 4 , 5 , 6 , 4 , 6 , 7 , 8 , 9 , 10 , 8 , 10 , 11 , 12 , 13 , 14 , 12 , 14 , 15 , 16 , 17 , 18 , 16 , 18 , 19 , 20 , 21 , 22 , 20 , 22 , 23 , };


	static final float PlaneVertexData[] = { 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -0.0f, 0.0f, 1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -0.0f, 0.0f, 1.0f, 1.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, -0.0f, 0.0f, 0.0f, 1.0f, };
	static final int PlaneIndices[] = { 0, 1, 2, 0, 2, 3, };

	static final float CylinderVertexData[] = { 0.55557f, 0.83147f, -1.0f, 0.55556f, 0.83145f, 0.0f, 0.81279f, -0.58255f, 0.0f, 1.28125f, 0.0f, 0.55556f, 0.83147f, 1.0f, 0.55556f, 0.83145f, 0.0f, 0.84925f, -0.52799f, 0.0f, 1.28125f, 1.0f, 0.70711f, 0.70711f, 1.0f, 0.70708f, 0.70708f, 0.0f, 0.72993f, -0.68352f, 0.0f, 1.21875f, 1.0f, 0.70711f, 0.70711f, -1.0f, 0.70708f, 0.70708f, 0.0f, 0.68352f, -0.72993f, 0.0f, 1.21875f, 0.0f, 0.38269f, 0.92388f, -1.0f, 0.38267f, 0.92386f, 0.0f, 0.91083f, -0.41279f, 0.0f, 1.34375f, 0.0f, 0.38268f, 0.92388f, 1.0f, 0.38267f, 0.92386f, 0.0f, 0.93594f, -0.35216f, 0.0f, 1.34375f, 1.0f, 0.19509f, 0.98078f, -1.0f, 0.19507f, 0.98077f, 0.0f, 0.97386f, -0.22717f, 0.0f, 1.40625f, 0.0f, 0.19508f, 0.98079f, 1.0f, 0.19507f, 0.98077f, 0.0f, 0.98666f, -0.1628f, 0.0f, 1.40625f, 1.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.99946f, -0.03281f, 0.0f, 1.46875f, 0.0f, -1e-05f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.99946f, 0.03282f, 0.0f, 1.46875f, 1.0f, -0.19509f, 0.98079f, -1.0f, -0.19507f, 0.98077f, 0.0f, 0.99518f, 0.09802f, 0.0f, 1.53125f, 0.0f, -0.1951f, 0.98078f, 1.0f, -0.19507f, 0.98077f, 0.0f, 0.99518f, 0.09802f, 0.0f, 1.53125f, 1.0f, -0.38268f, 0.92388f, -1.0f, -0.38267f, 0.92386f, 0.0f, 0.93594f, 0.35216f, 0.0f, -0.40625f, 0.0f, -0.38269f, 0.92388f, 1.0f, -0.38267f, 0.92386f, 0.0f, 0.91082f, 0.4128f, 0.0f, -0.40625f, 1.0f, -0.1951f, 0.98078f, 1.0f, -0.19507f, 0.98077f, 0.0f, 0.95694f, 0.29029f, 0.0f, -0.46875f, 1.0f, -0.19509f, 0.98079f, -1.0f, -0.19507f, 0.98077f, 0.0f, 0.95694f, 0.29028f, 0.0f, -0.46875f, 0.0f, -0.55557f, 0.83147f, -1.0f, -0.55556f, 0.83145f, 0.0f, 0.84925f, 0.52799f, 0.0f, -0.34375f, 0.0f, -0.55557f, 0.83147f, 1.0f, -0.55556f, 0.83145f, 0.0f, 0.81279f, 0.58256f, 0.0f, -0.34375f, 1.0f, -0.70711f, 0.70711f, -1.0f, -0.70708f, 0.70708f, 0.0f, 0.72993f, 0.68352f, 0.0f, -0.28125f, 0.0f, -0.70711f, 0.7071f, 1.0f, -0.70708f, 0.70708f, 0.0f, 0.68352f, 0.72993f, 0.0f, -0.28125f, 1.0f, -0.83147f, 0.55557f, -1.0f, -0.83145f, 0.55556f, 0.0f, 0.58255f, 0.81279f, 0.0f, -0.21875f, 0.0f, -0.83147f, 0.55557f, 1.0f, -0.83145f, 0.55556f, 0.0f, 0.52799f, 0.84925f, 0.0f, -0.21875f, 1.0f, -0.92388f, 0.38268f, -1.0f, -0.92386f, 0.38267f, 0.0f, 0.41279f, 0.91083f, 0.0f, -0.15625f, 0.0f, -0.92388f, 0.38268f, 1.0f, -0.92386f, 0.38267f, 0.0f, 0.35216f, 0.93594f, 0.0f, -0.15625f, 1.0f, -0.98079f, 0.19509f, -1.0f, -0.98077f, 0.19507f, 0.0f, 0.22717f, 0.97386f, -0.0f, -0.09375f, 0.0f, -0.98079f, 0.19509f, 1.0f, -0.98077f, 0.19507f, 0.0f, 0.1628f, 0.98666f, -0.0f, -0.09375f, 1.0f, -1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.03281f, 0.99946f, -0.0f, -0.03125f, 0.0f, -1.0f, -0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -0.03281f, 0.99946f, 0.0f, -0.03125f, 1.0f, -0.98079f, -0.19509f, -1.0f, -0.98077f, -0.19507f, 0.0f, -0.1628f, 0.98666f, 0.0f, 0.03125f, 0.0f, -0.98078f, -0.19509f, 1.0f, -0.98077f, -0.19507f, 0.0f, -0.22717f, 0.97386f, 0.0f, 0.03125f, 1.0f, -0.92388f, -0.38268f, -1.0f, -0.92386f, -0.38267f, 0.0f, -0.35216f, 0.93594f, -0.0f, 0.09375f, 0.0f, -0.92388f, -0.38269f, 1.0f, -0.92386f, -0.38267f, 0.0f, -0.41279f, 0.91082f, -0.0f, 0.09375f, 1.0f, -0.83147f, -0.55557f, -1.0f, -0.83145f, -0.55556f, 0.0f, -0.52799f, 0.84925f, -0.0f, 0.15625f, 0.0f, -0.83147f, -0.55557f, 1.0f, -0.83145f, -0.55556f, 0.0f, -0.58256f, 0.81279f, 0.0f, 0.15625f, 1.0f, -0.70711f, -0.70711f, -1.0f, -0.70708f, -0.70708f, 0.0f, -0.68352f, 0.72993f, 0.0f, 0.21875f, 0.0f, -0.70711f, -0.70711f, 1.0f, -0.70708f, -0.70708f, 0.0f, -0.72993f, 0.68352f, 0.0f, 0.21875f, 1.0f, -0.55557f, -0.83147f, -1.0f, -0.55556f, -0.83145f, 0.0f, -0.81279f, 0.58255f, -0.0f, 0.28125f, 0.0f, -0.55557f, -0.83147f, 1.0f, -0.55556f, -0.83145f, 0.0f, -0.84925f, 0.52799f, -0.0f, 0.28125f, 1.0f, -0.38268f, -0.92388f, -1.0f, -0.38267f, -0.92386f, 0.0f, -0.91083f, 0.41279f, -0.0f, 0.34375f, 0.0f, -0.38268f, -0.92388f, 1.0f, -0.38267f, -0.92386f, 0.0f, -0.93594f, 0.35216f, -0.0f, 0.34375f, 1.0f, -0.19509f, -0.98079f, -1.0f, -0.19507f, -0.98077f, 0.0f, -0.97386f, 0.22717f, 0.0f, 0.40625f, 0.0f, -0.19509f, -0.98079f, 1.0f, -0.19507f, -0.98077f, 0.0f, -0.98666f, 0.1628f, 0.0f, 0.40625f, 1.0f, -0.0f, -1.0f, -1.0f, 0.0f, -1.0f, 0.0f, -0.99946f, 0.03281f, 0.0f, 0.46875f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, -1.0f, 0.0f, -0.99946f, -0.03281f, -0.0f, 0.46875f, 1.0f, 0.19509f, -0.98079f, -1.0f, 0.19507f, -0.98077f, 0.0f, -0.98666f, -0.1628f, -0.0f, 0.53125f, 0.0f, 0.19509f, -0.98079f, 1.0f, 0.19507f, -0.98077f, 0.0f, -0.97386f, -0.22717f, -0.0f, 0.53125f, 1.0f, 0.38268f, -0.92388f, -1.0f, 0.38267f, -0.92386f, 0.0f, -0.93594f, -0.35216f, 0.0f, 0.59375f, 0.0f, 0.38268f, -0.92388f, 1.0f, 0.38267f, -0.92386f, 0.0f, -0.91083f, -0.41279f, 0.0f, 0.59375f, 1.0f, 0.55557f, -0.83147f, -1.0f, 0.55556f, -0.83145f, 0.0f, -0.84925f, -0.52799f, 0.0f, 0.65625f, 0.0f, 0.55557f, -0.83147f, 1.0f, 0.55556f, -0.83145f, 0.0f, -0.81279f, -0.58255f, 0.0f, 0.65625f, 1.0f, 0.70711f, -0.70711f, -1.0f, 0.70708f, -0.70708f, 0.0f, -0.72993f, -0.68352f, 0.0f, 0.71875f, 0.0f, 0.70711f, -0.70711f, 1.0f, 0.70708f, -0.70708f, 0.0f, -0.68352f, -0.72993f, 0.0f, 0.71875f, 1.0f, 0.83147f, -0.55557f, -1.0f, 0.83145f, -0.55556f, 0.0f, -0.58255f, -0.81279f, 0.0f, 0.78125f, 0.0f, 0.83147f, -0.55557f, 1.0f, 0.83145f, -0.55556f, 0.0f, -0.52799f, -0.84925f, 0.0f, 0.78125f, 1.0f, 0.92388f, -0.38268f, -1.0f, 0.92386f, -0.38267f, 0.0f, -0.41279f, -0.91082f, 0.0f, 0.84375f, 0.0f, 0.92388f, -0.38269f, 1.0f, 0.92386f, -0.38267f, 0.0f, -0.35216f, -0.93594f, 0.0f, 0.84375f, 1.0f, 0.98079f, -0.19509f, -1.0f, 0.98077f, -0.19507f, 0.0f, -0.22717f, -0.97386f, 0.0f, 0.90625f, 0.0f, 0.98078f, -0.19509f, 1.0f, 0.98077f, -0.19507f, 0.0f, -0.1628f, -0.98666f, 0.0f, 0.90625f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, -0.03281f, -0.99946f, 0.0f, 0.96875f, 0.0f, 1.0f, -0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.03281f, -0.99946f, 0.0f, 0.96875f, 1.0f, 0.98079f, 0.19509f, -1.0f, 0.98077f, 0.19507f, 0.0f, 0.1628f, -0.98666f, 0.0f, 1.03125f, 0.0f, 0.98079f, 0.19509f, 1.0f, 0.98077f, 0.19507f, 0.0f, 0.22717f, -0.97386f, 0.0f, 1.03125f, 1.0f, 0.92388f, 0.38268f, -1.0f, 0.92386f, 0.38267f, 0.0f, 0.35216f, -0.93594f, 0.0f, 1.09375f, 0.0f, 0.92388f, 0.38268f, 1.0f, 0.92386f, 0.38267f, 0.0f, 0.41279f, -0.91083f, 0.0f, 1.09375f, 1.0f, 0.83147f, 0.55557f, -1.0f, 0.83145f, 0.55556f, 0.0f, 0.52799f, -0.84925f, 0.0f, 1.15625f, 0.0f, 0.83147f, 0.55557f, 1.0f, 0.83145f, 0.55556f, 0.0f, 0.58255f, -0.81279f, 0.0f, 1.15625f, 1.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.70711f, 0.70711f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.85355f, 0.85355f, 0.83147f, 0.55557f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.91573f, 0.77779f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.5f, 0.5f, 0.83147f, 0.55557f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.91574f, 0.77778f, 0.70711f, 0.70711f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.85355f, 0.85355f, 0.92388f, 0.38268f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.96194f, 0.69134f, 0.92388f, 0.38268f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.96194f, 0.69134f, 0.98079f, 0.19509f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.99039f, 0.59755f, 0.98079f, 0.19509f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.99039f, 0.59754f, 1.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.5f, 1.0f, -0.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 1.0f, 0.5f, 0.98079f, -0.19509f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.99039f, 0.40245f, 0.98078f, -0.19509f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.99039f, 0.40245f, 0.92388f, -0.38268f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.96194f, 0.30866f, 0.92388f, -0.38269f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.96194f, 0.30866f, 0.83147f, -0.55557f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.91573f, 0.22221f, 0.83147f, -0.55557f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.91573f, 0.22221f, 0.70711f, -0.70711f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.85355f, 0.14645f, 0.70711f, -0.70711f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.85355f, 0.14645f, 0.55557f, -0.83147f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.77779f, 0.08427f, 0.55557f, -0.83147f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.77778f, 0.08427f, 0.38268f, -0.92388f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.69134f, 0.03806f, 0.38268f, -0.92388f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.69134f, 0.03806f, 0.19509f, -0.98079f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.59755f, 0.00961f, 0.19509f, -0.98079f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.59755f, 0.00961f, -0.0f, -1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.5f, 0.0f, -0.19509f, -0.98079f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.40245f, 0.00961f, -0.19509f, -0.98079f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.40246f, 0.00961f, -0.38268f, -0.92388f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.30866f, 0.03806f, -0.38268f, -0.92388f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.30866f, 0.03806f, -0.55557f, -0.83147f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.22221f, 0.08427f, -0.55557f, -0.83147f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.22222f, 0.08426f, -0.70711f, -0.70711f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.14645f, 0.14645f, -0.70711f, -0.70711f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.14645f, 0.14645f, -0.83147f, -0.55557f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.08427f, 0.22222f, -0.83147f, -0.55557f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.08427f, 0.22221f, -0.92388f, -0.38268f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.03806f, 0.30866f, -0.92388f, -0.38269f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.03806f, 0.30866f, -0.98079f, -0.19509f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.00961f, 0.40246f, -0.98078f, -0.19509f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.00961f, 0.40245f, -1.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.0f, 0.5f, -1.0f, -0.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.0f, 0.5f, -0.98079f, 0.19509f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.00961f, 0.59755f, -0.98079f, 0.19509f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.00961f, 0.59754f, -0.92388f, 0.38268f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.03806f, 0.69134f, -0.92388f, 0.38268f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.03806f, 0.69134f, -0.83147f, 0.55557f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.08427f, 0.77779f, -0.83147f, 0.55557f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.08426f, 0.77778f, -0.70711f, 0.70711f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.14645f, 0.85355f, -0.70711f, 0.7071f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.14644f, 0.85355f, -0.55557f, 0.83147f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.22222f, 0.91574f, -0.55557f, 0.83147f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.22221f, 0.91573f, -0.38268f, 0.92388f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.30866f, 0.96194f, -0.38269f, 0.92388f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.30866f, 0.96194f, -0.19509f, 0.98079f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.40246f, 0.99039f, -0.1951f, 0.98078f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.40245f, 0.99039f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f, -1e-05f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.5f, 1.0f, 0.19509f, 0.98078f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.59755f, 0.99039f, 0.19508f, 0.98079f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.59754f, 0.99039f, 0.38269f, 0.92388f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.69134f, 0.96194f, 0.38268f, 0.92388f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 0.0f, 0.0f, 0.69134f, 0.96194f, 0.55557f, 0.83147f, -1.0f, 0.0f, 0.0f, -1.0f, 1.0f, -0.0f, 0.0f, 0.77779f, 0.91573f, 0.55556f, 0.83147f, 1.0f, 0.0f, 0.0f, 1.0f, -1.0f, -0.0f, 0.0f, 0.77778f, 0.91574f, };
	static final int CylinderIndices[] = { 0 , 1 , 2 , 0 , 2 , 3 , 1 , 0 , 4 , 1 , 4 , 5 , 6 , 7 , 5 , 6 , 5 , 4 , 8 , 9 , 7 , 8 , 7 , 6 , 10 , 11 , 9 , 10 , 9 , 8 , 12 , 13 , 14 , 12 , 14 , 15 , 16 , 17 , 13 , 16 , 13 , 12 , 18 , 19 , 17 , 18 , 17 , 16 , 20 , 21 , 19 , 20 , 19 , 18 , 22 , 23 , 21 , 22 , 21 , 20 , 24 , 25 , 23 , 24 , 23 , 22 , 26 , 27 , 25 , 26 , 25 , 24 , 28 , 29 , 27 , 28 , 27 , 26 , 30 , 31 , 29 , 30 , 29 , 28 , 32 , 33 , 31 , 32 , 31 , 30 , 34 , 35 , 33 , 34 , 33 , 32 , 36 , 37 , 35 , 36 , 35 , 34 , 38 , 39 , 37 , 38 , 37 , 36 , 40 , 41 , 39 , 40 , 39 , 38 , 42 , 43 , 41 , 42 , 41 , 40 , 44 , 45 , 43 , 44 , 43 , 42 , 46 , 47 , 45 , 46 , 45 , 44 , 48 , 49 , 47 , 48 , 47 , 46 , 50 , 51 , 49 , 50 , 49 , 48 , 52 , 53 , 51 , 52 , 51 , 50 , 54 , 55 , 53 , 54 , 53 , 52 , 56 , 57 , 55 , 56 , 55 , 54 , 58 , 59 , 57 , 58 , 57 , 56 , 60 , 61 , 59 , 60 , 59 , 58 , 62 , 63 , 61 , 62 , 61 , 60 , 64 , 65 , 63 , 64 , 63 , 62 , 3 , 2 , 65 , 3 , 65 , 64 , 66 , 67 , 68 , 69 , 70 , 71 , 66 , 68 , 72 , 69 , 73 , 70 , 66 , 72 , 74 , 69 , 75 , 73 , 66 , 74 , 76 , 69 , 77 , 75 , 66 , 76 , 78 , 69 , 79 , 77 , 66 , 78 , 80 , 69 , 81 , 79 , 66 , 80 , 82 , 69 , 83 , 81 , 66 , 82 , 84 , 69 , 85 , 83 , 66 , 84 , 86 , 69 , 87 , 85 , 66 , 86 , 88 , 69 , 89 , 87 , 66 , 88 , 90 , 69 , 91 , 89 , 66 , 90 , 92 , 69 , 93 , 91 , 66 , 92 , 94 , 69 , 95 , 93 , 66 , 94 , 96 , 69 , 97 , 95 , 66 , 96 , 98 , 69 , 99 , 97 , 66 , 98 , 100 , 69 , 101 , 99 , 66 , 100 , 102 , 69 , 103 , 101 , 66 , 102 , 104 , 69 , 105 , 103 , 66 , 104 , 106 , 69 , 107 , 105 , 66 , 106 , 108 , 69 , 109 , 107 , 66 , 108 , 110 , 69 , 111 , 109 , 66 , 110 , 112 , 69 , 113 , 111 , 66 , 112 , 114 , 69 , 115 , 113 , 66 , 114 , 116 , 69 , 117 , 115 , 66 , 116 , 118 , 69 , 119 , 117 , 66 , 118 , 120 , 69 , 121 , 119 , 66 , 120 , 122 , 69 , 123 , 121 , 66 , 122 , 124 , 69 , 125 , 123 , 66 , 124 , 126 , 69 , 127 , 125 , 66 , 126 , 128 , 69 , 129 , 127 , 66 , 128 , 130 , 69 , 131 , 129 , 130 , 67 , 66 , 69 , 71 , 131 , };














	public LocalParameterManager GetShaderParamaters() {
		return Shaders[activeShader].GetParamaters();
	}
}


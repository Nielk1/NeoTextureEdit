package com.mystictri.neotextureedit;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.Util;
import org.lwjgl.util.glu.GLU;

import engine.base.Logger;
import engine.base.Utils;
import engine.graphics.synthesis.texture.Channel;
import engine.parameters.LocalParameterManager;

abstract class AbstractShader {
	final int TEXTURE_RESX = 256;
	final int TEXTURE_RESY = 256;
	
	int getUniformLocation(String loc_name, int programID) {
		byte[] bs = loc_name.getBytes();
		ByteBuffer b = Utils.allocByteBuffer(bs.length+1);
		b.put(bs); b.put((byte)0);
		b.flip();
		int ret = ARBShaderObjects.glGetUniformLocationARB(programID, b);
		if (ret == -1) {
			Logger.logWarning(this, "    WARNING: Shader: "+programID+": could not get uniform location " + loc_name);
		}
		Util.checkGLError();
		return ret;
	}
	
	abstract void loadShaderLocations();
	
	protected abstract int GetProgramID();
	
	public void bindTangentAttribute(String name) {
		ByteBuffer nameb = Utils.allocByteBuffer(name.length()+1);
		nameb.put(name.getBytes()); nameb.put((byte)0);
		nameb.flip();
		GL20.glBindAttribLocation(GetProgramID(), 1, nameb); 
	}

	public abstract void UpdateDiffuse(Channel _updateDiffuse);
	public abstract void UpdateNormal(Channel _updateNormal);
	public abstract void UpdateSpecWeight(Channel _updateSpecWeight);
	public abstract void UpdateHeightmap(Channel _updateHeightmap);
	public abstract void UpdateEmissive(Channel _updateEmissive);

	public abstract BufferedImage GetBufferedImageDiffuse(Channel _updateDiffuse);
	public abstract BufferedImage GetBufferedImageNormal(Channel _updateNormal);
	public abstract BufferedImage GetBufferedImageSpecWeight(Channel _updateSpecWeight);
	public abstract BufferedImage GetBufferedImageHeightmap(Channel _updateHeightmap);
	public abstract BufferedImage GetBufferedImageEmissive(Channel _updateEmissive);
	
	public abstract void render1();
	public abstract void render2();

	public abstract void updateCamera(float eye_x, float eye_y, float eye_z, FloatBuffer m_CamONB);
	
	protected abstract void initGLState();
	
	public int getShaderProgram(String vcode, String fcode) {
		ByteBuffer vc = ByteBuffer.allocateDirect(vcode.length());
		vc.put(vcode.getBytes()); vc.flip();
		ByteBuffer fc = ByteBuffer.allocateDirect(fcode.length());
		fc.put(fcode.getBytes()); fc.flip();
		return getShaderProgram(vc, fc);
	}
	
	/**
	 * Loads a vertex and fragment shader pair, compiles them and links them to a ProgramObjectARB
	 * @param name
	 * @return the program object ID
	 */
	public int getShaderProgram(ByteBuffer vcode, ByteBuffer fcode) {
		int progID = -1;

		int vid = ARBShaderObjects.glCreateShaderObjectARB(ARBVertexShader.GL_VERTEX_SHADER_ARB);
		int fid = ARBShaderObjects.glCreateShaderObjectARB(ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);

		ARBShaderObjects.glShaderSourceARB(vid, vcode);
		ARBShaderObjects.glCompileShaderARB(vid);
		printLogInfo(vid, "Compile Vertex Shader");

		ARBShaderObjects.glShaderSourceARB(fid, fcode);
		ARBShaderObjects.glCompileShaderARB(fid);
		printLogInfo(fid, "Compile Fragment Shader");
		
		progID = ARBShaderObjects.glCreateProgramObjectARB();
		ARBShaderObjects.glAttachObjectARB(progID, vid);
		ARBShaderObjects.glAttachObjectARB(progID, fid);
		
		ARBShaderObjects.glLinkProgramARB(progID);
		printLogInfo(progID, "Link");
		ARBShaderObjects.glValidateProgramARB(progID);
		printLogInfo(progID, "Validate");
		
		return progID;
	}
	
	
	
	
	
	
	ByteBuffer bbuf = Utils.allocByteBuffer(TEXTURE_RESX * TEXTURE_RESY * 4);

	//!!TODO: it is inefficient to do the texture creation over a temporary BufferedImage as a procedural texture channel alrady operates
	//        on a float buffer that could be directly send to OpenGL for conversion (or at least converted directly in the convertImageData
	//        function below.
	public int create2dTexture(int color) {
		int id = genTexID();
		
		IntBuffer ibuf = bbuf.asIntBuffer();
		for (int i = 0; i < ibuf.capacity(); i++) ibuf.put(i, color);

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		int targetFormat = GL11.GL_RGBA;
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, targetFormat, TEXTURE_RESX, TEXTURE_RESY, 0, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, bbuf.asIntBuffer());
		GLU.gluBuild2DMipmaps(GL11.GL_TEXTURE_2D, targetFormat, TEXTURE_RESX, TEXTURE_RESY, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, bbuf);

		return id;
	}
	
	public synchronized void update2dTexture_ConstanctColor(int color, int id) {
		IntBuffer ibuf = bbuf.asIntBuffer();
		for (int i = 0; i < ibuf.capacity(); i++) ibuf.put(i, color);

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		int targetFormat = GL11.GL_RGBA;
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, targetFormat, TEXTURE_RESX, TEXTURE_RESY, 0, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, bbuf.asIntBuffer());
		GLU.gluBuild2DMipmaps(GL11.GL_TEXTURE_2D, targetFormat, TEXTURE_RESX, TEXTURE_RESY, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, bbuf);
	}

	
	// here are the same performance problems as in create2dTexture
	public synchronized void update2dTexture(BufferedImage img, int id) {
		if (img.getWidth() != TEXTURE_RESX || img.getHeight() != TEXTURE_RESY) {
			Logger.logError(this, "TextureResolution does not match image resolution for update.");
			return;
		}
		convertImageData(bbuf.asIntBuffer(), img);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		//int targetFormat = (img.getColorModel().hasAlpha()) ? GL11.GL_RGBA : GL11.GL_RGB;
		int targetFormat = GL11.GL_RGBA;
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, targetFormat, TEXTURE_RESX, TEXTURE_RESY, 0, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, bbuf.asIntBuffer());
		GLU.gluBuild2DMipmaps(GL11.GL_TEXTURE_2D, targetFormat, TEXTURE_RESX, TEXTURE_RESY, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, bbuf);
	}
	
	
	
	
	// !!TODO: this is a slow conversion operation because each byte is touched by hand...
	public void convertImageData(IntBuffer result, BufferedImage img) {
		int xres = img.getWidth();
		int yres = img.getHeight();
		int[] data = new int[xres * yres];
		img.getRGB(0, 0, xres, yres, data, 0, xres);

		for (int i = 0; i < xres * yres; i++) {
			int A = ((data[i] >> 24) & 0xFF);
			int R = ((data[i] >> 16) & 0xFF);
			int G = ((data[i] >> 8) & 0xFF);
			int B = ((data[i] >> 0) & 0xFF);
			data[i] = (R << 24) | (G << 16) | (B << 8) | (A << 0);
		}

		result.put(data);
		result.rewind();
	}
	
	
	ByteBuffer loadShaderCodeFromFile(String filename) {
		byte[] code = null;

		try {
			InputStream fs = OpenGLPreviewPanel.class.getResourceAsStream(filename);
			if (fs == null) {
				System.err.println("Error opening file "+filename);
				return null;
			}
			fs.read(code = new byte[fs.available()]);
			fs.close();
		} catch (IOException e) {
			System.out.println(e);
			return null;
		}

		ByteBuffer shaderCode = Utils.allocByteBuffer(code.length);
		
		shaderCode.put(code);
		shaderCode.flip();

		return shaderCode;
	}
	
	public int genTexID() {
		IntBuffer i = Utils.allocIntBuffer(1);
		GL11.glGenTextures(i);
		return i.get(0);
	}
	
	private void printLogInfo(int obj, String name) {
		IntBuffer iVal = Utils.allocIntBuffer(1);
		ARBShaderObjects.glGetObjectParameterARB(obj, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB, iVal);

		int length = iVal.get();
		if (length > 1) {
			System.out.println();
			ByteBuffer infoLog = Utils.allocByteBuffer(length);
			iVal.flip();
			ARBShaderObjects.glGetInfoLogARB(obj, iVal, infoLog);
			byte[] infoBytes = new byte[length];
			infoLog.get(infoBytes);
			String out = new String(infoBytes);
			System.out.println("Info log: " + name + "\n" + out);
		}

		Util.checkGLError();
	}

	public abstract LocalParameterManager GetParamaters();
	
	
}

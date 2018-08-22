package com.mystictri.neotextureedit;

import java.nio.FloatBuffer;

import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.glu.GLU;

import com.mystictri.neotextureedit.OpenGLTextureRenderCanvas.GLPreviewParameters;

import engine.parameters.AbstractParam;
import engine.parameters.EnumParam;
import engine.parameters.FloatParam;
import engine.parameters.IntParam;
import engine.parameters.LocalParameterManager;
import engine.parameters.ParamChangeListener;
import engine.graphics.synthesis.texture.Channel;

class ShaderBasic extends AbstractShader {

	int texidDiffuse = 0;
	int texidNormal = 0;
	int texidSpecWeight = 0;
	int texidHeightmap = 0;
	int texidEmissive = 0;
	
	int previewProgram = 0;
	
	int u_WS_EyePos_loc;
	int u_CameraONB_loc;
	int _2dDiffuseMap_loc;
	int _2dNormalMap_loc;
	int _2dSpecWeightMap_loc;
	int _2dHeightMap_loc;
	int _2dEmissiveMap_loc;
	
	int u_SpecularPower_loc;
	int u_POM_Strength_loc;
	int u_TexScale_loc;
	int u_Ambient_loc;
	
	static class BasicShaderPreviewParameters extends LocalParameterManager {
		public FloatParam specularPower = CreateLocalFloatParam("Spec. Power", 20.0f, 0.f, 200.0f);
		public FloatParam pomStrength = CreateLocalFloatParam("POM Strength", 0.25f, 0.f, 1.0f).setDefaultIncrement(0.125f);
		public FloatParam ambient = CreateLocalFloatParam("Ambient", 0.5f, 0.f, 1.0f).setDefaultIncrement(0.125f);
		public IntParam texScaleU = CreateLocalIntParam("TexScale U", 1, 1, 8);
		public IntParam texScaleV = CreateLocalIntParam("TexScale V", 1, 1, 8);
	}
	
	BasicShaderPreviewParameters params = new BasicShaderPreviewParameters();
	
	@Override
	public LocalParameterManager GetParamaters() {
		return (LocalParameterManager)params;
	}
	
	// very basic preview shader (does compute the camera transform extra to provide world space coordinates for the fragment shader)
	String previewVertexShader = 		
		"uniform vec3 u_WS_EyePos; "+
		"uniform mat3 u_CameraONB; "+
		"attribute vec3 a_OS_tangent; "+
		
		"varying vec3 v_WS_vertex; "+
		"varying vec3 v_WS_normal; "+
		"varying vec3 v_WS_tangent; "+
		
		"void main(void) { "+
		"	gl_TexCoord[0] = gl_MultiTexCoord0; "+
		
		"	v_WS_vertex = vec3(gl_ModelViewMatrix * gl_Vertex); "+
		"	v_WS_normal = vec3(gl_NormalMatrix * gl_Normal); "+
		"	v_WS_tangent = vec3(gl_NormalMatrix * a_OS_tangent); "+
		
		"	gl_Position = gl_ProjectionMatrix * vec4((v_WS_vertex - u_WS_EyePos) * u_CameraONB, 1); "+
		"}";
	
	// very basic preview shader
	String previewFragmentShader = 
		"uniform vec3 u_WS_EyePos; " +

		"uniform sampler2D _2dTex0; " +
		"uniform sampler2D _2dNormalMap; " +
		"uniform sampler2D _2dSpecWeightMap; " +
		"uniform sampler2D _2dHeightMap; " +
		"uniform sampler2D _2dEmissiveMap; " +

		"varying vec3 v_WS_vertex; " +
		"varying vec3 v_WS_normal; " +
		"varying vec3 v_WS_tangent; " +
		"" +
		"uniform float u_SpecularPower; " +
		"uniform float u_POM_Strength; " +
		"uniform vec2 u_TexScale; " +
		"uniform float u_Ambient; " +
		"" +

		"void main(void){ " +
		"	vec3 lightPos = vec3(20.0, 60.0, 40.0); " +

		"	vec3 lightDir = normalize(lightPos - v_WS_vertex); " +

		"	mat3 onb; " +
		"	onb[2] = normalize(v_WS_normal); " +
		"	onb[0] = normalize(v_WS_tangent - dot(onb[2], v_WS_tangent)*onb[2]); " +
		"	onb[1] = cross(onb[2], onb[0]); " +

		"	vec3 rayDir = v_WS_vertex - u_WS_EyePos; " +
		"	vec3 eyeDir = normalize(rayDir * onb); " +

		"	vec3 pos = vec3(gl_TexCoord[0].xy * u_TexScale, 1.0); " +

		"   rayDir = eyeDir; " +
		"   if (rayDir.z > 0.0) rayDir.z = -rayDir.z; " +
		"	if (u_POM_Strength > 0.0) {" +
		"     rayDir.z /= 0.25*u_POM_Strength; " +
		"     rayDir.x *= 0.5; " +
		"     rayDir.y *= 0.5; " +
		"     for (int i = 0; i < 512; i++) { " +
		"     	  if (pos.z < 1.0-(texture2D(_2dHeightMap, pos.xy).r)) break; " +
		"   	  pos += rayDir*0.001; " +
		"     } " +
		"   } " +

		"	vec3 normal = onb * normalize(texture2D(_2dNormalMap, pos.xy).rgb * 2.0 - 1.0); " +

		"	vec3 refl = reflect(lightDir, normal); " +
		"	float SpecWeightyness = texture2D(_2dSpecWeightMap, pos.xy).x;" +
		"	float spec = pow(max(dot(refl, eyeDir), 0.0), u_SpecularPower)*0.5*SpecWeightyness; " +
		"	float diff = dot(lightDir, normal); " +
		"	if (diff < 0.0) diff = 0.0; " +
		//"	diff = min(diff+u_Ambient, 1.0); " +
		
		"	vec4 emssvtxt = texture2D(_2dEmissiveMap, pos.xy); " +
		"	diff = min(diff+u_Ambient+emssvtxt.a, 1.0); " +
		//"	diff = min(u_Ambient+emssvtxt.a, 1.0); " +
		
		//"	vec4 color = vec4(texture2D(_2dTex0, pos.xy).xyz, 0.0); " +
		//"	color += vec4(emssvtxt.x, emssvtxt.y, emssvtxt.z, 0.0) * (emssvtxt.a); " +
		
		"	vec4 color = mix(texture2D(_2dTex0, pos.xy),emssvtxt,emssvtxt.a) * diff + spec; " +
		//"	vec4 color = vec4(texture2D(_2dTex0, pos.xy).xyz, 0.0) * diff + spec; " +
		"	gl_FragColor = color; " +
		"}";
	
	@Override
	void loadShaderLocations() {
		u_WS_EyePos_loc = getUniformLocation("u_WS_EyePos", previewProgram);
		u_CameraONB_loc = getUniformLocation("u_CameraONB", previewProgram);
		
		_2dDiffuseMap_loc = getUniformLocation("_2dTex0", previewProgram);
		_2dNormalMap_loc  = getUniformLocation("_2dNormalMap", previewProgram);
		_2dSpecWeightMap_loc  = getUniformLocation("_2dSpecWeightMap", previewProgram);
		_2dHeightMap_loc  = getUniformLocation("_2dHeightMap", previewProgram);
		_2dEmissiveMap_loc = getUniformLocation("_2dEmissiveMap", previewProgram);
		
		u_SpecularPower_loc = getUniformLocation("u_SpecularPower", previewProgram);
		u_POM_Strength_loc = getUniformLocation("u_POM_Strength", previewProgram);
		u_TexScale_loc = getUniformLocation("u_TexScale", previewProgram);
		u_Ambient_loc = getUniformLocation("u_Ambient", previewProgram);
		
		bindTangentAttribute("a_OS_tangent");
	}
	
	@Override
	public void UpdateDiffuse(Channel _updateDiffuse) {
		if (_updateDiffuse != null) update2dTexture(ChannelUtils.createAndComputeImage(_updateDiffuse, TEXTURE_RESX, TEXTURE_RESY, null, 0), texidDiffuse);
		else update2dTexture_ConstanctColor(0x7F7FFFFF, texidDiffuse);
	}
		
	@Override
	public void UpdateNormal(Channel _updateNormal) {
		if (_updateNormal != null) update2dTexture(ChannelUtils.createAndComputeImage(_updateNormal, TEXTURE_RESX, TEXTURE_RESY, null, 0), texidNormal);
		else update2dTexture_ConstanctColor(0x7F7FFFFF, texidNormal);
	}
	
	@Override
	public void UpdateSpecWeight(Channel _updateSpecWeight) {
		if (_updateSpecWeight != null) update2dTexture(ChannelUtils.createAndComputeImage(_updateSpecWeight, TEXTURE_RESX, TEXTURE_RESY, null, 0), texidSpecWeight);
		else update2dTexture_ConstanctColor(0xFFFFFFFF, texidSpecWeight);
	}
		
	@Override
	public void UpdateHeightmap(Channel _updateHeightmap) {
		if (_updateHeightmap != null) update2dTexture(ChannelUtils.createAndComputeImage(_updateHeightmap, TEXTURE_RESX, TEXTURE_RESY, null, 0), texidHeightmap);
		else update2dTexture_ConstanctColor(0xFFFFFFFF, texidHeightmap);
	}
	
	@Override
	public void UpdateEmissive(Channel _updateEmissive) {
		if (_updateEmissive != null) update2dTexture(ChannelUtils.createAndComputeImage(_updateEmissive, TEXTURE_RESX, TEXTURE_RESY, null, 3), texidEmissive);
		else update2dTexture_ConstanctColor(0x00000000, texidEmissive);
	}

	@Override
	protected int GetProgramID() {
		return previewProgram;
	}
	
	@Override
	public void render1() {
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		
		
		ARBShaderObjects.glUseProgramObjectARB(previewProgram);
	}
	
	@Override
	public void render2() {
		GL13.glActiveTexture(GL13.GL_TEXTURE0+0);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texidDiffuse);
		ARBShaderObjects.glUniform1iARB(_2dDiffuseMap_loc, 0);			
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0+1);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texidNormal);
		ARBShaderObjects.glUniform1iARB(_2dNormalMap_loc, 1);			
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0+2);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texidSpecWeight);
		ARBShaderObjects.glUniform1iARB(_2dSpecWeightMap_loc, 2);			

		GL13.glActiveTexture(GL13.GL_TEXTURE0+3);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texidHeightmap);
		ARBShaderObjects.glUniform1iARB(_2dHeightMap_loc, 3);	
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0+4);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texidEmissive);
		ARBShaderObjects.glUniform1iARB(_2dEmissiveMap_loc, 4);	

		ARBShaderObjects.glUniform1fARB(u_SpecularPower_loc, params.specularPower.get());
		ARBShaderObjects.glUniform1fARB(u_POM_Strength_loc, params.pomStrength.get());
		ARBShaderObjects.glUniform2fARB(u_TexScale_loc, params.texScaleU.get(), params.texScaleV.get());
		ARBShaderObjects.glUniform1fARB(u_Ambient_loc, params.ambient.get());
	}
	
	@Override
	public void updateCamera(float eye_x, float eye_y, float eye_z, FloatBuffer m_CamONB) {
		ARBShaderObjects.glUniform3fARB(u_WS_EyePos_loc, eye_x, eye_y, eye_z);
		ARBShaderObjects.glUniformMatrix3ARB(u_CameraONB_loc, false, m_CamONB);
	}
	
	@Override
	protected void initGLState() {
		previewProgram = getShaderProgram(previewVertexShader, previewFragmentShader);
		
		// TODO: consider running this every render call with a boolean to know if it's already run
		// this would allow the shader to do "per execute" actions if needed
		loadShaderLocations();

		// hmm: temp initialization here
		// create the empty textures (later they are updated from the channels)
		texidDiffuse = create2dTexture(0x7F7FFFFF);
		texidNormal = create2dTexture(0x7F7FFFFF);
		texidSpecWeight = create2dTexture(0xFFFFFFFF);
		texidHeightmap = create2dTexture(0xFFFFFFFF);
		texidEmissive = create2dTexture(0x00000000);
	}
}

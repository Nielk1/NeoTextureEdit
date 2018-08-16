package engine.graphics.synthesis.texture;

import engine.base.FMath;
import engine.base.Vector4;
import engine.graphics.synthesis.texture.CacheTileManager.TileCacheEntry;
import engine.parameters.BoolParam;
import engine.parameters.EnumParam;
import engine.parameters.FloatParam;

public class FilterMath1 extends Channel {
	FloatParam A = CreateLocalFloatParam("A", 0.0f, -Float.MAX_VALUE, Float.MAX_VALUE).setDefaultIncrement(0.25f);
	
	EnumParam function = CreateLocalEnumParam("Function", "I + a,I * a,a - I,I ^ a");
	
	BoolParam onR = CreateLocalBoolParam("R", true);
	BoolParam onG = CreateLocalBoolParam("G", true);
	BoolParam onB = CreateLocalBoolParam("B", true);
	BoolParam onA = CreateLocalBoolParam("A", false);
	
	FloatParam ClampMax = CreateLocalFloatParam("Clamp Max", 1f, -Float.MAX_VALUE, Float.MAX_VALUE).setDefaultIncrement(0.1f);
	BoolParam EnableClampMax = CreateLocalBoolParam("Enable Clamp Max", false);
	FloatParam ClampMin = CreateLocalFloatParam("Clamp Min", 0f, -Float.MAX_VALUE, Float.MAX_VALUE).setDefaultIncrement(0.1f);
	BoolParam EnableClampMin = CreateLocalBoolParam("Enable Clamp Min", false);
	
	public String getName() {
		return "Math1";
	}

	public String getHelpText() {
		return "Computes some basic math on a single input.";
	}
	
	public FilterMath1() {
		super(1);
	}
	
	public OutputType getOutputType() {
		return OutputType.RGBA;
	}

	public OutputType getChannelInputType(int idx) {
		if (idx == 0) return OutputType.RGBA;
		else System.err.println("Invalid channel access in " + this);
		return OutputType.SCALAR;
	}
	
	float apply(float I) {
		float a = A.get();
		float retVal = I;
		switch (function.getEnumPos()) {
			case 0: retVal = I + a; break;
			case 1: retVal = I * a; break;
			case 2: retVal = a - I; break;
			case 3: retVal = FMath.pow(I, a); break;
			default:
				System.err.println("Invalid function selector in " + this);
				retVal = I; break;
		}
		if(EnableClampMax.get())
			retVal = Math.min(retVal, ClampMax.get());
		if(EnableClampMin.get())
			retVal = Math.max(retVal, ClampMin.get());
		return retVal;
	}

	private final Vector4 _function(Vector4 in0, float u, float v) {
		Vector4 c = new Vector4(in0);
		if (onR.get()) c.x = apply(c.x);
		if (onG.get()) c.y = apply(c.y);
		if (onB.get()) c.z = apply(c.z);
		if (onA.get()) c.w = apply(c.w);
		return c;
	}

	protected void cache_function(Vector4 out, TileCacheEntry[] caches, int localX, int localY, float u, float v) {
		out.set(_function(caches[0].sample(localX, localY), u, v));
	}


	protected Vector4 _valueRGBA(float u, float v) {
		Vector4 c0 = inputChannels[0].valueRGBA(u, v);
		return _function(c0, u, v);
	}	
}

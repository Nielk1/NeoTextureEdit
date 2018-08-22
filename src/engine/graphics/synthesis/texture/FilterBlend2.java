/**
    Copyright (C) 2010  Holger Dammertz

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package engine.graphics.synthesis.texture;

import engine.base.Vector4;
import engine.graphics.synthesis.texture.CacheTileManager.TileCacheEntry;
import engine.parameters.BoolParam;
import engine.parameters.EnumParam;
import engine.parameters.FloatParam;

/**
 * This filter computes the output image as
 * Cout = Cin + (1-Cout.alpha) + Cout*Cout.alpha;
 * @author Holger Dammertz
 *
 */
public final class FilterBlend2 extends Channel {
	
	EnumParam blendFunction = CreateLocalEnumParam("Layer Func.", "Normal,Darken,Lighten,Multiply,Screen ,Color Dodge,Color Burn,Linear Dodge,Linear Burn,Overlay,Hard Light,Soft Light,Vivid Light,Linear Light,Pin Light,Hard Mix,Difference,Exclusion,Add,Subtract");
	FloatParam opacity = CreateLocalFloatParam("Opacity", 1.0f, 0.0f, 1.0f).setDefaultIncrement(0.125f);
	BoolParam invertAlpha = CreateLocalBoolParam("Inv. Alpha", false);
	
	
	public String getName() {
		return "Blend";
	}
	
	public String getHelpText() {
		return "Gets two inputs and blends the second one with the first \n" +
				"similar to the Layer Operations in PhotoShop or Gimp.\n" +
				"Opacity controls how much of the first image stays visible.\n" +
				"If I1 has an alpha channel it is used as\n" +
				"additional blending weight (multiplied with opacity).\n" +
				"Output Alpha is set to the alpha value of I0.\n" +
				"\n" +
				"Functions (operate only on RGB); I0 = first input; I1 = second input; O = Output:\n" +
				"   Normal:     O = I1 \n" +
				"   Mutliply:   O = I0*I1 \n" +
				"   Divide:     O = I1/(I0+1) \n" +
				"   Screen:     O = 1-(1-I0)*(1-I1) \n" +
				"   Overlay:    O = I0*(I0 + 2*I1*(1-I0)) \n" +
				"   Dodge:      O = I0/((1-I1)+1) \n" +
				"   Burn:       O = 1-((1-I0)/(I1+1)) \n" +
				"   Difference: O = |I0-I1| \n" +
				"   Addition:   O = min(1, I0+I1) \n" +
				"   Subtract:   O = max(0, I0-I1) \n" +
				"";
	}
	

	public FilterBlend2() {
		super(2);
	}
	
	public OutputType getOutputType() {
		return OutputType.RGBA;
	}
	
	public OutputType getChannelInputType(int idx) {
		if (idx == 0) return OutputType.RGBA;
		else if (idx == 1) return OutputType.RGBA;
		else System.err.println("Invalid channel access in " + this);
		return OutputType.SCALAR;
	}
	
	private final Vector4 _function(Vector4 c0, Vector4 c1) {
		float alpha = c1.w;
		if (invertAlpha.get()) alpha = 1.0f - alpha;
		
		final Vector4 color = new Vector4(c1);
		final int func = blendFunction.getEnumPos();
		
		alpha *= (opacity.get()); 
		
		// apply the blending function without alpha:
		if (func == 0) { // normal
			// do nothing
		} else if (func == 1) { // Darken
			color.set(c0).min(c1);
		} else if (func == 2) { // Lighten
			color.set(c0).max(c1);
		} else if (func == 3) { // Multiply
			color.multComp_ip(c0);
		} else if (func == 4) { // Screen 
			color.set(1).sub_ip(new Vector4(1).sub_ip(c0).multComp_ip(new Vector4(1).sub_ip(c1)));
		} else if (func == 5) { // Color Dodge
			color.divComp_ip(new Vector4(1).sub_ip(c0));
		} else if (func == 6) { // Color Burn
			color.set(1).sub_ip(new Vector4(1).sub_ip(c1).divComp_ip(c0)); 
		} else if (func == 7) { // Linear Dodge
			color.add_ip(c0);
		} else if (func == 8) { // Linear Burn
			color.sub_ip(c0); 
		} else if (func == 9) { // Overlay
			Vector4 Multiply = new Vector4(c1).multComp_ip(c0);
			Vector4 Screen = new Vector4(1).sub_ip(new Vector4(1).sub_ip(c0).multComp_ip(new Vector4(1).sub_ip(c1)));
			color.set(c1.x > 0.5f ? Screen.x : Multiply.x,
					  c1.y > 0.5f ? Screen.y : Multiply.y,
					  c1.z > 0.5f ? Screen.w : Multiply.z,
					  c1.w > 0.5f ? Screen.x : Multiply.w);
		} else if (func == 10) { // Hard Light
			Vector4 Multiply = new Vector4(c1).multComp_ip(c0);
			Vector4 Screen = new Vector4(1).sub_ip(new Vector4(1).sub_ip(c0).multComp_ip(new Vector4(1).sub_ip(c1)));
			color.set(c0.x > 0.5f ? Screen.x : Multiply.x,
					  c0.y > 0.5f ? Screen.y : Multiply.y,
					  c0.z > 0.5f ? Screen.w : Multiply.z,
					  c0.w > 0.5f ? Screen.x : Multiply.w);
		} else if (func == 11) { // Soft Light
			Vector4 xx = new Vector4(2).multComp_ip(c0).sub_ip(1);
			Vector4 x0 = xx.multComp_ip(new Vector4(c1).sub_ip(new Vector4(c1).pow_ip(2))).add_ip(c1); // (2*A-1)*(    B-B^2)+B;
			Vector4 x1 = xx.multComp_ip(new Vector4(c1).sqrt_ip().sub_ip(c1)).add_ip(c1);              // (2*A-1)*(sqrt(B)-B)+B;
			color.set(c0.x > 0.5f ? x1.x : x0.x,
					  c0.y > 0.5f ? x1.y : x0.y,
					  c0.z > 0.5f ? x1.w : x0.z,
					  c0.w > 0.5f ? x1.x : x0.w);
		} else if (func == 12) { // Vivid Light
			Vector4 x0 = new Vector4(1).sub_ip(new Vector4(1).sub_ip(c1).divComp_ip(new Vector4(c0).mult_ip(2)));
			Vector4 x1 = new Vector4(c1).divComp_ip(new Vector4(1).sub_ip(c0).mult_ip((2)));
			color.set(c0.x > 0.5f ? x1.x : x0.x,
					  c0.y > 0.5f ? x1.y : x0.y,
					  c0.z > 0.5f ? x1.w : x0.z,
					  c0.w > 0.5f ? x1.x : x0.w);
		} else if (func == 13) { // Linear Light
			color.add_ip(new Vector4(c0).mult_ip(2)).sub_ip(1);
		} else if (func == 14) { // Pin Light
			Vector4 x0 = new Vector4(c0).mult_ip(2).sub_ip(1);
			Vector4 x1 = c1;
			Vector4 x2 = new Vector4(c0).mult_ip(2);
			color.set(c1.x < (2*c0.x-1) ? x0.x : c1.x > (2*c0.x) ? x2.x : x1.x,
					  c1.y < (2*c0.y-1) ? x0.y : c1.y > (2*c0.y) ? x2.y : x1.y,
					  c1.z < (2*c0.z-1) ? x0.z : c1.z > (2*c0.z) ? x2.z : x1.z,
					  c1.w < (2*c0.w-1) ? x0.w : c1.w > (2*c0.w) ? x2.w : x1.w);			
		} else if (func == 15) { // Hard Mix // TODO: determine if this is correct, docs say A<1-B and A>1-B missing when A==1-B
			color.set(c0.x > (1f-c1.x) ? 1 : c0.x < (1f-c1.x) ? 0 : c0.x,
					  c0.y > (1f-c1.y) ? 1 : c0.y < (1f-c1.y) ? 0 : c0.y,
					  c0.z > (1f-c1.z) ? 1 : c0.z < (1f-c1.z) ? 0 : c0.z,
					  c0.w > (1f-c1.w) ? 1 : c0.w < (1f-c1.w) ? 0 : c0.w);
		} else if (func == 16) { // Difference
			color.set(c0).sub_ip(c1).abs_ip();
		} else if (func == 17) { // Exclusion
			color.set(c0).add_ip(c1).sub_ip(new Vector4().multComp_ip(c0).multComp_ip(c1));
		} else if (func == 18) { // Add
			color.add_ip(c0);
		} else if (func == 19) { // Subtract
			color.set(c0).sub_ip(c1);
		}/* else if (func == 20) { // Hue
			
		} else if (func == 21) { // Hue (PSP)
			
		} else if (func == 22) { // Saturation
			
		} else if (func == 23) { // Saturation (PSP)
			
		} else if (func == 24) { // Color
			
		} else if (func == 25) { // Color (PSP)
			
		} else if (func == 26) { // Luminance
			
		} else if (func == 27) { // Luminance (PSP)
			
		}*/
		
		color.clamp(0.0f, 1.0f);
		
		float origW = c0.w; // keep alpha ???
		c0.mult_ip(1.0f - alpha);
		c0.mult_add_ip(alpha, color);
		c0.w = origW;
		return c0;
	}
	
	
	protected void cache_function(Vector4 out, TileCacheEntry[] caches, int localX, int localY, float u, float v) {
		out.set(_function(caches[0].sample(localX, localY), caches[1].sample(localX, localY)));
	}
	
	
	
	protected float _value1f(float u, float v) {
		Vector4 val = valueRGBA(u, v);
		return (val.x+val.y+val.z)*(1.0f/3.0f);
	}
	
	protected Vector4 _valueRGBA(float u, float v) {
		return _function(inputChannels[0].valueRGBA(u, v), inputChannels[1].valueRGBA(u, v));
	}
}

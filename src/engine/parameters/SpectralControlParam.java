package engine.parameters;

import java.io.IOException;
import java.io.Writer;
import java.util.Scanner;
import java.util.Vector;

import engine.base.Logger;

/**
 * This param class holds the spectral weights parameters as used for controlling the addition of noises of different
 * octaves.
 * 
 * @author Holger Dammertz
 * 
 */
public final class SpectralControlParam extends AbstractParam {
	private Vector<Float> values = new Vector<Float>();
	private int startBand;
	private int endBand;
	
	
	public SpectralControlParam(String name) {
		this.name = name;
	}

	public void save(Writer w) throws IOException {
		w.write(values.size() + " ");
		for (float i : values) {
			w.write(i + " ");
		}
	}

	public void load(Scanner s) {
		values.clear();
		int num = s.nextInt();
		for (int i = 0; i < num; i++) {
			values.add(Float.parseFloat(s.next()));
		}
		notifyParamChangeListener();
	}
	
	public int getStartBand() {
		return startBand;
	}
	
	public int getEndBand() {
		return endBand;
	}
	
	
	public void setStartEndBand(int start, int end, float persistence) {
		if (start != startBand || end != endBand) {
			startBand = start;
			endBand = end;
			
			float mult = 1.0f;
			for (int i = startBand; i <= endBand; i++) {
				get(i, mult);
				mult *= persistence;
			}
			notifyParamChangeListener();
		}
	}

	public void set(int idx, float value) {
		if (idx < 0) return;
		// we actually add default values here when accessing an invalid index
		while (values.size() <= idx) values.add(0.5f);
		
		if (values.get(idx) != value) {
			values.set(idx, value);
			notifyParamChangeListener();
		}
	}

	
	public float get(int idx, float defaultValue) {
		if (idx < 0) return 0.0f;
		// we actually add default values here when accessing an invalid index
		while (values.size() <= idx) { 
		//	Logger.log(this,"adding default value" + defaultValue);
			values.add(defaultValue);
	
		}
		return values.get(idx);
	}

	public static SpectralControlParam create(String name) {
		SpectralControlParam ret = new SpectralControlParam(name);
		return ret;
	}

	public static SpectralControlParam createManaged(String name) {
		SpectralControlParam ret = create(name);
		ParameterManager.add(ret);
		return ret;
	}
}

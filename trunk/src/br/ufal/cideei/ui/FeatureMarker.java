package br.ufal.cideei.ui;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;

import cide_ei.Activator;

public class FeatureMarker{
	
	public static final String FMARKER_ID = Activator.PLUGIN_ID + ".featuremarker";
	
	public static void createMarker(String message, Location loc){
		try {
			IMarker marker = loc.getFile().createMarker(FMARKER_ID);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.LINE_NUMBER, loc.getLineNumber());
			//marker.setAttribute(IMarker.LOCATION, loc.getFile().getName());
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

}


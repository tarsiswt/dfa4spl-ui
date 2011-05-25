package br.ufal.cideei.ui;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.*;
import org.eclipse.ui.views.markers.MarkerSupportView;

public class AnalysisResultView extends MarkerSupportView {
		
	//private final AnalysisResultView view = new AnalysisResultView();

	public AnalysisResultView() {
		super("analysisresultsSupport");
		/*this.addListenerObject(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent arg0) {
				IMarker[] markers = view.getSelectedMarkers();
				IMarker marker = markers[0];
				try {
					System.out.println(marker.getAttribute(IMarker.MESSAGE));
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		});*/
	}

	
}
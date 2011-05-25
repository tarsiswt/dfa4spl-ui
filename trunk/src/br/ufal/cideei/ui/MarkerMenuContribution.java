package br.ufal.cideei.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class MarkerMenuContribution extends ContributionItem{

	private ITextEditor editor;
	private IVerticalRulerInfo rulerInfo;
	private List<IMarker> markers;

	public MarkerMenuContribution(ITextEditor editor){
	    this.editor = editor;
	    this.rulerInfo = getRulerInfo();
	    this.markers = getMarkers();
	}

	private IVerticalRulerInfo getRulerInfo(){
	    return (IVerticalRulerInfo) editor.getAdapter(IVerticalRulerInfo.class);
	}

	private List<IMarker> getMarkers(){
	    List<IMarker> clickedOnMarkers = new ArrayList<IMarker>();
	    for (IMarker marker : getAllMarkers()){
	        if (markerHasBeenClicked(marker)){
	            clickedOnMarkers.add(marker);
	        }
	    }

	    return clickedOnMarkers;
	}

	//Determine whether the marker has been clicked using the ruler's mouse listener
	private boolean markerHasBeenClicked(IMarker marker){
	    return (marker.getAttribute(IMarker.LINE_NUMBER, 0)) == (rulerInfo.getLineOfLastMouseButtonActivity() + 1);
	}

	//Get all My Markers for this source file
	private IMarker[] getAllMarkers(){
	    try {
			return ((FileEditorInput) editor.getEditorInput()).getFile()
			    .findMarkers(FeatureMarkerCreator.FMARKER_ID, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	//Create a menu item for each marker on the line clicked on
	public void fill(Menu menu, int index){
	    for (final IMarker marker : markers){
	        MenuItem menuItem = new MenuItem(menu, SWT.CHECK, index);
	        menuItem.setText(marker.getAttribute(IMarker.MESSAGE, ""));
	        menuItem.addSelectionListener(createDynamicSelectionListener(marker));
	    }
	}

	//Action to be performed when clicking on the menu item is defined here
	private SelectionAdapter createDynamicSelectionListener(final IMarker marker){
	    return new SelectionAdapter(){
	        public void widgetSelected(SelectionEvent e){
	            System.out.println(marker.getAttribute(IMarker.MESSAGE, ""));
	        }
	    };
	}
	}
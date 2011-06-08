package br.ufal.cideei.handlers2;

import java.util.ArrayList;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.views.markers.MarkerItem;
import org.eclipse.ui.views.markers.MarkerSupportView;
import org.eclipse.ui.views.markers.MarkerViewHandler;

import de.ovgu.cide.language.jdt.editor.ColoredCompilationUnitEditor;

public class HideFeatureHandler extends MarkerViewHandler  {

	@Override
	@SuppressWarnings({ "unused", "restriction" })
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IStructuredSelection selection = (IStructuredSelection) HandlerUtil.getActiveMenuSelection(event);
		Shell shell = HandlerUtil.getActiveShellChecked(event);
		
		Object marker = selection.getFirstElement();
		
		MarkerSupportView page = getView(event);
		
		IEditorPart editor = page.getSite().getWorkbenchWindow().getActivePage().getActiveEditor();
		editor.setFocus();
		
		System.out.println(editor);
		System.out.println(editor instanceof ColoredCompilationUnitEditor);
				
		Position p = new Position(27,4000);
		ArrayList positions = new ArrayList<Position>();
		positions.add(p);
		
		System.out.println(positions);
		
		if(editor instanceof ColoredCompilationUnitEditor){
			((ColoredCompilationUnitEditor) editor).updateFoldingStructure(positions);
			//System.out.println(((ColoredCompilationUnitEditor) editor).getProjectionAnnotationModel().collapseAll(offset, length));
		}else{
			System.out.println("FUUU!");
		}
		
		if(marker instanceof MarkerItem){
			try {
				String feature  = (String) ((MarkerItem) marker).getMarker().getAttribute(IMarker.TEXT);
				feature = feature.substring(1, feature.length() - 1);
				System.out.println("The configuration that will be visible is : "+feature);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	
		return null;
	}

}

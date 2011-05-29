package br.ufal.cideei.handlers2;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.views.markers.*;

public class HideFeatureHandler extends MarkerViewHandler  {

	@Override
	@SuppressWarnings("unused")
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IStructuredSelection selection = (IStructuredSelection) HandlerUtil.getActiveMenuSelection(event);
		Shell shell = HandlerUtil.getActiveShellChecked(event);
		
		Object marker = selection.getFirstElement();
		
		if(marker instanceof MarkerItem){
			try {
				String feature  = (String) ((MarkerItem) marker).getMarker().getAttribute(IMarker.TASK);
				feature = feature.substring(1, feature.length() - 1);
				System.out.println("The feature that will be hidden is : "+feature);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
	
		return null;
	}

}

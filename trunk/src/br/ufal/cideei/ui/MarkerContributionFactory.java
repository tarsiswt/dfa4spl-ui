package br.ufal.cideei.ui;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.menus.ExtensionContributionFactory;
import org.eclipse.ui.menus.IContributionRoot;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;

public class MarkerContributionFactory extends ExtensionContributionFactory{

	public void createContributionItems(IServiceLocator serviceLocator, IContributionRoot additions){
	    ITextEditor editor = (ITextEditor) 
	        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();

	    additions.addContributionItem(new MarkerMenuContribution(editor), null);
	}
	}

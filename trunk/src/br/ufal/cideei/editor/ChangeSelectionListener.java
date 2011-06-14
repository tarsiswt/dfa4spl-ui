package br.ufal.cideei.editor;

import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;


@SuppressWarnings("restriction")
public class ChangeSelectionListener implements ISelectionListener{

	private int previousCaretPosition;
	
	@Override
	public void selectionChanged(IWorkbenchPart arg0, ISelection arg1) {
		
		ExtendedColoredJavaEditor editor = (ExtendedColoredJavaEditor) arg0.getSite().getWorkbenchWindow().getActivePage().getActiveEditor().getAdapter(ExtendedColoredJavaEditor.class);
		
		if(editor instanceof ExtendedColoredJavaEditor){
			
			System.out.println("#####################");
	
			// Get source viewer  
	        ISourceViewer viewer = editor.getViewer();
	        if (viewer == null)
	            return;
	
	        // Get the caret position	
	        Point selectedRange = viewer.getSelectedRange();
	        int caretAt = selectedRange.x;
	        int length = selectedRange.y;
	
	        // Get the Java element
	        ITypeRoot element = JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
	        if (element == null)
	            return;
	
	        // Get the compilation unit AST
	        CompilationUnit ast = SharedASTProvider.getAST(element,SharedASTProvider.WAIT_YES, null);
	        if (ast == null)
	            return;
	
	        // Find the node at caret position
	        NodeFinder finder = new NodeFinder(caretAt, length);
	        ast.accept(finder);
	     
	        ASTNode originalNode = finder.getCoveringNode();
        
	        if (originalNode.getNodeType() == ASTNode.ASSIGNMENT) {
	        	
	        	System.out.println("==========================> " + previousCaretPosition);
	        	System.out.println("==========================> " + caretAt);

	        	previousCaretPosition = caretAt;
	        	
	        	System.out.println("==========================> " + originalNode.toString());
	        }
		}
		
	}

}

package br.ufal.cideei.editor;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.ASTNode;
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

	private int selectionLength;
	private int caretPosition;
	private ExtendedColoredJavaEditor editor;
	private Thread timer;
	private IFile file;
	
	public int getCaretPosition() {
		return caretPosition;
	}
	
	public IFile getFile() {
		return file;
	}

	@Override
	public void selectionChanged(IWorkbenchPart arg0, ISelection arg1) {
		
		ExtendedColoredJavaEditor editor = (ExtendedColoredJavaEditor) arg0.getSite().getWorkbenchWindow().getActivePage().getActiveEditor().getAdapter(ExtendedColoredJavaEditor.class);
		
		if(editor instanceof ExtendedColoredJavaEditor){
			
			this.editor = editor;
			
			this.file = (IFile) editor.getEditorInput().getAdapter(IFile.class);
			
			// Get source viewer  
			ISourceViewer viewer = editor.getViewer();
	        if (viewer == null)
	            return;
	
	        // Get the caret position	
	        Point selectedRange = viewer.getSelectedRange();
	        this.caretPosition = selectedRange.x;
	        this.selectionLength = selectedRange.y;
			
        	this.timer = new CaretTimer(this);
        	this.timer.start();
		}
		
	}

	public ASTNode getNode() {
		
		// Get the Java element
		ITypeRoot element = JavaUI.getEditorInputTypeRoot(this.editor.getEditorInput());

		// Get the compilation unit AST
		CompilationUnit ast = SharedASTProvider.getAST(element,SharedASTProvider.WAIT_YES, null);

		// Find the node at caret position
		NodeFinder finder = new NodeFinder(this.caretPosition, this.selectionLength);
		ast.accept(finder);
    
		ASTNode originalNode = finder.getCoveringNode();
		
		return originalNode;
	}

}

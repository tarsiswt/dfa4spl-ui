package br.ufal.cideei.handlers2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.views.markers.MarkerItem;
import org.eclipse.ui.views.markers.MarkerSupportView;
import org.eclipse.ui.views.markers.MarkerViewHandler;

import org.eclipse.jdt.core.dom.ASTNode;

import soot.Unit;

import br.ufal.cideei.soot.instrument.asttounit.ASTNodeUnitBridge;
import br.ufal.cideei.visitors.SupplementaryConfigurationVisitor;

import de.ovgu.cide.language.jdt.editor.ColoredCompilationUnitEditor;
import br.ufal.cideei.editor.ExtendedColoredJavaEditor;

public class HideFeatureHandler extends MarkerViewHandler  {

	@Override
	@SuppressWarnings({ "unused"})
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		ISelection selection = (ISelection) HandlerUtil.getActiveMenuSelection(event);
		Object marker = ((IStructuredSelection) selection).getFirstElement();
				
			try {
				
				if(marker instanceof MarkerItem){
					
					/*
					 * Code which gets the document on the annotations will be placed.
					 */
					
					MarkerSupportView page = getView(event);
					ExtendedColoredJavaEditor editor = (ExtendedColoredJavaEditor) page.getSite().getWorkbenchWindow().getActivePage().getActiveEditor()
					.getAdapter(ExtendedColoredJavaEditor.class);
					
					IDocumentProvider dp = editor.getDocumentProvider();
					IAnnotationModel am = editor.getProjectionAnnotationModel();
					IDocument d = editor.getDocument();
	
					/*
					 * Get the file which will be analysed by the visitor.
					 */
					
					IFile file = (IFile) HandlerUtil.getActiveEditorChecked(event).getEditorInput().getAdapter(IFile.class);
					
					/*
					 * Informations received from the selected market.
					 */
					
					String feature  = (String) ((MarkerItem) marker).getMarker().getAttribute(IMarker.TEXT);
					int lineOfMarker = ((Integer) ((MarkerItem) marker).getMarker().getAttribute(IMarker.LINE_NUMBER)).intValue();
					
					/*
					 * This visitor selects the features that will be collapsed.
					 */
					
					Set<String> configuration = this.stringToSet(feature);
					SupplementaryConfigurationVisitor supplementaryConfigurationVisitor = new SupplementaryConfigurationVisitor(configuration,file);
					
					ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(file);
					ASTParser parser = ASTParser.newParser(AST.JLS3);
					parser.setSource(compilationUnit);
					parser.setKind(ASTParser.K_COMPILATION_UNIT);
					parser.setResolveBindings(true);
					CompilationUnit jdtCompilationUnit = (CompilationUnit) parser.createAST(null);
					
					jdtCompilationUnit.accept(supplementaryConfigurationVisitor);
					HashMap<String,Set<ASTNode>> featureLines = supplementaryConfigurationVisitor.getFeatureLines();
					
					/*
					 * According to the visitor's results, the annotations will be created and added to the document.
					 */
					
					Set<String> features = supplementaryConfigurationVisitor.getFeatureNames();
					Iterator<String> featureNames = features.iterator();
					
					ASTNode node = null;
					String featureName = null;
					Set<Integer> lines = null;
					Iterator<ASTNode> iteratorNodes = null;
					Set<ASTNode> nodes = null;
					
					HashMap<String, TreeSet<Integer>> featuresLineNumbers = convertFromNodesToLines(
							jdtCompilationUnit, featureLines, featureNames);
	
					ArrayList<Position> positions = createPositions(d, features,featuresLineNumbers);
					
					/*
					 * The action which updates the editor to show the folding areas.
					 */
					
					if(editor instanceof ExtendedColoredJavaEditor){
						((ExtendedColoredJavaEditor) editor).updateFoldingStructure(positions);
					}
								
				}
			
			} catch (CoreException e) {
				e.printStackTrace();
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
			
		return null;
	}

	private ArrayList<Position> createPositions(IDocument d,Set<String> features,HashMap<String, TreeSet<Integer>> featuresLineNumbers) throws BadLocationException {
		
		Iterator<String> featureNames;
		String featureName;
		Set<Integer> lines;
		ArrayList<Position> positions = new ArrayList<Position>();
		
		Iterator<Integer> iteratorInteger = null;
		int line = 0;
		int previousLine = 0;
		int length = 0;
		int offset = 0;
		boolean newAnnotation = false;
		featureNames = features.iterator();
		
		while (featureNames.hasNext()) {
			
			line = 0;
			previousLine = 0;
			length = 0;
			offset = 0;
			
			featureName = featureNames.next();
			lines = featuresLineNumbers.get(featureName);
			
			if(lines.size() > 1){
			
				iteratorInteger = lines.iterator();
				offset = d.getLineOffset(iteratorInteger.next().intValue());
										
				while (iteratorInteger.hasNext()) {
					if(newAnnotation == true){
						offset = line;
						line = previousLine;
						newAnnotation = false;
					}else{
						line = iteratorInteger.next().intValue() - 1;
					}
					length = length + d.getLineLength(line);
					if(previousLine > 0 && line > previousLine + 1){
						previousLine = line;
						newAnnotation = true;
						positions.add(new Position(offset,length));
						break;
					}
					previousLine = line;							
				}
				positions.add(new Position(offset,length));
			}
		}
		return positions;
	}

	private HashMap<String, TreeSet<Integer>> convertFromNodesToLines(
			CompilationUnit jdtCompilationUnit,
			HashMap<String, Set<ASTNode>> featureLines,
			Iterator<String> featureNames) {
		
		ASTNode node = null;
		String featureName = null;
		Set<Integer> lines = null;
		Iterator<ASTNode> iteratorNodes = null;
		Set<ASTNode> nodes = null;
		HashMap<String,TreeSet<Integer>> featuresLineNumbers = new HashMap<String, TreeSet<Integer>>();
		
		while (featureNames.hasNext()) {
			
			featureName = featureNames.next();
			nodes = featureLines.get(featureName);
			lines = new TreeSet<Integer>();
			iteratorNodes = nodes.iterator();
			
			while (iteratorNodes.hasNext()) {
				node = iteratorNodes.next();
				lines.add(new Integer(jdtCompilationUnit.getLineNumber(node.getStartPosition())));
			}
			
			featuresLineNumbers.put(featureName, (TreeSet<Integer>) lines);
		}
		return featuresLineNumbers;
	}
	
	private Set<String> stringToSet(String markerConfigColumn){
		
		String feature = markerConfigColumn.substring(1, markerConfigColumn.length() - 1);
		String[] featuresArray = feature.split(",");
		Set<String> configuration = new TreeSet<String>();
		for (String string : featuresArray) {
			configuration.add(string);
		}
		return configuration;
	}

}

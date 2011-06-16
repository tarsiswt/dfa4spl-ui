package br.ufal.cideei.editor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.jgrapht.graph.DirectedMultigraph;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import br.ufal.cideei.features.CIDEFeatureExtracterFactory;
import br.ufal.cideei.features.IFeatureExtracter;
import br.ufal.cideei.handlers2.ReachingDefinitionsHandler;
import br.ufal.cideei.soot.SootManager;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;
import br.ufal.cideei.soot.instrument.FeatureTag;
import br.ufal.cideei.soot.instrument.asttounit.ASTNodeUnitBridge;
import br.ufal.cideei.util.MethodDeclarationSootMethodBridge;
import br.ufal.cideei.util.ValueContainerEdge;

public class CaretTimer extends Thread{
	
	private ChangeSelectionListener listener;
	private int caretPositionBeforeSleeping;
	
	
	public CaretTimer(ChangeSelectionListener listener){
		this.listener = listener;
		this.caretPositionBeforeSleeping = listener.getCaretPosition();
	}
	
	@Override
	public void run() {
		try {
			Thread.sleep(3000);
			int actualCaretPosition = this.listener.getCaretPosition();
			if(actualCaretPosition ==  this.caretPositionBeforeSleeping){
				ASTNode node = this.listener.getNode();
				
				if(this.compareNodeType(node.getNodeType())){
					HashSet<ASTNode> nodes = new HashSet<ASTNode>();
					nodes.add(node);
					
					IFile file = this.listener.getFile();
					
					IFeatureExtracter extracter = CIDEFeatureExtracterFactory.getInstance().newExtracter();
					
					String correspondentClasspath = MethodDeclarationSootMethodBridge.getCorrespondentClasspath(file);
					SootManager.configure(correspondentClasspath);
					MethodDeclaration methodDeclaration = MethodDeclarationSootMethodBridge.getParentMethod(nodes.iterator().next());
					String declaringMethodClass = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName();
					MethodDeclarationSootMethodBridge mdsm = new MethodDeclarationSootMethodBridge(methodDeclaration);
					SootMethod sootMethod = SootManager.getMethodBySignature(declaringMethodClass, mdsm.getSootMethodSubSignature());
					Body body = sootMethod.retrieveActiveBody();
					
					CompilationUnit jdtCompilationUnit = ReachingDefinitionsHandler.getCompilationUnit(file);
					
					Collection<Unit> unitsInSelection = ASTNodeUnitBridge.getUnitsFromLines(ASTNodeUnitBridge.getLinesFromASTNodes(nodes, jdtCompilationUnit),
							body);
					
					FeatureTag<Set<String>> bodyFeatureTag = ReachingDefinitionsHandler.getFeatureTags(extracter,
							correspondentClasspath, body);
					
					LiftedReachingDefinitions reachingDefinitions = ReachingDefinitionsHandler.executeLiftedReachingDefinitionAnalysis(
							body, bodyFeatureTag);
					
					DirectedMultigraph<Unit, ValueContainerEdge<Set<String>>> reachesData = ReachingDefinitionsHandler.createProvidesGraphCaret(unitsInSelection, reachingDefinitions, body);
					
					ReachingDefinitionsHandler.populateView(reachesData, file);
				}
				new ThreadDeath();
			}else{
				new ThreadDeath();
			}
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	private boolean compareNodeType(int nodeType){
		boolean result = false;
		switch (nodeType){
			case ASTNode.ASSIGNMENT:
				result = true;
				break;
			case ASTNode.EXPRESSION_STATEMENT:
				result = true;
				break;
			case ASTNode.VARIABLE_DECLARATION_EXPRESSION:
				result = true;
				break;
			case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
				result = true;
				break;
			case ASTNode.VARIABLE_DECLARATION_STATEMENT:
				result = true;
				break;
			default:
				result = false;
		}
		return result;
	}
	
}

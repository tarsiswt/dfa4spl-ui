package br.ufal.cideei.handlers2;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import soot.Body;
import soot.G;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.GotoStmt;
import soot.toolkits.graph.BriefUnitGraph;
import br.ufal.cideei.alg.bfa.BrokenFlowAnalysis;
import br.ufal.cideei.features.CIDEFeatureExtracterFactory;
import br.ufal.cideei.features.IFeatureExtracter;
import br.ufal.cideei.soot.SootManager;
import br.ufal.cideei.soot.instrument.FeatureModelInstrumentorTransformer;
import br.ufal.cideei.soot.instrument.FeatureTag;
import br.ufal.cideei.soot.instrument.asttounit.ASTNodeUnitBridge;
import br.ufal.cideei.ui.EmergentPopup;
import br.ufal.cideei.util.MethodDeclarationSootMethodBridge;
import br.ufal.cideei.util.Pair;
import br.ufal.cideei.visitors.SelectionNodesVisitor;

/**
 * Handler for the br.ufal.cideei.commands.DoCompute extension command.
 * 
 * @author Társis
 * 
 */
public class BrokenFlow extends AbstractHandler {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.commands.AbstractHandler#execute(org.eclipse.core.commands
	 * .ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// TODO: this wrapping try is for debug only. remove later.
		try {
			/*
			 * In order to perform analyses on the selected code, there are few
			 * things we need to collect first in order to configure the Soot
			 * framework environment. They are: - Which ASTNodes are in the text
			 * selection - The casspath entry to the package root of the text
			 * selection - The method name which contains the text selection -
			 * The ColoredSourceFile object of the text selection.
			 */
			ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);
			Shell shell = HandlerUtil.getActiveShellChecked(event);

			if (!(selection instanceof ITextSelection))
				throw new ExecutionException("Not a text selection");

			/*
			 * used to find out the project name and later to create a
			 * compilation unit from it
			 */
			IFile textSelectionFile = (IFile) HandlerUtil.getActiveEditorChecked(event).getEditorInput().getAdapter(IFile.class);

			/*
			 * this visitor will compute the ASTNodes that were selected by the
			 * user
			 */
			ITextSelection textSelection = (ITextSelection) selection;
			SelectionNodesVisitor selectionNodesVisitor = new SelectionNodesVisitor(textSelection);

			/*
			 * Now we need to create a compilation unit for the file, and then
			 * parse it to generate an AST in which we will perform our
			 * analyses.
			 * 
			 * TODO: is there a different way of doing this? Maybe eclipse has a
			 * copy of the compilation unit in memory already?
			 */
			ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(textSelectionFile);
			ASTParser parser = ASTParser.newParser(AST.JLS3);
			parser.setSource(compilationUnit);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setResolveBindings(true);
			CompilationUnit jdtCompilationUnit = (CompilationUnit) parser.createAST(null);
			jdtCompilationUnit.accept(selectionNodesVisitor);

			Set<ASTNode> selectionNodes = selectionNodesVisitor.getNodes();

			/*
			 * Provides a way to query for features from CIDE.
			 */
			IFeatureExtracter extracter = CIDEFeatureExtracterFactory.getInstance().newExtracter();

			/*
			 * Initialize and configure Soot's options and find out which method
			 * contains the selection
			 */
			String correspondentClasspath = MethodDeclarationSootMethodBridge.getCorrespondentClasspath(textSelectionFile);
			SootManager.configure(correspondentClasspath);
			MethodDeclaration methodDeclaration = MethodDeclarationSootMethodBridge.getParentMethod(selectionNodes.iterator().next());
			String declaringMethodClass = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName();
			MethodDeclarationSootMethodBridge mdsm = new MethodDeclarationSootMethodBridge(methodDeclaration);
			SootMethod sootMethod = SootManager.getMethodBySignature(declaringMethodClass, mdsm.getSootMethodSubSignature());
			Body body = sootMethod.retrieveActiveBody();

			/*
			 * Maps ASTNodes to Units based on the line no.
			 */
			Collection<Unit> unitsInSelection = ASTNodeUnitBridge.getUnitsFromLines(ASTNodeUnitBridge.getLinesFromASTNodes(selectionNodes, jdtCompilationUnit),
					body);
			if (unitsInSelection.isEmpty()) {
				System.out.println("the selection doesn't map to any Soot Unit");
				return null;
			}

			/*
			 * Instrumento the Jimple in-memory code.
			 */
			FeatureModelInstrumentorTransformer instrumentorTransformer = new FeatureModelInstrumentorTransformer(extracter, correspondentClasspath);
			instrumentorTransformer.transform2(body, correspondentClasspath);

			FeatureTag<Set<String>> bodyFeatureTag = (FeatureTag<Set<String>>) body.getTag("FeatureTag");

			/*
			 * Build CFG.
			 */
			BriefUnitGraph bodyGraph = new BriefUnitGraph(body);

			Set<AssignStmt> focuses = new HashSet<AssignStmt>();
			for (Unit unit : unitsInSelection) {
				if (unit instanceof AssignStmt) {
					focuses.add((AssignStmt) unit);
				}
			}

			BrokenFlowAnalysis bfa = new BrokenFlowAnalysis(bodyGraph, bodyFeatureTag, focuses);
			bfa.execute();

			String message = createMessage(bfa, focuses);

			EmergentPopup.pop(shell, message);

		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			G.v().reset();
		}

		return null;
	}

	private String createMessage(BrokenFlowAnalysis bfa, Set<AssignStmt> focuses) {
		boolean appendedConfiguration = false;
		StringBuilder stringBuilder = new StringBuilder();
		Map<Pair<AssignStmt, Set<String>>, Set<GotoStmt>> configMap = bfa.getConfigMap();
		System.out.println(configMap);
		for (Entry<Pair<AssignStmt, Set<String>>, Set<GotoStmt>> entry : configMap.entrySet()) {
			Pair<AssignStmt, Set<String>> pair = entry.getKey();
			AssignStmt assignStmt = pair.getFirst();
			Set<String> configuration = pair.getSecond();
			Set<GotoStmt> flowBreakers = entry.getValue();

			for (GotoStmt flowBreak : flowBreakers) {
				if (!appendedConfiguration) {
					stringBuilder.append(configuration);
					stringBuilder.append('\n');
					appendedConfiguration = true;
				}
				stringBuilder.append(assignStmt + " reaches flow break @line " + ASTNodeUnitBridge.getLineFromUnit(flowBreak) + " colored with "
						+ flowBreak.getTag("FeatureTag") + '\n');
			}
			appendedConfiguration = false;

		}
		return stringBuilder.toString();
	}
}

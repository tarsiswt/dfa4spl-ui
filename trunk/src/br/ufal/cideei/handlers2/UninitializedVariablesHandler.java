package br.ufal.cideei.handlers2;

import java.util.Set;

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
import soot.toolkits.graph.BriefUnitGraph;
import br.ufal.cideei.features.CIDEFeatureExtracterFactory;
import br.ufal.cideei.features.IFeatureExtracter;
import br.ufal.cideei.soot.SootManager;
import br.ufal.cideei.soot.analyses.uninitvars.LiftedUninitializedVariableAnalysis;
import br.ufal.cideei.soot.instrument.FeatureModelInstrumentorTransformer;
import br.ufal.cideei.soot.instrument.FeatureTag;
import br.ufal.cideei.util.MethodDeclarationSootMethodBridge;
import br.ufal.cideei.visitors.SelectionNodesVisitor;

/**
 * Handler for the br.ufal.cideei.commands.DoCompute extension command.
 * 
 * @author Társis
 * 
 */
public class UninitializedVariablesHandler extends AbstractHandler {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.core.commands.AbstractHandler#execute(org.eclipse.core.commands
	 * .ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		System.out.println("wtf");
		// TODO: this wrapping try is for debug only. remove later.
		try {
			/*
			 * In order to perform analyses on the selected code, there are few
			 * thing we need to collect first in order to configure the Soot
			 * framework environment.They are:
			 * 
			 * - Which ASTNodes are in the text selection
			 * 
			 * - The casspath entry to the package root of the text selection
			 * 
			 * - The method name which contains the text selection
			 * 
			 * - The ColoredSourceFile object of the text selection
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

			// used to compute the ASTNodes corresponding to the text selection
			ITextSelection textSelection = (ITextSelection) selection;

			// this visitor will compute the ASTNodes that were selected by the
			// user
			SelectionNodesVisitor selectionNodesVisitor = new SelectionNodesVisitor(textSelection);

			/*
			 * Now we need to create a compilation unit for the file, and then
			 * parse it to generate an AST in which we will perform our
			 * analyses.
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
			 * Some algorithms might need to compare some features related to
			 * ASTNodes. This is the only current implementation and it provides
			 * a way to query for features from CIDE.
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
			 * Instrumento in-memory Jimple code.
			 */
			FeatureModelInstrumentorTransformer instrumentorTransformer = new FeatureModelInstrumentorTransformer(extracter, correspondentClasspath);
			instrumentorTransformer.transform2(body, correspondentClasspath);
			FeatureTag bodyFeatureTag = (FeatureTag) body.getTag("FeatureTag");

			BriefUnitGraph bodyGraph = new BriefUnitGraph(body);
			LiftedUninitializedVariableAnalysis uv = new LiftedUninitializedVariableAnalysis(bodyGraph, bodyFeatureTag.getFeatures());

			/*
			 * TODO: Iterar sobre os resultados das análises e compilar as
			 * informações relevantes
			 */

			// Reset SOOT states and free resources
			G.v().reset();

		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return null;
	}
}

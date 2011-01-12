package br.ufal.cideei.handlers2;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.IntegerNameProvider;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import soot.Body;
import soot.G;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.toolkits.base.Aggregator;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.FlowSet;
import br.ufal.cideei.alg.coa.ChainContributionGraphAnalysis;
import br.ufal.cideei.features.CIDEFeatureExtracterFactory;
import br.ufal.cideei.features.IFeatureExtracter;
import br.ufal.cideei.soot.SootManager;
import br.ufal.cideei.soot.UnitUtil;
import br.ufal.cideei.soot.analyses.LiftedFlowSet;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;
import br.ufal.cideei.soot.instrument.FeatureModelInstrumentorTransformer;
import br.ufal.cideei.soot.instrument.FeatureTag;
import br.ufal.cideei.soot.instrument.asttounit.ASTNodeUnitBridge;
import br.ufal.cideei.util.MethodDeclarationSootMethodBridge;
import br.ufal.cideei.util.graph.VertexLineNameProvider;
import br.ufal.cideei.util.graph.VertexNameFilterProvider;
import br.ufal.cideei.util.graph.WeighEdgeNameProvider;
import br.ufal.cideei.visitors.SelectionNodesVisitor;

/**
 * Handler for the br.ufal.cideei.commands.DoCompute extension command.
 * 
 * @author Társis
 * 
 */
public class ReachingDefinitionsHandler extends AbstractHandler {

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
			 * TODO: Check the real uses of this.
			 */
			SootManager.runPacks(extracter);
			Map options = new HashMap();
			options.put("only-stack-locals", "true");
			options.put("enabled", "true");
			Aggregator.v().transform(body, "", options);

			/*
			 * Instrumento the Jimple in-memory code.
			 */
			FeatureModelInstrumentorTransformer instrumentorTransformer = FeatureModelInstrumentorTransformer.v(extracter, correspondentClasspath);
			instrumentorTransformer.transform2(body, correspondentClasspath);

			FeatureTag bodyFeatureTag = (FeatureTag) body.getTag("FeatureTag");

			/*
			 * Build CFG and run the analysis.
			 */
			BriefUnitGraph bodyGraph = new BriefUnitGraph(body);
			LiftedReachingDefinitions tst = new LiftedReachingDefinitions(bodyGraph, bodyFeatureTag.getFeatures());
			tst.execute();

			AssignStmt focus = null;
			for (Unit unit : unitsInSelection) {
				if (unit instanceof AssignStmt) {
					focus = (AssignStmt) unit;
				}
			}

			ChainContributionGraphAnalysis ccgAnalysis = new ChainContributionGraphAnalysis(bodyGraph, bodyFeatureTag.getFeatures(), focus);
			ccgAnalysis.execute();
			DefaultDirectedWeightedGraph<Unit, DefaultWeightedEdge> ccg = ccgAnalysis.getContributionChainGraph();
			System.out.println("CHAIN: " + ccgAnalysis.getFocusChain());

			try {
				String userHomeDir = System.getProperty("user.home");

				DOTExporter<Unit, DefaultWeightedEdge> completeExporter = new DOTExporter<Unit, DefaultWeightedEdge>(new VertexNameFilterProvider<Unit>(
						jdtCompilationUnit), null, new WeighEdgeNameProvider<DefaultWeightedEdge>(ccg));

				FileWriter fileWriterForCompleteExporter = new FileWriter(new File(userHomeDir + File.separator + "comp.dot"));
				completeExporter.export(fileWriterForCompleteExporter, ccg);
				fileWriterForCompleteExporter.close();

				DOTExporter<Unit, DefaultWeightedEdge> simplifiedExporter = new DOTExporter<Unit, DefaultWeightedEdge>(new VertexLineNameProvider<Unit>(
						jdtCompilationUnit), null, null);

				FileWriter fileWriterForSimplifiedExporter = new FileWriter(new File(userHomeDir + File.separator + "simp.dot"));
				simplifiedExporter.export(fileWriterForSimplifiedExporter, ccg);
				fileWriterForSimplifiedExporter.close();

				UnitUtil.serializeBody(body, userHomeDir + File.separator + "body.jimple");

			} catch (IOException e) {
				e.printStackTrace();
			}

			Map<Unit, Set<Unit>> providesMap = new HashMap<Unit, Set<Unit>>();

			/*
			 * debugging results
			 */
			Iterator<Unit> graphIterator = bodyGraph.iterator();
			String format = "|%1$-35s|%2$-30s|%3$-40s|\n";
			while (graphIterator.hasNext()) {
				Unit unit = (Unit) graphIterator.next();
				LiftedFlowSet flowAfter = tst.getFlowAfter(unit);

				System.out.format(format, unit, unit.getTag("FeatureTag"), flowAfter);
			}

			for (Unit unitFromSelection : unitsInSelection) {
				if (unitFromSelection instanceof DefinitionStmt) {
					DefinitionStmt definition = (DefinitionStmt) unitFromSelection;

					Iterator<Unit> iterator = body.getUnits().snapshotIterator();
					while (iterator.hasNext()) {
						Unit nextUnit = iterator.next();
						LiftedFlowSet<Collection<Set<Object>>> liftedFlowAfter = tst.getFlowAfter(nextUnit);
						Set<String>[] configurations = liftedFlowAfter.getConfigurations();
						FlowSet[] lattices = liftedFlowAfter.getLattices();
						for (int configurationIndex = 0; configurationIndex < configurations.length; configurationIndex++) {
							FlowSet flowSet = lattices[configurationIndex];
							if (flowSet.contains(definition)) {
								List<ValueBox> useBoxes = nextUnit.getUseBoxes();
								for (ValueBox vbox : useBoxes) {
									if (vbox.getValue().equivTo(definition.getLeftOp())) {
										Set<Unit> providesCol = providesMap.get(definition);
										if (providesCol == null) {
											providesCol = new HashSet<Unit>();
											providesCol.add(nextUnit);
											providesMap.put(definition, providesCol);
										} else {
											providesCol.add(nextUnit);
										}
									}
								}
							}
						}
					}
				}
			}
			System.out.println(providesMap);
			G.v().reset();

		} catch (Exception ex) {
			ex.printStackTrace();
			G.v().reset();
		}

		return null;
	}

	public void runTestReachingDefs(BriefUnitGraph bodyGraph, Collection<Set<String>> configs) {
		long liftedStart = System.currentTimeMillis();
		LiftedReachingDefinitions tst = new LiftedReachingDefinitions(bodyGraph, configs);
		long liftedEnd = System.currentTimeMillis();
		System.out.println("Lifted time: " + (liftedEnd - liftedStart));
		Iterator<Unit> iterator = bodyGraph.iterator();
		String format = "|%1$-35s|%2$-30s|%3$-40s|\n";
		while (iterator.hasNext()) {
			Unit unit = (Unit) iterator.next();
			LiftedFlowSet flowAfter = tst.getFlowAfter(unit);

			System.out.format(format, unit, unit.getTag("FeatureTag"), flowAfter);

		}
		/*
		 * UnitUtil.serializeGraph(bodyGraph.getBody(), null);
		 */
	}
}

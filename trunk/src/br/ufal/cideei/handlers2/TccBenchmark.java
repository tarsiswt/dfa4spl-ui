package br.ufal.cideei.handlers2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.grimp.Grimp;
import soot.grimp.GrimpBody;
import soot.jimple.DefinitionStmt;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.FlowSet;
import br.ufal.cideei.features.CIDEFeatureExtracterFactory;
import br.ufal.cideei.features.IFeatureExtracter;
import br.ufal.cideei.soot.SootManager;
import br.ufal.cideei.soot.UnitUtil;
import br.ufal.cideei.soot.analyses.LiftedFlowSet;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;
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
public class TccBenchmark extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// TODO: this wrapping try is for debug only. remove later.
		final int TIMES = 10;
		List<Long> runsTimer = new ArrayList<Long>(TIMES);
		for (int i = 0; i < TIMES; i++) {
			long startTimer = System.currentTimeMillis();
			try {
				ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);
				Shell shell = HandlerUtil.getActiveShellChecked(event);

				if (!(selection instanceof ITextSelection))
					throw new ExecutionException("Not a text selection");

				IFile textSelectionFile = (IFile) HandlerUtil.getActiveEditorChecked(event).getEditorInput().getAdapter(IFile.class);

				ITextSelection textSelection = (ITextSelection) selection;
				SelectionNodesVisitor selectionNodesVisitor = new SelectionNodesVisitor(textSelection);

				ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(textSelectionFile);
				ASTParser parser = ASTParser.newParser(AST.JLS3);
				parser.setSource(compilationUnit);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setResolveBindings(true);
				CompilationUnit jdtCompilationUnit = (CompilationUnit) parser.createAST(null);
				jdtCompilationUnit.accept(selectionNodesVisitor);

				Set<ASTNode> selectionNodes = selectionNodesVisitor.getNodes();
				System.out.println("Selection" + selectionNodes);

				IFeatureExtracter extracter = CIDEFeatureExtracterFactory.getInstance().newExtracter();

				String correspondentClasspath = MethodDeclarationSootMethodBridge.getCorrespondentClasspath(textSelectionFile);
				SootManager.configure(correspondentClasspath);
				MethodDeclaration methodDeclaration = MethodDeclarationSootMethodBridge.getParentMethod(selectionNodes.iterator().next());
				String declaringMethodClass = methodDeclaration.resolveBinding().getDeclaringClass().getQualifiedName();
				MethodDeclarationSootMethodBridge mdsm = new MethodDeclarationSootMethodBridge(methodDeclaration);
				SootMethod sootMethod = SootManager.getMethodBySignature(declaringMethodClass, mdsm.getSootMethodSubSignature());
				Body body = sootMethod.retrieveActiveBody();

				Collection<Unit> unitsInSelection = ASTNodeUnitBridge.getUnitsFromLines(ASTNodeUnitBridge.getLinesFromASTNodes(selectionNodes,
						jdtCompilationUnit), body);
				if (unitsInSelection.isEmpty()) {
					System.out.println("the selection doesn't map to any Soot Unit");
					return null;
				}

				FeatureModelInstrumentorTransformer instrumentorTransformer = FeatureModelInstrumentorTransformer.v(extracter, correspondentClasspath);
				instrumentorTransformer.transform2(body, correspondentClasspath);

				FeatureTag<Set<String>> bodyFeatureTag = (FeatureTag<Set<String>>) body.getTag("FeatureTag");

				BriefUnitGraph bodyGraph = new BriefUnitGraph(body);
				LiftedReachingDefinitions reachingDefinitions = new LiftedReachingDefinitions(bodyGraph, bodyFeatureTag.getFeatures());
				reachingDefinitions.execute();

				Map<Pair<Unit, Set<String>>, Set<Unit>> createProvidesConfigMap = createProvidesConfigMap(unitsInSelection, reachingDefinitions, body);
				System.out.println(createProvidesConfigMap);
				String message = createMessage(createProvidesConfigMap);

				// EmergentPopup.pop(shell, message);
			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				G.v().reset();
			}
			long estimatedTime = System.currentTimeMillis() - startTimer;
			runsTimer.add(estimatedTime);
		}
		System.out.println(runsTimer);
		return null;
	}

	private String createMessage(Map<Pair<Unit, Set<String>>, Set<Unit>> createProvidesConfigMap) {
		StringBuilder stringBuilder = new StringBuilder();
		boolean appendedConfiguration = false;
		for (Entry<Pair<Unit, Set<String>>, Set<Unit>> provideEntry : createProvidesConfigMap.entrySet()) {
			Pair<Unit, Set<String>> key = provideEntry.getKey();
			Unit definition = key.getFirst();
			FeatureTag definitionTag = (FeatureTag) definition.getTag("FeatureTag");
			Set<String> configuration = key.getSecond();

			Set<Unit> reachedUses = provideEntry.getValue();
			for (Unit reachedUnit : reachedUses) {
				FeatureTag reachedUnitTag = (FeatureTag) reachedUnit.getTag("FeatureTag");
				Set<String> difference = new HashSet<String>(reachedUnitTag);
				difference.removeAll(definitionTag);

				if (difference.size() == 0) {
					continue;
				}

				if (!appendedConfiguration) {
					stringBuilder.append("\n\n");
					stringBuilder.append(configuration);
					stringBuilder.append('\n');
					appendedConfiguration = true;
				}
				stringBuilder.append("Provides " + definition + " to\n");

				for (String feature : difference) {
					stringBuilder.append("line " + ASTNodeUnitBridge.getLineFromUnit(reachedUnit));
					stringBuilder.append(" [feature " + feature + "]\n");
				}
			}
			appendedConfiguration = false;
		}
		return stringBuilder.toString();
	}

	private Map<Pair<Unit, Set<String>>, Set<Unit>> createProvidesConfigMap(Collection<Unit> unitsInSelection, LiftedReachingDefinitions reachingDefinitions,
			Body body) {
		Map<Pair<Unit, Set<String>>, Set<Unit>> unitConfigurationMap = new HashMap<Pair<Unit, Set<String>>, Set<Unit>>();

		for (Unit unitFromSelection : unitsInSelection) {
			if (unitFromSelection instanceof DefinitionStmt) {
				/*
				 * exclude definitions when it's $temp on the leftOp.
				 */
				DefinitionStmt definition = (DefinitionStmt) unitFromSelection;
				Local leftOp = (Local) definition.getLeftOp();
				if (leftOp.getName().charAt(0) == '$') {
					continue;
				}

				System.out.println("Definition:" + definition);

				// for every unit in the body...
				Iterator<Unit> iterator = body.getUnits().snapshotIterator();
				while (iterator.hasNext()) {
					Unit nextUnit = iterator.next();
					LiftedFlowSet<Collection<Set<Object>>> liftedFlowAfter = reachingDefinitions.getFlowAfter(nextUnit);
					Set<String>[] configurations = liftedFlowAfter.getConfigurations();
					FlowSet[] lattices = liftedFlowAfter.getLattices();
					// and for every configuration...
					for (int configurationIndex = 0; configurationIndex < configurations.length; configurationIndex++) {
						FlowSet flowSet = lattices[configurationIndex];
						Set<String> currConfiguration = configurations[configurationIndex];
						FeatureTag nextUnitTag = (FeatureTag) nextUnit.getTag("FeatureTag");

						// if the unit belongs to the current configuration...
						if (nextUnitTag.belongsToConfiguration(currConfiguration)) {

							// if the definition reaches this unit...
							if (flowSet.contains(definition)) {
								List<ValueBox> useBoxes = nextUnit.getUseBoxes();
								for (ValueBox vbox : useBoxes) {
									/*
									 * and the definition is used, add to the
									 * map...
									 */
									if (vbox.getValue().equivTo(leftOp)) {
										Pair<Unit, Set<String>> currentPair = new Pair<Unit, Set<String>>(definition, currConfiguration);
										Set<Unit> unitConfigurationReachesSet = unitConfigurationMap.get(currentPair);

										if (unitConfigurationReachesSet == null) {
											unitConfigurationReachesSet = new HashSet<Unit>();
											unitConfigurationReachesSet.add(nextUnit);
											unitConfigurationMap.put(currentPair, unitConfigurationReachesSet);
										} else {
											unitConfigurationReachesSet.add(nextUnit);
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return unitConfigurationMap;
	}
}

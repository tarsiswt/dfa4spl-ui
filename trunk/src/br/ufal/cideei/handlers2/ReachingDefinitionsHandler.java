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
import java.util.TreeSet;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
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
import org.jgrapht.graph.DirectedMultigraph;

import soot.Body;
import soot.G;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.DefinitionStmt;
import soot.tagkit.SourceLnPosTag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.scalar.FlowSet;
import br.ufal.cideei.features.CIDEFeatureExtracterFactory;
import br.ufal.cideei.features.IFeatureExtracter;
import br.ufal.cideei.soot.SootManager;
import br.ufal.cideei.soot.analyses.LiftedFlowSet;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;
import br.ufal.cideei.soot.instrument.FeatureModelInstrumentorTransformer;
import br.ufal.cideei.soot.instrument.FeatureTag;
import br.ufal.cideei.soot.instrument.asttounit.ASTNodeUnitBridge;
import br.ufal.cideei.ui.FeatureMarkerCreator;
import br.ufal.cideei.ui.Location;
import br.ufal.cideei.util.ConfigurationEdgeFactory;
import br.ufal.cideei.util.ConfigurationEdgeNameProvider;
import br.ufal.cideei.util.MethodDeclarationSootMethodBridge;
import br.ufal.cideei.util.Pair;
import br.ufal.cideei.util.ValueContainerEdge;
import br.ufal.cideei.util.graph.VertexLineNameProvider;
import br.ufal.cideei.visitors.AllFeatureNodes;
import br.ufal.cideei.visitors.GetFeatureVisitor;
import br.ufal.cideei.visitors.SelectionNodesVisitor;

/**
 * Handler for the br.ufal.cideei.commands.DoCompute extension command.
 * 
 * @author Társis
 * 
 */
public class ReachingDefinitionsHandler extends AbstractHandler {

	private static TreeSet<Integer> lineNumbers = new TreeSet<Integer>();
	private DirectedMultigraph<Unit, ValueContainerEdge<Set<String>>> reachesData;

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

			/*
			 * Code that returns the interface to whole feature
			 */

			// Returns the set of features of line(s) selected
			GetFeatureVisitor getFeatureVisitor = new GetFeatureVisitor(textSelection, textSelectionFile);
			jdtCompilationUnit.accept(getFeatureVisitor);
			Set<String> features = getFeatureVisitor.getFeatures();

			// System.out.println("Features: "+features);

			// Returns all nodes corresponding to the set of features of
			// selection
			AllFeatureNodes allFeatureNodes = new AllFeatureNodes(textSelection, textSelectionFile, features);
			jdtCompilationUnit.accept(allFeatureNodes);

			// Set<ASTNode> selectionNodes = allFeatureNodes.getNodes();

			jdtCompilationUnit.accept(selectionNodesVisitor);
			Set<ASTNode> selectionNodes = selectionNodesVisitor.getNodes();

			for (ASTNode astNode : selectionNodes) {
				ReachingDefinitionsHandler.lineNumbers.add(jdtCompilationUnit.getLineNumber(astNode.getStartPosition()));
			}
			System.out.println("Selection" + selectionNodes);

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
			 * Instrumento in-memory Jimple code.
			 */
			FeatureModelInstrumentorTransformer instrumentorTransformer = FeatureModelInstrumentorTransformer.v(extracter, correspondentClasspath);
			instrumentorTransformer.transform2(body, correspondentClasspath);

			FeatureTag<Set<String>> bodyFeatureTag = (FeatureTag<Set<String>>) body.getTag("FeatureTag");

			/*
			 * Build CFG and run the analysis.
			 */
			BriefUnitGraph bodyGraph = new BriefUnitGraph(body);
			LiftedReachingDefinitions reachingDefinitions = new LiftedReachingDefinitions(bodyGraph, bodyFeatureTag.getFeatures());
			reachingDefinitions.execute();


			createProvidesGraph(unitsInSelection, reachingDefinitions, body);
			populateView(reachesData, textSelectionFile);
			
			/*
			 * TODO: This block generates the .dot file for the graph
			 * representing the analysis reasoning. This is for debuggin only;
			 * remove later.
			 */
			{
				DOTExporter<Unit, ValueContainerEdge<Set<String>>> exporter = new DOTExporter<Unit, ValueContainerEdge<Set<String>>>(
						new VertexLineNameProvider<Unit>(jdtCompilationUnit), null, new ConfigurationEdgeNameProvider<ValueContainerEdge<Set<String>>>());
				try {
					exporter.export(new FileWriter(System.getProperty("user.home") + File.separator + "REACHES DATA" + ".dot"), this.reachesData);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			G.v().reset();
		}

		return null;
	}

	private void populateView(DirectedMultigraph<Unit, ValueContainerEdge<Set<String>>> reachesData, IFile fileSelected) {
		try {
			fileSelected.deleteMarkers(FeatureMarkerCreator.FMARKER_ID, true, IResource.DEPTH_INFINITE);
		} catch (CoreException e) {
			e.printStackTrace();
		}

		Set<ValueContainerEdge<Set<String>>> edgeSet = reachesData.edgeSet();
		for (ValueContainerEdge<Set<String>> edge : edgeSet) {
			Set<String> configuration = edge.getValue();
			Unit unitInSelection = reachesData.getEdgeSource(edge);
			Unit reachedUnit = reachesData.getEdgeTarget(edge);

			Location loc = new Location();
			loc.setLineNumber(new Integer(ASTNodeUnitBridge.getLineFromUnit(reachedUnit)));
			loc.setFile(fileSelected);
			loc.setConfiguration("" + configuration);
			loc.setFeature("" + reachedUnit.getTag("FeatureTag").toString());

			FeatureMarkerCreator.createMarker("Provides " + unitInSelection + " to " + reachedUnit, loc);
		}
	}

	// TODO: extract *some* methods from this one.
	private Map<Pair<Unit, Set<String>>, Set<Unit>> createProvidesGraph(Collection<Unit> unitsInSelection, LiftedReachingDefinitions reachingDefinitions,
			Body body) {
		Map<Pair<Unit, Set<String>>, Set<Unit>> unitConfigurationMap = new HashMap<Pair<Unit, Set<String>>, Set<Unit>>();
		FeatureTag bodyFeatureTag = (FeatureTag) body.getTag("FeatureTag");

		this.reachesData = new DirectedMultigraph<Unit, ValueContainerEdge<Set<String>>>(ConfigurationEdgeFactory.getInstance());

		// for every unit in the selection...
		for (Unit unitFromSelection : unitsInSelection) {
			if (unitFromSelection instanceof DefinitionStmt) {
				/*
				 * exclude definitions when it's $temp on the leftOp.
				 */
				DefinitionStmt definition = (DefinitionStmt) unitFromSelection;
				Local leftOp = (Local) definition.getLeftOp();
				if (leftOp.getName().contains("$")) {
					continue;
				}

				Set<String> featuresThatUseDefinition = new HashSet<String>();

				// for every unit in the body...
				Iterator<Unit> iterator = body.getUnits().snapshotIterator();
				while (iterator.hasNext()) {
					Unit nextUnit = iterator.next();
					FeatureTag nextUnitTag = (FeatureTag) nextUnit.getTag("FeatureTag");

					List useAndDefBoxes = nextUnit.getUseAndDefBoxes();
					for (Object object : useAndDefBoxes) {
						ValueBox vbox = (ValueBox) object;
						if (vbox.getValue().equivTo(leftOp)) {
							featuresThatUseDefinition.addAll(nextUnitTag);
						}
					}

					LiftedFlowSet<Collection<Set<Object>>> liftedFlowAfter = reachingDefinitions.getFlowAfter(nextUnit);
					FlowSet[] lattices = liftedFlowAfter.getLattices();

					// and for every configuration...
					for (int latticeIndex = 0; latticeIndex < lattices.length; latticeIndex++) {
						FlowSet flowSet = lattices[latticeIndex];
						Set<String> currConfiguration = bodyFeatureTag.getConfigurationForId(latticeIndex);

						// if the unit belongs to the current configuration...
						if (nextUnitTag.belongsToConfiguration(currConfiguration)) {

							// if the definition reaches this unit...
							if (flowSet.contains(definition)) {
								List<ValueBox> useBoxes = nextUnit.getUseBoxes();
								for (ValueBox vbox : useBoxes) {
									/*
									 * and the definition is used, add to the
									 * map (graph)...
									 */
									if (vbox.getValue().equivTo(leftOp)) {
										Pair<Unit, Set<String>> currentPair = new Pair<Unit, Set<String>>(definition, currConfiguration);
										Set<Unit> unitConfigurationReachesSet = unitConfigurationMap.get(currentPair);

										if (!reachesData.containsVertex(definition)) {
											reachesData.addVertex(definition);
										}
										if (!reachesData.containsVertex(nextUnit)) {
											reachesData.addVertex(nextUnit);
										}

										Set<ValueContainerEdge<Set<String>>> allEdges = reachesData.getAllEdges(definition, nextUnit);
										if (allEdges.size() >= 1) {
											int diffCounter = 0;
											Iterator<ValueContainerEdge<Set<String>>> edgesIterator = allEdges.iterator();
											Set<ValueContainerEdge<Set<String>>> edgeRemovalSchedule = new HashSet<ValueContainerEdge<Set<String>>>();
											while (edgesIterator.hasNext()) {
												ValueContainerEdge<Set<String>> valueContainerEdge = (ValueContainerEdge<Set<String>>) edgesIterator.next();
												Set<String> valueConfiguration = valueContainerEdge.getValue();
												Integer idForConfiguration = 0;// bodyFeatureTag.getConfigurationForId(valueConfiguration);
												FlowSet flowSetFromOtherReached = lattices[idForConfiguration];
												if (flowSetFromOtherReached.equals(flowSet)) {
													/*
													 * Se a configuração que
													 * estiver "querendo" entrar
													 * for menor, então ela
													 * expulsará os maiores.
													 */
													if (valueConfiguration.size() > currConfiguration.size()
															&& featuresThatUseDefinition.containsAll(currConfiguration)) {
														edgeRemovalSchedule.add(valueContainerEdge);
														ValueContainerEdge<Set<String>> addEdge = reachesData.addEdge(definition, nextUnit);
														addEdge.setValue(currConfiguration);
														continue;
													}
												} else {
													diffCounter++;
												}
											}
											if (diffCounter == allEdges.size() && featuresThatUseDefinition.containsAll(currConfiguration)) {
												ValueContainerEdge<Set<String>> addEdge = reachesData.addEdge(definition, nextUnit);
												addEdge.setValue(currConfiguration);
											}
											reachesData.removeAllEdges(edgeRemovalSchedule);
										} else {
											ValueContainerEdge<Set<String>> addEdge = reachesData.addEdge(definition, nextUnit);
											addEdge.setValue(currConfiguration);
										}

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
				System.out.println("features that use the definition at issue: " + featuresThatUseDefinition);
			}
		}

		return unitConfigurationMap;
	}
}

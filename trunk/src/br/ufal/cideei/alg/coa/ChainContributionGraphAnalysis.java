package br.ufal.cideei.alg.coa;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jgrapht.graph.ClassBasedEdgeFactory;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.FlowSet;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;
import br.ufal.cideei.soot.instrument.asttounit.ASTNodeUnitBridge;

/**
 * This class utilizes the GEN sets of the Reaching definitions analysis to
 * generate a chain contribution graph as a subproduct. Additionally, to avoid
 * expensive graph iterations and pathing, a linked set @code{focusChain} is
 * generated. The first element in it is the focus. All the subsequent elements
 * are present in the graph and there is a path from focus to each of them.
 * 
 * @see LiftedReachingDefinitions;
 */
public class ChainContributionGraphAnalysis extends LiftedReachingDefinitions {

	/** The contribution chain graph. */
	private DefaultDirectedWeightedGraph<Unit, DefaultWeightedEdge> chainContributionGraph;

	/** The focus. */
	private AssignStmt focus;

	/** The focus chain. */
	private Set<AssignStmt> focusChain = new LinkedHashSet<AssignStmt>();

	/**
	 * Gets the chain contribution graph.
	 * 
	 * @return the contribution chain graph
	 */
	public DefaultDirectedWeightedGraph<Unit, DefaultWeightedEdge> getContributionChainGraph() {
		return chainContributionGraph;
	}

	/**
	 * Instantiates a new chain contribution graph transformer.
	 * 
	 * @param graph
	 *            the graph
	 * @param configurations
	 *            the configurations
	 * @param focus
	 *            the focus
	 */
	public ChainContributionGraphAnalysis(DirectedGraph<Unit> graph, Collection<Set<String>> configurations, AssignStmt focus) {
		super(graph, configurations);
		this.focus = focus;
		this.chainContributionGraph = new DefaultDirectedWeightedGraph<Unit, DefaultWeightedEdge>(new ClassBasedEdgeFactory<Unit, DefaultWeightedEdge>(
				DefaultWeightedEdge.class));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions#gen
	 * (soot.toolkits.scalar.FlowSet, soot.Unit, soot.toolkits.scalar.FlowSet)
	 */
	protected void gen(FlowSet source, Unit unit, FlowSet dest, Set<String> configuration) {
		super.gen(source, unit, dest, configuration);
		if (unit instanceof AssignStmt) {
			this.chainContributionGraph.addVertex(unit);

			AssignStmt assignment = (AssignStmt) unit;

			List<ValueBox> useBoxes = assignment.getRightOp().getUseBoxes();

			/*
			 * If the rlh operand has more than one boxes
			 */
			if (useBoxes.size() > 0) {
				for (ValueBox useBox : useBoxes) {
					Value value = useBox.getValue();
					List list = source.toList();
					for (Object objInSource : list) {
						if (objInSource instanceof AssignStmt) {
							AssignStmt assignmentInSource = (AssignStmt) objInSource;
							handleAssignment(assignmentInSource, value, assignment);
						}
					}
				}
			}
			/*
			 * If is this a simple assignment, e.g.: x = y, then the useboxes
			 * size is 0.
			 */
			else {
				Value value = assignment.getRightOp();
				List list = source.toList();
				for (Object objInSource : list) {
					if (objInSource instanceof AssignStmt) {
						AssignStmt assignmentInSource = (AssignStmt) objInSource;
						handleAssignment(assignmentInSource, value, assignment);
					}
				}
			}
		}
	}

	/**
	 * Handle assignment with respect to the chain contribution graph. Decides
	 * wether to include edges and vertices or not in the graph, and computes
	 * the focus chain.
	 * 
	 * @param assignmentInSource
	 *            the assignment in source
	 * @param rightOp
	 *            the right op
	 * @param unit
	 *            the unit
	 */
	private void handleAssignment(AssignStmt assignmentInSource, Value rightOp, AssignStmt unit) {
		if (assignmentInSource.getLeftOp().equivTo(rightOp)) {
			DefaultWeightedEdge edge = this.chainContributionGraph.addEdge(assignmentInSource, unit);
			if (edge != null) {
				if (ASTNodeUnitBridge.getLineFromUnit(assignmentInSource).equals(ASTNodeUnitBridge.getLineFromUnit(unit))) {
					this.chainContributionGraph.setEdgeWeight(edge, 0);
				} else {
					this.chainContributionGraph.setEdgeWeight(edge, 1);
				}
			}

			if (focusChain.contains(assignmentInSource) || assignmentInSource.equals(focus)) {
				focusChain.add(unit);
			}
		}
	}

	/**
	 * Gets the focus chain.
	 * 
	 * @return the focus chain
	 */
	public Collection<AssignStmt> getFocusChain() {
		return Collections.unmodifiableSet(focusChain);
	}
}

package br.ufal.cideei.alg.bfa;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.GotoStmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.FlowSet;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;

public class BrokenFlowAnalysis extends LiftedReachingDefinitions {

	/** The focus. */
	private AssignStmt focus;

	private Set<GotoStmt> focusedGotos;

	public Set<GotoStmt> getFocusedGotos() {
		return Collections.unmodifiableSet(focusedGotos);
	}

	private Map<GotoStmt, Set<AssignStmt>> gotoMap;

	public Map<GotoStmt, Set<AssignStmt>> getGotoMap() {
		return Collections.unmodifiableMap(gotoMap);
	}

	public BrokenFlowAnalysis(DirectedGraph<Unit> graph, Collection<Set<String>> configurations, AssignStmt focus) {
		super(graph, configurations);
		this.focus = focus;
		this.gotoMap = new HashMap<GotoStmt, Set<AssignStmt>>();
		this.focusedGotos = new HashSet<GotoStmt>();
	}

	protected void gen(FlowSet source, Unit unit, FlowSet dest, Set<String> configuration) {
		super.gen(source, unit, dest, configuration);
		if (unit instanceof GotoStmt) {
			GotoStmt gotoStmt = (GotoStmt) unit;
			Iterator destIterator = dest.iterator();
			Set<AssignStmt> setOfassignments = new HashSet<AssignStmt>(dest.toList());
			gotoMap.put(gotoStmt, setOfassignments);
			if (dest.contains(focus)) {
				focusedGotos.add(gotoStmt);
			}
		}
	}
}

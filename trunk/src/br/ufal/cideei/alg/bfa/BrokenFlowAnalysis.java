package br.ufal.cideei.alg.bfa;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.GotoStmt;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.FlowSet;
import br.ufal.cideei.soot.analyses.reachingdefs.LiftedReachingDefinitions;
import br.ufal.cideei.soot.instrument.FeatureTag;
import br.ufal.cideei.util.Pair;

public class BrokenFlowAnalysis extends LiftedReachingDefinitions {

	/** The focus. */
	private Set<AssignStmt> focuses;

	private Map<Pair<AssignStmt, Set<String>>, Set<GotoStmt>> configMap;

	public Map<Pair<AssignStmt, Set<String>>, Set<GotoStmt>> getConfigMap() {
		return configMap;
	}

	public BrokenFlowAnalysis(DirectedGraph<Unit> graph, Collection<Set<String>> configurations, Set<AssignStmt> focuses) {
		super(graph, configurations);
		this.focuses = focuses;
		configMap = new HashMap<Pair<AssignStmt, Set<String>>, Set<GotoStmt>>();
	}

	protected void gen(FlowSet source, Unit unit, FlowSet dest, Set<String> configuration) {
		super.gen(source, unit, dest, configuration);
		if (unit instanceof GotoStmt) {
			GotoStmt gotoStmt = (GotoStmt) unit;
			for (AssignStmt focus : focuses) {
				if (dest.contains(focus)) {
					Set<String> difference = new HashSet<String>((FeatureTag)gotoStmt.getTag("FeatureTag"));
					difference.removeAll((FeatureTag)focus.getTag("FeatureTag"));

					if (difference.size() == 0) {
						continue;
					}
					
					Set<GotoStmt> set = configMap.get(configuration);
					Pair<AssignStmt, Set<String>> pair = new Pair<AssignStmt, Set<String>>(focus,configuration);
					if (set == null) {
						set = new HashSet<GotoStmt>();
						set.add(gotoStmt);
						configMap.put(pair, set);
					} else {
						set.add(gotoStmt);
					}
				}
			}
		}
	}
}
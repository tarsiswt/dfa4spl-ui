package br.ufal.cideei.util;

import org.jgrapht.ext.EdgeNameProvider;

public class ConfigurationEdgeNameProvider<E extends ValueContainerEdge> implements EdgeNameProvider<E> {

	@Override
	public String getEdgeName(E edge) {
		return edge.getValue().toString();
	}

}

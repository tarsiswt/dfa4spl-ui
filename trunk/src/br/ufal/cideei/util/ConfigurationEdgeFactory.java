package br.ufal.cideei.util;

import java.util.Set;

import org.jgrapht.EdgeFactory;

import soot.Unit;

public class ConfigurationEdgeFactory implements EdgeFactory<Unit, ValueContainerEdge<Set<String>>> {

	static ConfigurationEdgeFactory instance = null;

	private ConfigurationEdgeFactory() {
	}

	public static ConfigurationEdgeFactory getInstance() {
		if (ConfigurationEdgeFactory.instance == null) {
			instance = new ConfigurationEdgeFactory();
		}
		return ConfigurationEdgeFactory.instance;
	}

	@Override
	public ValueContainerEdge<Set<String>> createEdge(Unit source, Unit target) {
		return new ValueContainerEdge<Set<String>>();
	}
}

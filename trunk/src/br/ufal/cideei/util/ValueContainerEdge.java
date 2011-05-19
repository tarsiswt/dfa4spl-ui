package br.ufal.cideei.util;

import org.jgrapht.graph.DefaultEdge;

public class ValueContainerEdge<S> extends DefaultEdge {

	private S value;

	public void setValue(S value) {
		this.value = value;
	}

	public S getValue() {
		return value;
	}
}

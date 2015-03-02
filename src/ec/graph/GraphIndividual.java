package ec.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ec.Individual;
import ec.simple.SimpleFitness;
import ec.util.Parameter;

public class GraphIndividual extends Individual {

	public Map<String, Node> nodeMap = new HashMap<String, Node>();
	public Map<String, Node> considerableNodeMap= new HashMap<String, Node>();
	public List<Edge> edgeList = new ArrayList<Edge>();
	public List<Edge> considerableEdgeList = new ArrayList<Edge>();
	public Set<Node> unused;
	public int longestPathLength;
	public int numAtomicServices;

	public GraphIndividual(){
		super();
		super.fitness = new SimpleFitness();
		super.species = new GraphSpecies();
	}

	public GraphIndividual(Set<Node> unused) {
		super();
		super.fitness = new SimpleFitness();
		super.species = new GraphSpecies();
		this.unused = unused;
	}

	@Override
	public Parameter defaultBase() {
		return new Parameter("graphindividual");
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof GraphIndividual) {
			return toString().equals(other.toString());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for(Edge e: edgeList) {
			builder.append(e);
			builder.append(" ");
		}
		return builder.toString();
	}

//	@Override
//	public GraphIndividual clone() {
//		GraphIndividual newG = new GraphIndividual();
//		newG.longestPathLength = longestPathLength;
//		newG.numAtomicServices = numAtomicServices;
//
//		return newG;
//	}

}

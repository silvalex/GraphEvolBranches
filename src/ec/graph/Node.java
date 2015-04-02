package ec.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ec.graph.taskNodes.TaskNode;

public class Node implements Cloneable {
	private List<Edge> incomingEdgeList = new ArrayList<Edge>();
	private List<Edge> outgoingEdgeList = new ArrayList<Edge>();
	private List<TaxonomyNode> taxonomyOutputs = new ArrayList<TaxonomyNode>();
	private List<TaxonomyNode> generalTaxonomyOutputs = new ArrayList<TaxonomyNode>();
	private List<TaxonomyNode> specificTaxonomyOutputs = new ArrayList<TaxonomyNode>();
	private String name;
	private String baseName;
	private double[] qos;
	private Set<String> inputs;
	private List<List<String>> outputPossibilities;
	private List<Float> probabilities;
	private boolean consider = true;
	private TaskNode taskNode;
	private String generalCondition;
	private String specificCondition;

	public Node(String name, String baseName, double[] qos, Set<String> inputs, List<List<String>> outputPossibilities, List<Float> probabilities) {
		this.name = name;
		this.baseName = baseName;
		this.qos = qos;
		this.inputs = inputs;
		this.outputPossibilities = outputPossibilities;
		this.probabilities = probabilities;
	}

	public Node(String name, String baseName, double[] qos, String generalCondition, String specificCondition, List<List<String>> outputPossibilities, List<Float> probabilities, TaskNode taskNode) {
		this.name = name;
		this.baseName = baseName;
		this.qos = qos;
		this.generalCondition = generalCondition;
		this.specificCondition = specificCondition;
		this.outputPossibilities = outputPossibilities;
		this.probabilities = probabilities;
		this.taskNode = taskNode;
	}

	public String getGeneralCondition() {
		return generalCondition;
	}

	public String getSpecificCondition() {
		return specificCondition;
	}

	public List<Edge> getIncomingEdgeList() {
		return incomingEdgeList;
	}

	public List<Edge> getOutgoingEdgeList() {
		return outgoingEdgeList;
	}

	public double[] getQos() {
		return qos;
	}

	public Set<String> getInputs() {
		return inputs;
	}

	public List<List<String>> getOutputPossibilities() {
		return outputPossibilities;
	}

	public List<Float> getProbabilities() {
		return probabilities;
	}

	public void setProbabilities(List<Float> probabilities) {
		this.probabilities = probabilities;
	}

	public String getName() {
		return name;
	}

	public String getBaseName() {
		return baseName;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Node clone() {
		// If it is not a conditional node
		if (generalCondition == null)
			return new Node(name, baseName, qos, inputs, outputPossibilities, probabilities);
		// Otherwise, it is a conditional node
		else {
			Node n = new Node(name, baseName, qos, generalCondition, specificCondition, outputPossibilities, probabilities, taskNode);
			n.taxonomyOutputs = taxonomyOutputs;
			n.generalTaxonomyOutputs = generalTaxonomyOutputs;
			n.specificTaxonomyOutputs = specificTaxonomyOutputs;
			return n;
		}
	}

	public List<TaxonomyNode> getTaxonomyOutputs() {
		return taxonomyOutputs;
	}

	public List<TaxonomyNode> getGeneralTaxonomyOutputs() {
		return generalTaxonomyOutputs;
	}

	public List<TaxonomyNode> getSpecificTaxonomyOutputs() {
		return specificTaxonomyOutputs;
	}

	public boolean isConsidered() {
		return consider;
	}

	public void setConsidered(boolean consider) {
		this.consider = consider;
	}

	@Override
	public String toString(){
		if (consider)
			return name;
		else
			return name + "*";
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Node) {
			Node o = (Node) other;
			return name.equals(o.name);
		}
		else
			return false;
	}

	public TaskNode getTaskNode() {
		return taskNode;
	}

	public void setTaskNode(TaskNode taskNode) {
		this.taskNode = taskNode;
	}
}

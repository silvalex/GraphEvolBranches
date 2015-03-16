package ec.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ec.EvolutionState;
import ec.Individual;
import ec.Species;
import ec.graph.taskNodes.ConditionNode;
import ec.graph.taskNodes.OutputNode;
import ec.graph.taskNodes.TaskNode;
import ec.util.Parameter;

public class GraphSpecies extends Species {
	@Override
	public Parameter defaultBase() {
		return new Parameter("graphspecies");
	}

	@Override
	public Individual newIndividual(EvolutionState state, int thread) {
		GraphIndividual ind = createNewBranchedGraph(null, state, ((GraphInitializer)state.initializer).taskTree);
		return ind;
	}

	public GraphIndividual createNewBranchedGraph(GraphIndividual mergedGraph, EvolutionState state, TaskNode taskNode) {
		// The first goal node is the child of the input node
		taskNode = taskNode.getChildren().get(0);

		GraphInitializer init = (GraphInitializer) state.initializer;
		Set<Node> unused = new HashSet<Node>(init.relevant);

		GraphIndividual newGraph = new GraphIndividual(unused);
		Node start = init.startNode.clone();

		Set<String> currentGoalInputs = new HashSet<String>();
		Map<String,Edge> connections = new HashMap<String,Edge>();

		// Connect start node
		connectCandidateToGraphByInputs(start, connections, newGraph, init, currentGoalInputs, null, "");

		Set<Node> seenNodes = new HashSet<Node>();
		Set<Node> relevant = init.relevant;
		List<Node> candidateList = new ArrayList<Node>();

		if (mergedGraph != null)
			addToCandidateListFromEdges(start, mergedGraph, seenNodes, candidateList);
		else
			addToCandidateList(start, seenNodes, relevant, candidateList, init, false, false);

		Collections.shuffle(candidateList, init.random);

		Set<String> allowedAncestors = new HashSet<String>();
		allowedAncestors.add(start.getName());

		finishConstructingBranchedGraph(taskNode, candidateList, connections, currentGoalInputs, init, newGraph, mergedGraph, seenNodes, relevant, allowedAncestors);

		return newGraph;

	}

	public void finishConstructingBranchedGraph(TaskNode taskNode,
			List<Node> candidateList, Map<String, Edge> connections,
			Set<String> currentGoalInputs, GraphInitializer init,
			GraphIndividual newGraph, GraphIndividual mergedGraph,
			Set<Node> seenNodes, Set<Node> relevant,
			Set<String> allowedAncestors) {

		boolean goalReached = false;
		Pair<Boolean, Node> goalCheckPair = null;

		// while goal node cannot be connected to graph
		while (!goalReached) {

			// Select node
			int index;

			candidateLoop: for (index = 0; index < candidateList.size(); index++) {
				Node candidate = candidateList.get(index).clone();

				// For all of the candidate inputs, check that there is a
				// service already in the graph
				// that can satisfy it
				connections.clear();

				for (String input : candidate.getInputs()) {
					boolean found = false;
					for (Node s : init.taxonomyMap.get(input).servicesWithOutput) {

						String suffix = "";
						if (!s.getName().equals("start")
								&& !s.getName().startsWith("cond")
								&& !s.getName().startsWith("end")) {

							suffix = "-" + taskNode.getCorrespondingNode().getName();
						}

						if (newGraph.considerableNodeMap.containsKey(s.getName() + suffix)
								&& allowedAncestors.contains(s.getName()
										+ suffix)) {
							Set<String> intersect = new HashSet<String>();
							intersect.add(input);

							Edge mapEdge = connections
									.get(s.getName() + suffix);
							if (mapEdge == null) {
								Edge e = new Edge(intersect);
								e.setFromNode(newGraph.nodeMap.get(s.getName()
										+ suffix));
								e.setToNode(candidate);
								connections.put(e.getFromNode().getName()
										+ suffix, e);
							} else
								mapEdge.getIntersect().addAll(intersect);

							found = true;
							break;
						}
					}

					// If that input cannot be satisfied, move on to another
					// candidate node to connect
					if (!found) {
						// Move on to another candidate
						continue candidateLoop;
					}
				}

				// Connect candidate to graph, adding its reachable services to
				// the candidate list
				goalCheckPair = connectCandidateToGraphByInputs(candidate,
						connections, newGraph, init, currentGoalInputs,
						taskNode, "-"
								+ taskNode.getCorrespondingNode().getName());
				goalReached = goalCheckPair.a;

				allowedAncestors.add(candidate.getName());
				if (mergedGraph != null)
					addToCandidateListFromEdges(candidate, mergedGraph,
							seenNodes, candidateList);
				else
					addToCandidateList(candidate, seenNodes, relevant,
							candidateList, init, false, false);

				break;
			}
			candidateList.remove(index);
			Collections.shuffle(candidateList, init.random);
		}

		// Connect end node to graph
		Node goal = taskNode.getCorrespondingNode().clone();
		connections.clear();

		if (taskNode instanceof ConditionNode) {
			// Set probabilities
			goal.setProbabilities(goalCheckPair.b.getProbabilities());

			// Connect node
			newGraph.nodeMap.put(goal.getName(), goal);
			newGraph.considerableNodeMap.put(goal.getName(), goal);
			Edge e = new Edge(new HashSet<String>());
			e.setFromNode(goalCheckPair.b);
			e.setToNode(goal);
			goalCheckPair.b.getOutgoingEdgeList().add(e);
			goal.getIncomingEdgeList().add(e);
			newGraph.edgeList.add(e);
			newGraph.considerableEdgeList.add(e);

			ConditionNode conditionNode = (ConditionNode) taskNode;
			allowedAncestors.add(goal.getName());
			Set<String> ifSeparateAncestors = new HashSet<String>(allowedAncestors);
			currentGoalInputs.clear();
			connections.clear();
			seenNodes.clear();

			// First create the if branch (i.e. specific branch)
			if (mergedGraph != null)
				addToCandidateListFromEdges(goal, mergedGraph, seenNodes,
						candidateList);
			else
				addToCandidateList(goal, seenNodes, relevant, candidateList,
						init, true, true);

			Collections.shuffle(candidateList, init.random);
			finishConstructingBranchedGraph(conditionNode.specificChild,
					candidateList, connections, currentGoalInputs, init,
					newGraph, mergedGraph, seenNodes, relevant,
					ifSeparateAncestors);

			// Now create the else branch (i.e. general branch)
			allowedAncestors.add(goal.getName());
			Set<String> elseSeparateAncestors = new HashSet<String>(allowedAncestors);
			currentGoalInputs.clear();
			connections.clear();
			seenNodes.clear();

			if (mergedGraph != null)
				addToCandidateListFromEdges(goal, mergedGraph, seenNodes,
						candidateList);
			else
				addToCandidateList(goal, seenNodes, relevant, candidateList,
						init, true, false);

			Collections.shuffle(candidateList, init.random);
			finishConstructingBranchedGraph(conditionNode.generalChild,
					candidateList, connections, currentGoalInputs, init,
					newGraph, mergedGraph, seenNodes, relevant,
					elseSeparateAncestors);

		} else {
			Set<Node> nodeSet = new HashSet<Node>(newGraph.nodeMap.values());
			for (Node s : nodeSet) {
				if (!currentGoalInputs.isEmpty()) {
					if (allowedAncestors.contains(s.getName())) {
						Set<String> intersection = new HashSet<String>();

						for (String o : s.getOutputPossibilities().get(0)) {

							Set<String> endNodeInputs = init.taxonomyMap.get(o).endNodeInputs
									.get(goal.getName());
							if (endNodeInputs != null
									&& !endNodeInputs.isEmpty()) {

								for (String i : endNodeInputs) {
									if (currentGoalInputs.contains(i)) {
										intersection.add(i);
										currentGoalInputs.remove(i);
									}
								}

							}
						}

						if (!intersection.isEmpty()) {
							Edge e = new Edge(intersection);
							e.setFromNode(s);
							e.setToNode(goal);
							connections.put(e.getFromNode().getName(), e);
						}
					}
					connectCandidateToGraphByInputs(goal, connections,
							newGraph, init, currentGoalInputs, null, "");
				}
			}
		}
		init.removeDanglingNodes(newGraph);
	}

	private void addToCandidateListFromEdges (Node n, GraphIndividual mergedGraph, Set<Node> seenNode, List<Node> candidateList) {
		seenNode.add(n);

		Node original = mergedGraph.nodeMap.get(n.getName());

		for (Edge e : original.getOutgoingEdgeList()) {
			// Add servicesWithInput from taxonomy node as potential candidates to be connected
			Node current = e.getToNode();
			if (!seenNode.contains(current)) {
				candidateList.add(current);
				seenNode.add(current);
			}
		}
	}

	public Pair<Boolean, Node> connectCandidateToGraphByInputs(Node candidate, Map<String,Edge> connections, GraphIndividual graph, GraphInitializer init, Set<String> currentGoalInputs, TaskNode taskNode, String suffix) {
		candidate.setName(candidate.getName() + suffix);
		graph.nodeMap.put(candidate.getName(), candidate);
		graph.considerableNodeMap.put(candidate.getName(), candidate);
		graph.edgeList.addAll(connections.values());
		graph.considerableEdgeList.addAll(connections.values());
		candidate.getIncomingEdgeList().addAll(connections.values());

		for (Edge e : connections.values()) {
			Node fromNode = graph.nodeMap.get(e.getFromNode().getName());
			fromNode.getOutgoingEdgeList().add(e);
		}


		graph.unused.remove(candidate);

		if (taskNode != null) {

			boolean isConditionalTask = taskNode instanceof ConditionNode;
			// Check if goal reached in case of condition
			if (isConditionalTask) {
				if (candidate.getOutputPossibilities().size() > 1) {

					ConditionNode condNode = (ConditionNode) taskNode;
					Node node = taskNode.getCorrespondingNode();
					Set<String> generalConds = new HashSet<String>();
					Set<String> specificConds = new HashSet<String>();

					for (String o : candidate.getOutputPossibilities().get(0)) {
						TaxonomyNode taxNode = init.taxonomyMap.get(o);
						Set<String> inputs = taxNode.condNodeGeneralInputs.get(node.getName());
						if (inputs != null)
							generalConds.addAll(inputs);
					}

					for (String o : candidate.getOutputPossibilities().get(1)) {
						TaxonomyNode taxNode = init.taxonomyMap.get(o);
						Set<String> inputs = taxNode.condNodeSpecificInputs.get(node.getName());
						if (inputs != null)
							specificConds.addAll(inputs);
					}

					return new Pair<Boolean, Node>(generalConds.contains(node.getGeneralCondition()) && specificConds.contains(node.getSpecificCondition()), candidate);
				}
				else
					return new Pair<Boolean, Node>(false, candidate);
			}
			// Check if goal reached in case of output node
			else {
				for (String o : candidate.getOutputPossibilities().get(0)) {
					TaxonomyNode taxNode = init.taxonomyMap.get(o);
					Set<String> outputs = taxNode.endNodeInputs.get(taskNode.getCorrespondingNode().getName());
					if (outputs != null)
						currentGoalInputs.addAll(outputs);
				}
				return new Pair<Boolean, Node>(currentGoalInputs.containsAll(taskNode.getCorrespondingNode().getInputs()), null);
			}
		}
		return new Pair<Boolean, Node>(false, null);
	}

	public void addToCandidateList(Node n, Set<Node> seenNode, Set<Node> relevant, List<Node> candidateList, GraphInitializer init, boolean isCond, boolean isIfBranch) {
		seenNode.add(n);
		List<TaxonomyNode> taxonomyOutputs;
		if (n.getName().equals("start"))
			taxonomyOutputs = init.startNode.getTaxonomyOutputs();
		else if (isCond) {
			candidateList.clear();
			if (isIfBranch) {
				taxonomyOutputs = n.getSpecificTaxonomyOutputs();
			}
			else
				taxonomyOutputs = n.getGeneralTaxonomyOutputs();
		}
		else
			taxonomyOutputs = init.serviceMap.get(n.getBaseName()).getTaxonomyOutputs();

		for (TaxonomyNode t : taxonomyOutputs) {
			// Add servicesWithInput from taxonomy node as potential candidates to be connected
			for (Node current : t.servicesWithInput) {
				if (!seenNode.contains(current) && relevant.contains(current)) {
					candidateList.add(current);
					seenNode.add(current);
				}
			}
		}
	}
}

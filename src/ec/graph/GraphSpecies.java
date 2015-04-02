package ec.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ec.EvolutionState;
import ec.Individual;
import ec.Species;
import ec.graph.taskNodes.ConditionNode;
import ec.graph.taskNodes.TaskNode;
import ec.util.Parameter;

public class GraphSpecies extends Species {

	@Override
	public Parameter defaultBase() {
		return new Parameter("graphspecies");
	}

	@Override
	public Individual newIndividual(EvolutionState state, int thread) {
		GraphIndividual ind = createNewBranchedGraph(null, state, ((GraphInitializer)state.initializer).taskTree, null);
		return ind;
	}

	public GraphIndividual createNewBranchedGraph(GraphIndividual mergedGraph, EvolutionState state, TaskNode taskNode, Map<String, List<Node>> baseToNodesMap) {
		// The first goal node is the child of the input node
		taskNode = taskNode.getChildren().get(0);

		GraphInitializer init = (GraphInitializer) state.initializer;

		GraphIndividual newGraph = new GraphIndividual();
		Node start = init.startNode.clone();

		Set<String> currentGoalInputs = new HashSet<String>();
		Map<String,Edge> connections = new HashMap<String,Edge>();

		// Connect start node
		connectCandidateToGraphByInputs(start, connections, newGraph, init, currentGoalInputs, null, "");

		Set<String> seenNodes = new HashSet<String>();
		Set<Node> relevant = init.relevant;
		List<Node> candidateList = new ArrayList<Node>();

		if (mergedGraph != null)
			addToCandidateListFromEdges(start, mergedGraph, seenNodes, candidateList, init, baseToNodesMap);
		else
			addToCandidateList(start, seenNodes, relevant, candidateList, init, false, false);

		Collections.shuffle(candidateList, init.random);

		Set<String> allowedAncestors = new HashSet<String>();
		allowedAncestors.add(start.getName());

		finishConstructingBranchedGraph(taskNode, candidateList, connections, currentGoalInputs, init, newGraph, mergedGraph, seenNodes, relevant, allowedAncestors, true, baseToNodesMap);

		return newGraph;

	}

	public void finishConstructingBranchedGraph(TaskNode taskNode,
			List<Node> candidateList, Map<String, Edge> connections,
			Set<String> currentGoalInputs, GraphInitializer init,
			GraphIndividual newGraph, GraphIndividual mergedGraph,
			Set<String> seenNodes, Set<Node> relevant,
			Set<String> allowedAncestors, boolean removeDangling, Map<String, List<Node>> baseToNodesMap) {

		boolean goalReached = false;

		Pair<Boolean, Node> goalCheckPair = null;

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

						String ancestor = s.getBaseName();
						if (!s.getName().equals("start")
								&& !s.getName().startsWith("cond")
								&& !s.getName().startsWith("end")) {
						    for (String a : allowedAncestors) {
						        if (a.startsWith( s.getBaseName() )) {
						            ancestor = a;
						            break;
						        }
						    }
						}
						if (newGraph.considerableNodeMap.containsKey(ancestor) && allowedAncestors.contains( ancestor )) {
							Set<String> intersect = new HashSet<String>();
							intersect.add(input);

                          Edge mapEdge = connections.get(ancestor);
							if (mapEdge == null) {
								Edge e = new Edge(intersect);
								e.setFromNode(newGraph.nodeMap.get(ancestor));
								e.setToNode(candidate);
                              connections.put(ancestor, e);
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
						taskNode, "_"
								+ taskNode.getCorrespondingNode().getName());
				goalReached = goalCheckPair.a;

				allowedAncestors.add(candidate.getName());
				if (mergedGraph != null)
					addToCandidateListFromEdges(candidate, mergedGraph,
							seenNodes, candidateList, init, baseToNodesMap);
				else
					addToCandidateList(candidate, seenNodes, relevant,
							candidateList, init, false, false);

				break;
			}

			if (index != candidateList.size()) {
				candidateList.remove(index);
				Collections.shuffle(candidateList, init.random);
			}
			else {
				break;
			}
		}

		// Connect end node to graph
		Node goal = taskNode.getCorrespondingNode().clone();
		connections.clear();

		if (taskNode instanceof ConditionNode) {
			if (goalCheckPair == null || goalCheckPair.b == null || goalCheckPair.b.getProbabilities().size() == 1) {

				Node node = taskNode.getCorrespondingNode();

				for (String ancestor : allowedAncestors) {
					Node candidate = newGraph.nodeMap.get(ancestor);

					if (candidate.getOutputPossibilities().size() > 1) {
						Set<String> generalConds = new HashSet<String>();
						Set<String> specificConds = new HashSet<String>();

						addToGoalInputs(candidate, generalConds, init, node.getName(), true, false);
						addToGoalInputs(candidate, specificConds, init, node.getName(), true, true);

						if (generalConds.contains(node.getGeneralCondition()) && specificConds.contains(node.getSpecificCondition())) {
							goalCheckPair = new Pair<Boolean, Node>(true, candidate);
							break;
						}
					}
				}
			}

			if (goalCheckPair == null || goalCheckPair.b == null || goalCheckPair.b.getProbabilities().size() == 1) { // XXX
				System.out.println("What the heck!");
			}

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
			Set<String> ifSeenNodes = new HashSet<String>(seenNodes);
			List<Node> ifCandidateList = new ArrayList<Node>(candidateList);

			currentGoalInputs.clear();

			// If next task is an output node, update currentGoal inputs using ancestors
			TaskNode node = conditionNode.specificChild;

			if (!(node instanceof ConditionNode)) {
				for (String ancestor : allowedAncestors) {
					Node candidate = null;
					for (String key : newGraph.nodeMap.keySet()) {
						if (key.startsWith(ancestor)) {
							candidate = newGraph.nodeMap.get(key);
							break;
						}
					}

					addToGoalInputs(candidate, currentGoalInputs, init, node.getCorrespondingNode().getName(), false, false);
				}
			}

			connections.clear();

			// First create the if branch (i.e. specific branch)
			if (mergedGraph != null)
				addToCandidateListFromEdges(goal, mergedGraph, ifSeenNodes,
						ifCandidateList, init, baseToNodesMap);
			else
				addToCandidateList(goal, ifSeenNodes, relevant, ifCandidateList,
						init, true, true);

			Collections.shuffle(ifCandidateList, init.random);
			finishConstructingBranchedGraph(conditionNode.specificChild,
					ifCandidateList, connections, currentGoalInputs, init,
					newGraph, mergedGraph, ifSeenNodes, relevant,
					ifSeparateAncestors, false, baseToNodesMap);

			// Now create the else branch (i.e. general branch)
			allowedAncestors.add(goal.getName());
			Set<String> elseSeparateAncestors = new HashSet<String>(allowedAncestors);
			Set<String> elseSeenNodes = new HashSet<String>(seenNodes);
			List<Node> elseCandidateList = new ArrayList<Node>(candidateList);

			currentGoalInputs.clear();

			// If next task is an output node, update currentGoal inputs using ancestors
			node = conditionNode.generalChild;

			if (!(node instanceof ConditionNode)) {
				for (String ancestor : allowedAncestors) {
					Node candidate = null;
					for (String key : newGraph.nodeMap.keySet()) {
						if (key.startsWith(ancestor)) {
							candidate = newGraph.nodeMap.get(key);
							break;
						}
					}

					addToGoalInputs(candidate, currentGoalInputs, init, node.getCorrespondingNode().getName(), false, false);
				}
			}

			connections.clear();

			if (mergedGraph != null)
				addToCandidateListFromEdges(goal, mergedGraph, elseSeenNodes,
				elseCandidateList, init, baseToNodesMap);
			else
				addToCandidateList(goal, elseSeenNodes, relevant, elseCandidateList,
						init, true, false);

			Collections.shuffle(elseCandidateList, init.random);
			finishConstructingBranchedGraph(conditionNode.generalChild,
			elseCandidateList, connections, currentGoalInputs, init,
					newGraph, mergedGraph, elseSeenNodes, relevant,
					elseSeparateAncestors, false, baseToNodesMap);

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
				}
			}
			connectCandidateToGraphByInputs(goal, connections,
					newGraph, init, currentGoalInputs, null, "");
		}

		if (removeDangling) {
		    init.removeDanglingNodes(newGraph);
		}
	}

	public Pair<Boolean, Node> connectCandidateToGraphByInputs(Node candidate, Map<String,Edge> connections, GraphIndividual graph, GraphInitializer init, Set<String> currentGoalInputs, TaskNode taskNode, String suffix) {
		candidate.setName(candidate.getBaseName() + suffix);

		graph.nodeMap.put(candidate.getName(), candidate);
		graph.considerableNodeMap.put(candidate.getName(), candidate);
		graph.edgeList.addAll(connections.values());
		graph.considerableEdgeList.addAll(connections.values());
		candidate.getIncomingEdgeList().addAll(connections.values());

		for (Edge e : connections.values()) {
			Node fromNode = graph.nodeMap.get(e.getFromNode().getName());
			fromNode.getOutgoingEdgeList().add(e);
		}

		if (taskNode != null) {

			boolean isConditionalTask = taskNode instanceof ConditionNode;
			// Check if goal reached in case of condition
			if (isConditionalTask) {
				if (candidate.getProbabilities().size() > 1) { // XXX

					Node node = taskNode.getCorrespondingNode();
					Set<String> generalConds = new HashSet<String>();
					Set<String> specificConds = new HashSet<String>();

					addToGoalInputs(candidate, generalConds, init, node.getName(), true, false);
					addToGoalInputs(candidate, specificConds, init, node.getName(), true, true);

					return new Pair<Boolean, Node>(generalConds.contains(node.getGeneralCondition()) && specificConds.contains(node.getSpecificCondition()), candidate);
				}
				else {
					return new Pair<Boolean, Node>(false, candidate);
				}
			}
			// Check if goal reached in case of output node
			else {
				addToGoalInputs(candidate, currentGoalInputs, init, taskNode.getCorrespondingNode().getName(), false, false);

				return new Pair<Boolean, Node>(currentGoalInputs.containsAll(taskNode.getCorrespondingNode().getInputs()), null);
			}
		}

		return new Pair<Boolean, Node>(false, null);
	}

	public void addToGoalInputs(Node candidate, Set<String> goalInputs, GraphInitializer init, String taskName, boolean isConditionalGoal, boolean isIfBranch) {
		if (candidate == null)
			return;

		if (isConditionalGoal) {
			if (isIfBranch) {
				for (String o : candidate.getOutputPossibilities().get(1)) {
					TaxonomyNode taxNode = init.taxonomyMap.get(o);
					Set<String> inputs = taxNode.condNodeSpecificInputs.get(taskName);
					if (inputs != null)
						goalInputs.addAll(inputs);
				}
			}
			else {
				for (String o : candidate.getOutputPossibilities().get(0)) {
					TaxonomyNode taxNode = init.taxonomyMap.get(o);
					Set<String> inputs = taxNode.condNodeGeneralInputs.get(taskName);
					if (inputs != null)
						goalInputs.addAll(inputs);
				}
			}
		}
		else {
			for (String o : candidate.getOutputPossibilities().get(0)) {
				TaxonomyNode taxNode = init.taxonomyMap.get(o);
				Set<String> outputs = taxNode.endNodeInputs.get(taskName);
				if (outputs != null)
					goalInputs.addAll(outputs);
			}
		}
	}

	public void addToCandidateList(Node n, Set<String> seenNode, Set<Node> relevant, List<Node> candidateList, GraphInitializer init, boolean isCond, boolean isIfBranch) {
		seenNode.add(n.getBaseName());
		List<TaxonomyNode> taxonomyOutputs;
		if (n.getName().equals("start"))
			taxonomyOutputs = init.startNode.getTaxonomyOutputs();
		else if (isCond) {
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
				if (!seenNode.contains(current.getBaseName()) && relevant.contains(current)) {
					candidateList.add(current);
					seenNode.add(current.getBaseName());
				}
			}
		}
	}

	private void addToCandidateListFromEdges (Node n, GraphIndividual mergedGraph, Set<String> seenNode, List<Node> candidateList, GraphInitializer init, Map<String, List<Node>> baseToNodesMap) {
		seenNode.add(n.getBaseName());

		List<Node> originalNodes = baseToNodesMap.get(n.getBaseName());

		for (Node original : originalNodes) {
			for (Edge e : original.getOutgoingEdgeList()) {
				Node current = e.getToNode();
				if (!seenNode.contains(current.getBaseName()) && !current.getName().startsWith("cond") && !current.getName().startsWith("end")) {
					candidateList.add(current);
					seenNode.add(current.getBaseName());
				}
			}
		}
	}

	//==========================================================================================================================
		//                                                 Debugging Routines
		//==========================================================================================================================

	    public static void structureValidator( GraphIndividual graph ) {
	        for ( Edge e : graph.edgeList ) {
	            //Node fromNode = e.getFromNode();
	            Node fromNode = graph.nodeMap.get( e.getFromNode().getName());

	            boolean isContained = false;
	            for ( Edge outEdge : fromNode.getOutgoingEdgeList() ) {
	                if ( e == outEdge ) {
	                    isContained = true;
	                    break;
	                }
	            }

	            if ( !isContained ) {
	                System.out.println( "Outgoing edge for node " + fromNode.getName() + " not detected." );
	            }

	            //Node toNode = e.getToNode();
	            Node toNode = graph.nodeMap.get( e.getToNode().getName());

	            isContained = false;
	            for ( Edge inEdge : toNode.getIncomingEdgeList() ) {
	                if ( e == inEdge ) {
	                    isContained = true;
	                    break;
	                }
	            }

	            if ( !isContained ) {
	                System.out.println( "Incoming edge for node " + toNode.getName() + " not detected." );
	            }
	        }
	        System.out.println("************************************");
	    }

	    public static void structureValidator2( GraphIndividual graph ) {
	        for ( Edge e : graph.considerableEdgeList ) {
	            Node fromNode = graph.considerableNodeMap.get( e.getFromNode().getName());

	            boolean isContained = false;
	            for ( Edge outEdge : fromNode.getOutgoingEdgeList() ) {
	                if ( e == outEdge ) {
	                    isContained = true;
	                    break;
	                }
	            }

	            if ( !isContained ) {
	                System.out.println( "Considerable: Outgoing edge for node " + fromNode.getName() + " not detected." );
	            }

	            Node toNode = graph.considerableNodeMap.get( e.getToNode().getName());

	            isContained = false;
	            for ( Edge inEdge : toNode.getIncomingEdgeList() ) {
	                if ( e == inEdge ) {
	                    isContained = true;
	                    break;
	                }
	            }

	            if ( !isContained ) {
	                System.out.println( "Considerable: Incoming edge for node " + toNode.getName() + " not detected." );
	            }
	        }
	        System.out.println("-----------------------------------------------");
	    }

	    public static boolean hasConditionalNodes(GraphIndividual newG) {
		    boolean hasCond = false;
		    for (String s : newG.nodeMap.keySet()) {
		    	if (s.startsWith("cond")) {
		    		hasCond = true;
		    		break;
		    	}
		    }
		    return hasCond;
	    }
}

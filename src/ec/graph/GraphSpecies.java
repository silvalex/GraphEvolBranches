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
import ec.util.Parameter;

public class GraphSpecies extends Species {
	@Override
	public Parameter defaultBase() {
		return new Parameter("graphspecies");
	}

	@Override
	public Individual newIndividual(EvolutionState state, int thread) {
		return createNewGraph(null, state);
	}

	public GraphIndividual createNewGraph(GraphIndividual mergedGraph, EvolutionState state) {
		GraphInitializer init = (GraphInitializer) state.initializer;
		Set<Node> unused = new HashSet<Node>(init.relevant);

		GraphIndividual newGraph = new GraphIndividual(unused);
		Node start = init.startNode.clone();
		Node end   = init.endNode.clone();

		Set<String> currentEndInputs = new HashSet<String>();
		Map<String,Edge> connections = new HashMap<String,Edge>();

		// Connect start node
		connectCandidateToGraphByInputs(start, connections, newGraph, currentEndInputs, init);

		Set<Node> seenNodes = new HashSet<Node>();
		Set<Node> relevant = init.relevant;
		List<Node> candidateList = new ArrayList<Node>();

		if (mergedGraph != null)
			addToCandidateListFromEdges(start, mergedGraph, seenNodes, candidateList);
		else
			addToCandidateList(start, seenNodes, relevant, candidateList, init);

		Collections.shuffle(candidateList, init.random);

		finishConstructingGraph(currentEndInputs, end, candidateList, connections, init, newGraph, mergedGraph, seenNodes, relevant);

		return newGraph;
	}

	public void finishConstructingGraph(Set<String> currentEndInputs, Node end, List<Node> candidateList, Map<String,Edge> connections,
	        GraphInitializer init, GraphIndividual newGraph, GraphIndividual mergedGraph, Set<Node> seenNodes, Set<Node> relevant) {
	 // While end cannot be connected to graph
        while(!currentEndInputs.containsAll(end.getInputs())) {

            // Select node
            int index;

            candidateLoop:
            for (index = 0; index < candidateList.size(); index++) {
                Node candidate = candidateList.get(index).clone();
                // For all of the candidate inputs, check that there is a service already in the graph
                // that can satisfy it
                connections.clear();

                for (String input : candidate.getInputs()) {
                    boolean found = false;
                    //Collections.shuffle( init.taxonomyMap.get( input ).servicesWithOutput, init.random ); //XXX
                     for (Node s : init.taxonomyMap.get(input).servicesWithOutput) {
                         if (newGraph.nodeMap.containsKey(s.getName())) {
                             Set<String> intersect = new HashSet<String>();
                             intersect.add(input);

                             Edge mapEdge = connections.get(s.getName());
                             if (mapEdge == null) {
                                 Edge e = new Edge(intersect);
                                 e.setFromNode(newGraph.nodeMap.get(s.getName()));
                                 e.setToNode(candidate);
                                 connections.put(e.getFromNode().getName(), e);
                             }
                             else
                                 mapEdge.getIntersect().addAll(intersect);

                             found = true;
                             break;
                         }
                     }
                     // If that input cannot be satisfied, move on to another candidate node to connect
                     if (!found) {
                         // Move on to another candidate
                         continue candidateLoop;
                     }
                }

                // Connect candidate to graph, adding its reachable services to the candidate list
                connectCandidateToGraphByInputs(candidate, connections, newGraph, currentEndInputs, init);
                if (mergedGraph != null)
                    addToCandidateListFromEdges(candidate, mergedGraph, seenNodes, candidateList);
                else
                    addToCandidateList(candidate, seenNodes, relevant, candidateList, init);

                break;
            }

            candidateList.remove(index);
            Collections.shuffle(candidateList, init.random);
        }

        // Connect end node to graph
        connections.clear();
        Iterator<Node> it = newGraph.nodeMap.values().iterator();
        Node s;

        while (!currentEndInputs.isEmpty() && it.hasNext()) {
            s = it.next();

            Set<String> intersection = new HashSet<String>();

            for (String o : s.getOutputs()) {

                Set<String> endNodeInputs = init.taxonomyMap.get(o).endNodeInputs;
                if (!endNodeInputs.isEmpty()) {

                    for (String i : endNodeInputs) {
                        if (currentEndInputs.contains(i)) {
                            intersection.add(i);
                            currentEndInputs.remove(i);
                        }
                    }

                }
            }

            if (!intersection.isEmpty()) {
                Edge e = new Edge(intersection);
                e.setFromNode(s);
                e.setToNode(end);
                connections.put(e.getFromNode().getName(), e);
            }
        }
        connectCandidateToGraphByInputs(end, connections, newGraph, currentEndInputs, init);
        init.removeDanglingNodes(newGraph);
        if (newGraph.nodeMap.size() == 1) {
            int ieee = 1;
        }
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

	public void connectCandidateToGraphByInputs(Node candidate, Map<String,Edge> connections, GraphIndividual graph, Set<String> currentEndInputs, GraphInitializer init) {

		graph.nodeMap.put(candidate.getName(), candidate);
		graph.considerableNodeMap.put(candidate.getName(), candidate);
		graph.edgeList.addAll(connections.values());
		graph.considerableEdgeList.addAll(connections.values());
		candidate.getIncomingEdgeList().addAll(connections.values());

		for (Edge e : connections.values()) {
			Node fromNode = graph.nodeMap.get(e.getFromNode().getName());
			fromNode.getOutgoingEdgeList().add(e);
		}
		for (String o : candidate.getOutputs()) {
			currentEndInputs.addAll(init.taxonomyMap.get(o).endNodeInputs);
		}
		graph.unused.remove(candidate);
	}

	public void appendCandidateToGraphByInputs(Node candidate, Map<String,Edge> connections, GraphIndividual graph) {
		graph.nodeMap.put(candidate.getName(), candidate);
		graph.edgeList.addAll(connections.values());
		candidate.getIncomingEdgeList().addAll(connections.values());

		for (Edge e : connections.values()) {
			Node fromNode = graph.nodeMap.get(e.getFromNode().getName());
			fromNode.getOutgoingEdgeList().add(e);
		}
		graph.unused.remove(candidate);
	}

	public void appendCandidateToGraphByOutputs(Node candidate, Map<String,Edge> connections, GraphIndividual graph) {
		graph.nodeMap.put(candidate.getName(), candidate);
		graph.edgeList.addAll(connections.values());
		candidate.getOutgoingEdgeList().addAll(connections.values());

		for (Edge e : connections.values()) {
			Node toNode = graph.nodeMap.get(e.getToNode().getName());
			toNode.getIncomingEdgeList().add(e);
		}
		graph.unused.remove(candidate);
	}

	public void addToCandidateList(Node n, Set<Node> seenNode, Set<Node> relevant, List<Node> candidateList, GraphInitializer init) {
		seenNode.add(n);
		List<TaxonomyNode> taxonomyOutputs;
		if (n.getName().equals("start"))
			taxonomyOutputs = init.startNode.getTaxonomyOutputs();
		else
			taxonomyOutputs = init.serviceMap.get(n.getName()).getTaxonomyOutputs();

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

	//==========================================================================================================================
	//                                                 Debugging Routines
	//==========================================================================================================================

    public void structureValidator( GraphIndividual graph ) {
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

    public void structureValidator2( GraphIndividual graph ) {
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
}

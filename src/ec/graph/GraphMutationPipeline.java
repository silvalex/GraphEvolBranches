package ec.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import ec.BreedingPipeline;
import ec.EvolutionState;
import ec.Individual;
import ec.graph.taskNodes.ConditionNode;
import ec.graph.taskNodes.TaskNode;
import ec.util.Parameter;

public class GraphMutationPipeline extends BreedingPipeline {

	@Override
	public Parameter defaultBase() {
		return new Parameter("graphappendpipeline");
	}

	@Override
	public int numSources() {
		return 1;
	}

	@Override
	public int produce(int min, int max, int start, int subpopulation,
			Individual[] inds, EvolutionState state, int thread) {

		GraphInitializer init = (GraphInitializer) state.initializer;

		int n = sources[0].produce(min, max, start, subpopulation, inds, state, thread);

        if (!(sources[0] instanceof BreedingPipeline)) {
            for(int q=start;q<n+start;q++)
                inds[q] = (Individual)(inds[q].clone());
        }

        if (!(inds[start] instanceof GraphIndividual))
            // uh oh, wrong kind of individual
            state.output.fatal("GraphAppendPipeline didn't get a GraphIndividual. The offending individual is in subpopulation "
            + subpopulation + " and it's:" + inds[start]);

        // Perform mutation
        for(int q=start;q<n+start;q++) {
            GraphIndividual graph = (GraphIndividual)inds[q];
            String originalGraph = graph.toString();

            GraphSpecies species = (GraphSpecies) graph.species;
            Object[] nodes = graph.nodeMap.values().toArray();

            // Select node from which to perform mutation
            Node selected = null;
            while (selected == null) {
                Node temp = (Node) nodes[init.random.nextInt( nodes.length )];
                if (!temp.getName().startsWith( "end" ) && !temp.getName().startsWith("cond")) {
                    if (temp.getName().equals( "start" ))
                        selected = temp;
                    else {
                        String goalName = temp.getName().split( "_" )[1];
                        Node goal = graph.nodeMap.get( goalName );
                        if (hasPath(temp, goal, graph))
                            selected = temp;
                    }
                }
            }

            if (selected.getName().equals( "start" )) {
                // Create an entirely new graph
                graph = species.createNewBranchedGraph( null, state, init.taskTree, null );
            }
            else {

            	String selectedName = selected.getName();
                TaskNode taskNode = null;

                // If the selected node is a service
                if (selectedName.contains("_")) {
                	String[] tokens = selectedName.split("_");
                	String name = tokens[tokens.length - 1];

                	if (name.startsWith("end")) {
                		taskNode = retrieveTaskNode(init.endNodes, name);
                	}
                	else {
                		taskNode = retrieveTaskNode(init.condNodes, name);
                	}
                }
                // Else, if the selected node is a condition
                else {
                	taskNode = retrieveTaskNode(init.condNodes, selectedName);
                }

                // Find all nodes that should be removed based on edge connections
                Set<Node> nodesToRemove = findNodesToRemove(selected);

                // Now mark obliteration of nodes that are satisfying goals after current goal
                Set<String> suffixesToDelete = new HashSet<String>();

                Queue<TaskNode> deleteQueue = new LinkedList<TaskNode>();

                if (taskNode.getChildren() != null) {
	                for (TaskNode tn : taskNode.getChildren())
	                	deleteQueue.offer(tn);
                }

                while (!deleteQueue.isEmpty()) {
                	TaskNode current = deleteQueue.poll();
                	suffixesToDelete.add(current.getCorrespondingNode().getName());

                	if (current.getChildren() != null) {
    	                for (TaskNode tn : current.getChildren())
    	                	deleteQueue.offer(tn);
                    }
                }

                if (!suffixesToDelete.isEmpty()) {
                	for (String nodeName : graph.nodeMap.keySet()){
                		String[] tokens = nodeName.split("_");
                		String suffix = null;

                		if (tokens.length > 1) {
                			suffix = tokens[tokens.length-1];
                		}
                		else {
                			suffix = tokens[0];
                		}

                		if (suffixesToDelete.contains(suffix)) {
                			nodesToRemove.add(graph.nodeMap.get(nodeName));
                		}
                	}
                }



                Set<Edge> edgesToRemove = new HashSet<Edge>();

                // Remove nodes and edges
                for (Node node : nodesToRemove) {
                    graph.nodeMap.remove( node.getName() );
                    graph.considerableNodeMap.remove( node.getName() );

                    for (Edge e : node.getIncomingEdgeList()) {
                        edgesToRemove.add( e );
                        e.getFromNode().getOutgoingEdgeList().remove( e );
                    }
                    for (Edge e : node.getOutgoingEdgeList()) {
                        edgesToRemove.add( e );
                        e.getToNode().getIncomingEdgeList().remove( e );
                    }
                }

                for (Edge edge : edgesToRemove) {
                    graph.edgeList.remove( edge );
                    graph.considerableEdgeList.remove( edge );
                }

                // Create data structures
                Set<Node> relevant = init.relevant;

                Set<String> currentGoalInputs = new HashSet<String>();

                Set<String> seenNodes = new HashSet<String>();
                List<Node> candidateList = new ArrayList<Node>();
                Set<String> allowedAncestors = new HashSet<String>();

                // Must add all nodes as seen before adding candidate list entries. Do this by navigating the graph backwards, from the selected nodes
                Set<String> seenSuffix = new HashSet<String>();
                TaskNode currTask = taskNode;
                while (currTask != null) {
                	seenSuffix.add(currTask.getCorrespondingNode().getName());
                	currTask = currTask.getParent();
                }

                for (Node node : graph.nodeMap.values()) {
                	String[] nameTokens = node.getName().split("_");
                	String nameTag;
                	if (nameTokens.length > 1) {
                		nameTag = nameTokens[nameTokens.length - 1];
                	}
                	else
                		nameTag = nameTokens[0];

                	if (seenSuffix.contains(nameTag)) {
                		seenNodes.add(node.getBaseName());
                		allowedAncestors.add(node.getName());
                	}
                }

                // Need to run through the tree again to add candidates to the candidate list (must be done after all all seen nodes were recorded)
                // Not only tree, but any node with the allowed suffixes XXX

                for (Node node : graph.nodeMap.values()) {
                	String[] nameTokens = node.getName().split("_");
                	String nameTag;
                	if (nameTokens.length > 1) {
                		nameTag = nameTokens[nameTokens.length - 1];
                	}
                	else
                		nameTag = nameTokens[0];

                	if (seenSuffix.contains(nameTag)) {
                		if (!node.getName().startsWith( "end" )) {

                    		boolean isCond = false;
                    		boolean isIfBranch = false;
                    		if (node.getName().startsWith("cond")) {
                    			isCond = true;
                    			isIfBranch = determineWhetherIfBranch(node.getTaskNode(), taskNode);
                    		}
                            species.addToCandidateList( node, seenNodes, relevant, candidateList, init, isCond, isIfBranch);
                    	}
                	}
                }



//                Queue<Node> queue = new LinkedList<Node>();
//
//                for (Edge e : selected.getIncomingEdgeList()) {
//                	queue.add(e.getFromNode());
//                }
//
//                while (!queue.isEmpty()) {
//                	Node node = queue.poll();
//
//                	if (!node.getName().startsWith( "end" )) {
//
//                		boolean isCond = false;
//                		boolean isIfBranch = false;
//                		if (node.getName().startsWith("cond")) {
//                			isCond = true;
//                			isIfBranch = determineWhetherIfBranch(node.getTaskNode(), taskNode);
//                		}
//                        species.addToCandidateList( node, seenNodes, relevant, candidateList, init, isCond, isIfBranch);
//                	}
//                	for (Edge e : node.getIncomingEdgeList())
//                		queue.add(e.getFromNode());
//                }

                Collections.shuffle(candidateList, init.random);
                Map<String,Edge> connections = new HashMap<String,Edge>();

    			if (!(taskNode instanceof ConditionNode)) {

    				for (String key : graph.nodeMap.keySet()) {

    					Node candidate = null;

    					if (!key.startsWith("end") && allowedAncestors.contains(key)) {
    						candidate = graph.nodeMap.get(key);
    					}

    					if (candidate != null)
    						species.addToGoalInputs(candidate, currentGoalInputs, init, taskNode.getCorrespondingNode().getName(), false, false);
    				}
    			}

                // Continue constructing graph
    			Set<String> originalGoalInputs = new HashSet<String>(currentGoalInputs);
                species.finishConstructingBranchedGraph(taskNode, candidateList, connections, currentGoalInputs, init, graph, null, seenNodes, relevant, allowedAncestors, true, null);
            }
            graph.evaluated=false;
        }
        return n;
	}

	private Set<Node> findNodesToRemove(Node selected) {
	    Set<Node> nodes = new HashSet<Node>();
	    _findNodesToRemove(selected, nodes);
	    return nodes;

	}

	private void _findNodesToRemove(Node current, Set<Node> nodes) {
        nodes.add( current );
        for (Edge e: current.getOutgoingEdgeList()) {
            _findNodesToRemove(e.getToNode(), nodes);
        }
	}

	private TaskNode retrieveTaskNode(List<Node> nodes, String name) {
		TaskNode taskNode = null;
		for (Node e : nodes) {
    		if (e.getName().equals(name)) {
    			taskNode = e.getTaskNode();
    			break;
    		}
    	}
		return taskNode;
	}

	private boolean determineWhetherIfBranch(TaskNode current, TaskNode goal) {
		if (current == goal)
			return true;
		// It is a conditional node
		else if (current instanceof ConditionNode){
			ConditionNode condNode = (ConditionNode) current;
			return determineWhetherIfBranch(condNode.specificChild, goal);
		}
		else
			return false;
	}

	private boolean hasPath(Node from, Node to, GraphIndividual graph) {
	    Queue<Node> queue = new LinkedList<Node>();
	    queue.offer( from );

	    while (!queue.isEmpty()) {
	        Node current = queue.poll();
	        if (current == to)
	            return true;
	        else {
	            List<Edge> edges = current.getOutgoingEdgeList();
	            if (edges != null) {
    	            for (Edge edge : edges) {
    	                queue.offer( edge.getToNode() );
    	            }
	            }
	        }
	    }

	    return false;
	}

//	private void addToCurrentGoalInputs(Set<String> currentGoalInputs, TaskNode taskNode, Node node, GraphInitializer init) {
//		boolean isConditionalTask = taskNode instanceof ConditionNode;
//
//		if (isConditionalTask && node.getOutputPossibilities().size() > 1) {
//			Node condNode = taskNode.getCorrespondingNode();
//			Set<String> generalConds = new HashSet<String>();
//			Set<String> specificConds = new HashSet<String>();
//
//			for (String o : node.getOutputPossibilities().get(0)) {
//				TaxonomyNode taxNode = init.taxonomyMap.get(o);
//				Set<String> inputs = taxNode.condNodeGeneralInputs.get(condNode.getName());
//				if (inputs != null)
//					generalConds.addAll(inputs);
//			}
//
//			for (String o : node.getOutputPossibilities().get(1)) {
//				TaxonomyNode taxNode = init.taxonomyMap.get(o);
//				Set<String> inputs = taxNode.condNodeSpecificInputs.get(condNode.getName());
//				if (inputs != null)
//					specificConds.addAll(inputs);
//			}
//		}
//		else {
//			for (String o : node.getOutputPossibilities().get(0)) {
//				TaxonomyNode taxNode = init.taxonomyMap.get(o);
//				Set<String> outputs = taxNode.endNodeInputs.get(taskNode.getCorrespondingNode().getName());
//				if (outputs != null)
//					currentGoalInputs.addAll(outputs);
//			}
//		}
//	}
}

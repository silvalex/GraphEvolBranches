package ec.graph;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ec.EvolutionState;
import ec.Individual;
import ec.Problem;
import ec.graph.taskNodes.ConditionNode;
import ec.graph.taskNodes.TaskNode;
import ec.simple.SimpleFitness;
import ec.simple.SimpleProblemForm;

public class GraphEvol extends Problem implements SimpleProblemForm {

	@Override
	public void evaluate(final EvolutionState state, final Individual ind, final int subpopulation, final int threadnum) {
	    GraphInitializer init = (GraphInitializer) state.initializer;
	    if (init.runningOwls) {
	        evaluateOwls(init, state, ind, subpopulation, threadnum);
	    }
	    else {
	        evaluateQoS(init, state, ind, subpopulation, threadnum);
	    }
	}

    public void evaluateQoS(final GraphInitializer init, final EvolutionState state, final Individual ind, final int subpopulation, final int threadnum) {
        if (ind.evaluated) return;   //don't evaluate the individual if it's already evaluated
        if (!(ind instanceof GraphIndividual))
            state.output.fatal("Whoa!  It's not a GraphIndividual!!!",null);
        GraphIndividual ind2 = (GraphIndividual)ind;

        double a = 0.0;
        double r = 0.0;
        double t = 0.0;
        double c = 0.0;

        Set<String> serviceNames = new HashSet<String>();
        Map<String, Set<String>> serviceSuffixMap = new HashMap<String, Set<String>>();
        Map<String, Double> costBySuffixMap = new HashMap<String, Double>();
        Map<String, Double> availabilityBySuffixMap = new HashMap<String, Double>();
        Map<String, Double> reliabilityBySuffixMap = new HashMap<String, Double>();

        for (Node n : ind2.considerableNodeMap.values()) {
            if (!n.getName().equals("start") && !n.getName().startsWith("cond") && !n.getName().startsWith("end")) {
                String[] tokens = n.getName().split("_");
                serviceNames.add(tokens[0]);

                Set<String> services = serviceSuffixMap.get(tokens[tokens.length-1]);
                if (services == null) {
                    services = new HashSet<String>();
                    serviceSuffixMap.put(tokens[tokens.length-1], services);
                }
                services.add(tokens[0]);
            }
        }

        for (String suffix : serviceSuffixMap.keySet()) {
            double cost = 0.0;
            double availability = 1.0;
            double reliability = 1.0;
            for (String name : serviceSuffixMap.get(suffix)) {
                Node node = init.serviceMap.get(name);
                double[] qos = node.getQos();
                cost += qos[GraphInitializer.COST];
                availability *= qos[GraphInitializer.AVAILABILITY];
                reliability *= qos[GraphInitializer.RELIABILITY];
            }

            costBySuffixMap.put(suffix, cost);
            availabilityBySuffixMap.put(suffix, availability);
            reliabilityBySuffixMap.put(suffix, reliability);
        }

        // If an objective of a section has already been fulfilled by earlier sections,
        // just make sure these maps have "perfect" values for this section

        Map<String, Double> timeByEndMap = new HashMap<String, Double>();
        Map<String, Double> probByEndMap = new HashMap<String, Double>();
        Map<String, Double> costByEndMap = new HashMap<String, Double>();
        Map<String, Double> availabilityByEndMap = new HashMap<String, Double>();
        Map<String, Double> reliabilityByEndMap = new HashMap<String, Double>();

        for (Node endNode : init.endNodes) {
            double time = findLongestPath(ind2, endNode.getName(), init);
            timeByEndMap.put(endNode.getName(), time);
        }

        // Calculate probabilities by end
        calculateTreeProbs(init.taskTree.getChildren().get(0), probByEndMap, costByEndMap, availabilityByEndMap, reliabilityByEndMap,
                costBySuffixMap, availabilityBySuffixMap, reliabilityBySuffixMap, 1.0, new HashSet<String>(), ind2);


        for (Node endNode : init.endNodes) {
            double prob = probByEndMap.get(endNode.getName());
            double time = timeByEndMap.get(endNode.getName());
            double cost = costByEndMap.get(endNode.getName());
            double availability = availabilityByEndMap.get(endNode.getName());
            double reliability = reliabilityByEndMap.get(endNode.getName());

            t += (prob * time);
            c += (prob * cost);
            a += (prob * availability);
            r += (prob * reliability);
        }

//        a = normaliseAvailability(a, init);
//        r = normaliseReliability(r, init);
//        t = normaliseTime(t, init);
//        c = normaliseCost(c, init);

//        System.out.println("a: "+ a + ", r: " + r + ", t: " + t + ", c: " + c);

        double fitness = init.w1 * normaliseAvailability(a, init) +
        		         init.w2 * normaliseReliability(r, init) +
        		         init.w3 * (1.0 - normaliseTime(t, init)) +
        		         init.w4 * (1.0 - normaliseCost(c, init));

        ((SimpleFitness)ind2.fitness).setFitness(state,
                // ...the fitness...
                fitness,
                ///... is the individual ideal?  Indicate here...
                false);

        ind2.evaluated = true;
    }

    private void calculateTreeProbs(TaskNode taskNode, Map<String, Double> probByEndMap, Map<String, Double> costByEndMap, Map<String, Double> availabilityByEndMap,
    		Map<String, Double> reliabilityByEndMap, Map<String, Double> costBySuffixMap, Map<String,Double> availabilityBySuffixMap,
    		Map<String, Double> reliabilityBySuffixMap, Double prob, Set<String> suffixes, GraphIndividual graph) {
    	Set<String> newSuffixes = new HashSet<String>(suffixes);
    	newSuffixes.add(taskNode.getCorrespondingNode().getName());

    	if (taskNode instanceof ConditionNode) {
    		ConditionNode condNode = (ConditionNode) taskNode;
    		// Recurse on if-child
    		calculateTreeProbs(condNode.specificChild, probByEndMap, costByEndMap, availabilityByEndMap, reliabilityByEndMap,
    				costBySuffixMap, availabilityBySuffixMap, reliabilityBySuffixMap, prob * graph.nodeMap.get(condNode.getCorrespondingNode().getName()).getProbabilities().get(1), newSuffixes, graph);
    		// Recurse on else-child
    		calculateTreeProbs(condNode.generalChild, probByEndMap, costByEndMap, availabilityByEndMap, reliabilityByEndMap,
    				costBySuffixMap, availabilityBySuffixMap, reliabilityBySuffixMap, prob * graph.nodeMap.get(condNode.getCorrespondingNode().getName()).getProbabilities().get(0), newSuffixes, graph);
    	}
    	else {
    		probByEndMap.put(taskNode.getCorrespondingNode().getName(), prob);
    		double cost = 0.0;
    		double availability = 1.0;
    		double reliability = 1.0;
    		for (String suffix : newSuffixes) {
    			if (costBySuffixMap.get(suffix) != null) {
    				cost += costBySuffixMap.get(suffix);
    			}
    			if (availabilityBySuffixMap.get(suffix) != null)
    				availability *= availabilityBySuffixMap.get(suffix);
    			if (reliabilityBySuffixMap.get(suffix) != null)
    				reliability *= reliabilityBySuffixMap.get(suffix);
    		}
    		costByEndMap.put(taskNode.getCorrespondingNode().getName(), cost);
    		availabilityByEndMap.put(taskNode.getCorrespondingNode().getName(), availability);
    		reliabilityByEndMap.put(taskNode.getCorrespondingNode().getName(), reliability);
    	}
    }

    public void evaluateOwls(final GraphInitializer init, final EvolutionState state, final Individual ind, final int subpopulation, final int threadnum) {

		if (ind.evaluated) return;   //don't evaluate the individual if it's already evaluated
        if (!(ind instanceof GraphIndividual))
            state.output.fatal("Whoa!  It's not a GraphIndividual!!!",null);
        GraphIndividual ind2 = (GraphIndividual)ind;

        // Calculate longest time
        int runPath = findLongestPath2(ind2) - 1;
        ind2.longestPathLength = runPath;
        ind2.numAtomicServices = (ind2.considerableNodeMap.size() - 2);
        boolean isIdeal = runPath == init.idealPathLength && ind2.numAtomicServices == init.idealNumAtomic;

        double fitness = 0.5 * (1.0 / runPath) + 0.5 * (1.0/ ind2.numAtomicServices);
        //double fitness = (100 - runPath) + (100 - ind2.numAtomicServices);

        ((SimpleFitness)ind2.fitness).setFitness(state,
                // ...the fitness...
                fitness,
                ///... is the individual ideal?  Indicate here...
                isIdeal);

        ind2.evaluated = true;
	}



	private double normaliseAvailability(double availability, GraphInitializer init) {
		if (init.maxAvailability - init.minAvailability == 0.0)
			return 1.0;
		else
			return (availability - init.minAvailability)/(init.maxAvailability - init.minAvailability);
	}

	private double normaliseReliability(double reliability, GraphInitializer init) {
		if (init.maxReliability - init.minReliability == 0.0)
			return 1.0;
		else
			return (reliability - init.minReliability)/(init.maxReliability - init.minReliability);
	}

	private double normaliseTime(double time, GraphInitializer init) {
		//double numEnds = init.endNodes.size();

		if ((init.maxTime * init.serviceMap.size()) - init.minTime == 0.0)
			return 0.0;
		else
			return (time - init.minTime)/((init.maxTime * init.serviceMap.size()) - init.minTime);
	}

	private double normaliseCost(double cost, GraphInitializer init) {
		//double numEnds = init.endNodes.size();

		if ((init.maxCost * init.serviceMap.size()) - init.minCost == 0.0)
			return 0.0;
		else
			return (cost - init.minCost)/((init.maxCost * init.serviceMap.size()) - init.minCost);
	}

	/**
	 * Uses the Bellman-Ford algorithm with negative weights to find the longest
	 * path in an acyclic directed graph.
	 *
	 * @param g
	 * @return list of edges composing longest path
	 */
	private double findLongestPath(GraphIndividual g, String endNodeName, GraphInitializer init) {
		Map<String, Double> distance = new HashMap<String, Double>();
		Map<String, Node> predecessor = new HashMap<String, Node>();

		// Step 1: initialize graph
		for (Node node : g.considerableNodeMap.values()) {
			if (node.getName().equals("start"))
				distance.put(node.getName(), 0.0);
			else
				distance.put(node.getName(), Double.POSITIVE_INFINITY);
		}

		// Step 2: relax edges repeatedly
		for (int i = 1; i < g.considerableNodeMap.size(); i++) {
			for (Edge e : g.considerableEdgeList) {
				double dist = distance.get(e.getToNode().getName());
				if ((distance.get(e.getFromNode().getName()) -
				        e.getToNode().getQos()[GraphInitializer.TIME])
				        < distance.get(e.getToNode().getName())) {
					distance.put(e.getToNode().getName(), (distance.get(e.getFromNode().getName()) - e.getToNode().getQos()[GraphInitializer.TIME]));
					predecessor.put(e.getToNode().getName(), e.getFromNode());
				}
			}
		}

		// Now retrieve total cost
		Node pre = predecessor.get(endNodeName);
		double totalTime = 0.0;

		while (pre != null) {
		    String name = pre.getName();
		    if (name.startsWith( "serv" )) {
		        totalTime += pre.getQos()[GraphInitializer.TIME];
		    }
			pre = predecessor.get(name);
		}

		return totalTime;
	}

	/**
	 * Uses the Bellman-Ford algorithm with negative weights to find the longest
	 * path in an acyclic directed graph.
	 *
	 * @param g
	 * @return list of edges composing longest path
	 */
	private int findLongestPath2(GraphIndividual g) {
		Map<String, Integer> distance = new HashMap<String, Integer>();
		Map<String, Node> predecessor = new HashMap<String, Node>();

		// Step 1: initialize graph
		for (Node node : g.considerableNodeMap.values()) {
			if (node.getName().equals("start"))
				distance.put(node.getName(), 0);
			else
				distance.put(node.getName(), Integer.MAX_VALUE);
		}

		// Step 2: relax edges repeatedly
		for (int i = 1; i < g.considerableNodeMap.size(); i++) {
			for (Edge e : g.considerableEdgeList) {
				if ((distance.get(e.getFromNode().getName()) - 1)
				        < distance.get(e.getToNode().getName())) {
					distance.put(e.getToNode().getName(), (distance.get(e.getFromNode().getName()) - 1));
					predecessor.put(e.getToNode().getName(), e.getFromNode());
				}
			}
		}

		// Now retrieve total cost
		Node pre = predecessor.get("end");
		int totalTime = 0;

		while (pre != null) {
			totalTime += 1;
			pre = predecessor.get(pre.getName());
		}

		return totalTime;
	}

//	@Override
//	public void describe(EvolutionState state, Individual ind, int subpopulation, int thread, int log) {
//		Log l = state.output.getLog(log);
//		GraphIndividual graph = (GraphIndividual) ind;
//
//		System.out.println(String.format("runPath= %d #atomicProcess= %d\n", graph.longestPathLength, graph.considerableNodeMap.size() - 2));
//		l.writer.append(String.format("runPath= %d #atomicProcess= %d\n", graph.longestPathLength, graph.considerableNodeMap.size() - 2));
//	}
}

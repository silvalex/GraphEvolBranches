package ec.graph;
import java.util.HashMap;
import java.util.Map;

import ec.EvolutionState;
import ec.Individual;
import ec.Problem;
import ec.simple.SimpleFitness;
import ec.simple.SimpleProblemForm;
import ec.util.Log;

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

        double a = 1.0;
        double r = 1.0;
        double t = 0.0;
        double c = 0.0;

        for (Node n : ind2.considerableNodeMap.values()) {
        	double[] qos = n.getQos();
        	a *= qos[GraphInitializer.AVAILABILITY];
        	r *= qos[GraphInitializer.RELIABILITY];
        	c += qos[GraphInitializer.COST];
        }

        // Calculate longest time
        t = findLongestPath(ind2);

        a = normaliseAvailability(a, init);
        r = normaliseReliability(r, init);
        t = normaliseTime(t, init);
        c = normaliseCost(c, init);

        double fitness = init.w1 * a + init.w2 * r + init.w3 * t + init.w4 * c;

        ((SimpleFitness)ind2.fitness).setFitness(state,
                // ...the fitness...
                fitness,
                ///... is the individual ideal?  Indicate here...
                false);

        ind2.evaluated = true;
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
		if (init.maxTime - init.minTime == 0.0)
			return 1.0;
		else
			return (init.maxTime - time)/(init.maxTime - init.minTime);
	}

	private double normaliseCost(double cost, GraphInitializer init) {
		if (init.maxCost - init.minCost == 0.0)
			return 1.0;
		else
			return (init.maxCost - cost)/(init.maxCost - init.minCost);
	}

	/**
	 * Uses the Bellman-Ford algorithm with negative weights to find the longest
	 * path in an acyclic directed graph.
	 *
	 * @param g
	 * @return list of edges composing longest path
	 */
	private double findLongestPath(GraphIndividual g) {
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
				if ((distance.get(e.getFromNode().getName()) -
				        e.getToNode().getQos()[GraphInitializer.TIME])
				        < distance.get(e.getToNode().getName())) {
					distance.put(e.getToNode().getName(), (distance.get(e.getFromNode().getName()) - e.getToNode().getQos()[GraphInitializer.TIME]));
					predecessor.put(e.getToNode().getName(), e.getFromNode());
				}
			}
		}

		// Now retrieve total cost
		Node pre = predecessor.get("end");
		double totalTime = 0.0;

		while (pre != null) {
			totalTime += pre.getQos()[GraphInitializer.TIME];
			pre = predecessor.get(pre.getName());
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

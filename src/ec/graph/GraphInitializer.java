package ec.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import ec.EvolutionState;
import ec.graph.taskNodes.ConditionNode;
import ec.graph.taskNodes.InputNode;
import ec.graph.taskNodes.OutputNode;
import ec.graph.taskNodes.TaskNode;
import ec.simple.SimpleInitializer;
import ec.util.Parameter;

public class GraphInitializer extends SimpleInitializer {
	// Constants with of order of QoS attributes
	public static final int TIME = 0;
	public static final int COST = 1;
	public static final int AVAILABILITY = 2;
	public static final int RELIABILITY = 3;

	public Map<String, Node> serviceMap = new HashMap<String, Node>();
	public Set<Node> relevant;
	public Map<String, TaxonomyNode> taxonomyMap = new HashMap<String, TaxonomyNode>();
	public TaskNode taskTree;
	public Node startNode;
	public List<Node> endNodes = new ArrayList<Node>();
	public List<Node> condNodes = new ArrayList<Node>();
	public List<String> suffixList = new ArrayList<String>();
	public GraphRandom random;

	public final double minAvailability = 0.0;
	public double maxAvailability = -1.0;
	public final double minReliability = 0.0;
	public double maxReliability = -1.0;
	public double minTime = Double.MAX_VALUE;
	public double maxTime = -1.0;
	public double minCost = Double.MAX_VALUE;
	public double maxCost = -1.0;
	public double w1;
	public double w2;
	public double w3;
	public double w4;
	public boolean overlapEnabled;
	public boolean runningOwls;
	public boolean findConcepts;
	public double overlapPercentage;
	public int idealPathLength;
	public int idealNumAtomic;

//	public Stopwatch watch = new Stopwatch();

	@Override
	public void setup(EvolutionState state, Parameter base) {
//	    watch.start();
		Parameter servicesParam = new Parameter("composition-services");
		Parameter taskParam = new Parameter("composition-task");
		Parameter taxonomyParam = new Parameter("composition-taxonomy");
		Parameter weight1Param = new Parameter("fitness-weight1");
		Parameter weight2Param = new Parameter("fitness-weight2");
		Parameter weight3Param = new Parameter("fitness-weight3");
		Parameter weight4Param = new Parameter("fitness-weight4");
		Parameter overlapEnabledParam = new Parameter("overlap-enabled");
		Parameter overlapPercentageParam = new Parameter("overlap-percentage");
		Parameter idealPathLengthParam = new Parameter("ideal-path-length");
		Parameter idealNumAtomicParam = new Parameter("ideal-num-atomic");
		Parameter runningOwlsParam = new Parameter("running-owls");
		Parameter findConceptsParam = new Parameter("find-concepts");

		w1 = state.parameters.getDouble(weight1Param, null);
		w2 = state.parameters.getDouble(weight2Param, null);
		w3 = state.parameters.getDouble(weight3Param, null);
		w4 = state.parameters.getDouble(weight4Param, null);
	    overlapEnabled = state.parameters.getBoolean( overlapEnabledParam, null, false );
	    runningOwls = state.parameters.getBoolean( runningOwlsParam, null, false );
	    overlapPercentage = state.parameters.getDouble( overlapPercentageParam, null );
		idealPathLength = state.parameters.getInt(idealPathLengthParam, null);
		idealNumAtomic = state.parameters.getInt(idealNumAtomicParam, null);
		findConcepts = state.parameters.getBoolean( findConceptsParam, null, false );

		parseWSCServiceFile(state.parameters.getString(servicesParam, null));
		parseWSCTaskFile(state.parameters.getString(taskParam, null));
		parseWSCTaxonomyFile(state.parameters.getString(taxonomyParam, null));
		if (findConcepts)
		    findConceptsForInstances();

		random = new GraphRandom(state.random[0]);

		double[] mockQos = new double[4];
		mockQos[TIME] = 0;
		mockQos[COST] = 0;
		mockQos[AVAILABILITY] = 1;
		mockQos[RELIABILITY] = 1;
		Set<String> startOutput = new HashSet<String>();
		startOutput.addAll(((InputNode)taskTree).inputs);

		List<List<String>> outString = new ArrayList<List<String>>();
		outString.add(new ArrayList<String>(((InputNode)taskTree).inputs));
		List<Float> probabilities = new ArrayList<Float>();
		probabilities.add(1.0f);

		Set<OutputNode> outputs = new HashSet<OutputNode>();
		Set<ConditionNode> conditions = new HashSet<ConditionNode>();
		taskTree.getAllOutputNodes(outputs);
		taskTree.getAllConditionNodes(conditions);
		Set<String> outputStrings = new HashSet<String>();

		startNode = new Node("start", "start", mockQos, new HashSet<String>(), outString, probabilities);
		startNode.setTaskNode(taskTree);
		taskTree.setCorrespondingNode(startNode);

		int i = 1;
		for (OutputNode o : outputs) {
			outputStrings.addAll(o.outputs);
			Node endNode = new Node("end" + i, "end" + i, mockQos, o.outputs, null, probabilities);
			suffixList.add(endNode.getName());
			endNode.setTaskNode(o);
			endNodes.add(endNode);
			o.setCorrespondingNode(endNode);
			i++;
		}

		int j = 1;
		for (ConditionNode c : conditions) {
			List<String> outsGeneral = new ArrayList<String>();
			List<String> outsSpecific = new ArrayList<String>();
			outsGeneral.add(c.general);
			outsGeneral.addAll(startNode.getOutputPossibilities().get(0));
			outsSpecific.add(c.specific);
			outsSpecific.addAll(startNode.getOutputPossibilities().get(0));

			List<List<String>> outputPossibilities = new ArrayList<List<String>>();
			outputPossibilities.add(outsGeneral);
			outputPossibilities.add(outsSpecific);
			Node condNode = new Node("cond" + j, "cond" + j, mockQos, c.general, c.specific, outputPossibilities, null, null);
			suffixList.add(condNode.getName());
			condNode.setTaskNode(c);
			condNodes.add(condNode);
			c.setCorrespondingNode(condNode);
			j++;
		}

		populateTaxonomyTree();
		Set<String> inputStrings = new HashSet<String>();
		inputStrings.addAll(((InputNode)taskTree).inputs);

		relevant = getRelevantServices(serviceMap, inputStrings, outputStrings);
		calculateNormalisationBounds(relevant);
	}

	/**
	 * Checks whether set of inputs can be completely satisfied by the search
	 * set, making sure to check descendants of input concepts for the subsumption.
	 *
	 * @param inputs
	 * @param searchSet
	 * @return true if search set subsumed by input set, false otherwise.
	 */
	public boolean isSubsumed(Set<String> inputs, Set<String> searchSet) {
		boolean satisfied = true;
		for (String input : inputs) {
			Set<String> subsumed = taxonomyMap.get(input).getSubsumedConcepts();
			if (!isIntersection( searchSet, subsumed )) {
				satisfied = false;
				break;
			}
		}
		return satisfied;
	}

    private static boolean isIntersection( Set<String> a, Set<String> b ) {
        for ( String v1 : a ) {
            if ( b.contains( v1 ) ) {
                return true;
            }
        }
        return false;
    }

	/**
	 * Populates the taxonomy tree by associating services to the
	 * nodes in the tree.
	 */
	private void populateTaxonomyTree() {
		for (Node s: serviceMap.values()) {
			addServiceToTaxonomyTree(s);
		}

		// Add input, output and condition nodes
		addServiceToTaxonomyTree(startNode);
		addEndNodesToTaxonomyTree();
		addConditionNodesToTaxonomyTree();
	}

	private void addServiceToTaxonomyTree(Node s) {
		// Populate outputs
	    Set<TaxonomyNode> seenConceptsOutput = new HashSet<TaxonomyNode>();

	    // The general output possibilities come first by convention
		for (String outputVal : s.getOutputPossibilities().get(0)) {
			TaxonomyNode n = taxonomyMap.get(outputVal);
//			n.servicesWithOutput.add(s);
			s.getTaxonomyOutputs().add(n);

			// Also add output to all parent nodes
			Queue<TaxonomyNode> queue = new LinkedList<TaxonomyNode>();
			queue.add( n );

			while (!queue.isEmpty()) {
			    TaxonomyNode current = queue.poll();
		        seenConceptsOutput.add( current );
		        current.servicesWithOutput.add(s);
		        for (TaxonomyNode parent : current.parents) {
		            if (!seenConceptsOutput.contains( parent )) {
		                queue.add(parent);
		                seenConceptsOutput.add(parent);
		            }
		        }
			}
		}

		// Populate inputs
		Set<TaxonomyNode> seenConceptsInput = new HashSet<TaxonomyNode>();
		for (String inputVal : s.getInputs()) {
			TaxonomyNode n = taxonomyMap.get(inputVal);
			//n.servicesWithInput.add(s);

			// Also add input to all children nodes
			Queue<TaxonomyNode> queue = new LinkedList<TaxonomyNode>();
			queue.add( n );

			while(!queue.isEmpty()) {
				TaxonomyNode current = queue.poll();
				seenConceptsInput.add( current );
			    current.servicesWithInput.add(s);
			    for (TaxonomyNode child : current.children) {
			        if (!seenConceptsInput.contains( child )) {
			            queue.add(child);
			            seenConceptsInput.add( child );
			        }
			    }
			}
		}
		return;
	}

	private void addEndNodesToTaxonomyTree() {
		for (Node endNode : endNodes) {
			for (String inputVal : endNode.getInputs()) {
				TaxonomyNode n = taxonomyMap.get(inputVal);
				Set<String> inputs = n.endNodeInputs.get(endNode.getName());
				if (inputs == null) {
					inputs = new HashSet<String>();
					n.endNodeInputs.put(endNode.getName(), inputs);
				}
				inputs.add(inputVal);

				// Also add input to all children nodes
				Queue<TaxonomyNode> queue = new LinkedList<TaxonomyNode>();
				queue.addAll(n.children);

				while(!queue.isEmpty()) {
					TaxonomyNode current = queue.poll();
					Set<String> ins = current.endNodeInputs.get(endNode.getName());
					if (ins == null) {
						ins = new HashSet<String>();
						current.endNodeInputs.put(endNode.getName(), ins);
					}
					ins.add(inputVal);
					queue.addAll(current.children);
				}
			}
		}
	}

	private void addConditionNodesToTaxonomyTree() {


		for (Node condNode : condNodes) {
			// General value
			TaxonomyNode n = taxonomyMap.get(condNode.getGeneralCondition());
			condNode.getGeneralTaxonomyOutputs().add(n);
			Set<String> inputs = n.condNodeGeneralInputs.get(condNode.getName());
			if (inputs == null) {
				inputs = new HashSet<String>();
				n.condNodeGeneralInputs.put(condNode.getName(), inputs);
			}
			inputs.add(condNode.getGeneralCondition());

			// Also add input to all children nodes
			Queue<TaxonomyNode> queue = new LinkedList<TaxonomyNode>();
			queue.addAll(n.children);

			while (!queue.isEmpty()) {
				TaxonomyNode current = queue.poll();
				Set<String> ins = current.condNodeGeneralInputs.get(condNode.getName());
				if (ins == null) {
					ins = new HashSet<String>();
					current.condNodeGeneralInputs.put(condNode.getName(), ins);
				}
				ins.add(condNode.getGeneralCondition());
				queue.addAll(current.children);
			}

			// Specific value
			n = taxonomyMap.get(condNode.getSpecificCondition());
			condNode.getSpecificTaxonomyOutputs().add(n);
			inputs = n.condNodeSpecificInputs.get(condNode.getName());
			if (inputs == null) {
				inputs = new HashSet<String>();
				n.condNodeSpecificInputs.put(condNode.getName(), inputs);
			}
			inputs.add(condNode.getSpecificCondition());

			// Also add input to all children nodes
			queue = new LinkedList<TaxonomyNode>();
			queue.addAll(n.children);

			while (!queue.isEmpty()) {
				TaxonomyNode current = queue.poll();
				Set<String> ins = current.condNodeSpecificInputs.get(condNode.getName());
				if (ins == null) {
					ins = new HashSet<String>();
					current.condNodeSpecificInputs.put(condNode.getName(), ins);
				}
				ins.add(condNode.getSpecificCondition());
				queue.addAll(current.children);
			}

			queue = new LinkedList<TaxonomyNode>();
			queue.add( n );

			// Add cond. node to list of service outputs
			while (!queue.isEmpty()) {
			    TaxonomyNode current = queue.poll();
		        current.servicesWithOutput.add(condNode);
		        for (TaxonomyNode parent : current.parents) {
	                queue.add(parent);
		        }
			}
		}
	}

	/**
	 * Converts input, output, and service instance values to their corresponding
	 * ontological parent.
	 */
	private void findConceptsForInstances() {
		Set<String> temp = new HashSet<String>();

		// Go through root (task input) values
		Set<String> taskInputs = ((InputNode)taskTree).inputs;
		for (String s : taskInputs)
			temp.add(taxonomyMap.get(s).parents.get(0).value);
		taskInputs.clear();
		taskInputs.addAll(temp);
		temp.clear();

		// Find all output nodes, and go through their values

		recFindConceptsForInstances(taskTree, temp);

		for (Node s : serviceMap.values()) {
			temp.clear();
			Set<String> inputs = s.getInputs();
			for (String i : inputs)
				temp.add(taxonomyMap.get(i).parents.get(0).value);
			inputs.clear();
			inputs.addAll(temp);


			for (List<String> outList : s.getOutputPossibilities()) {
				temp.clear();
				for (String o : outList)
					temp.add(taxonomyMap.get(o).parents.get(0).value);
				outList.clear();
				outList.addAll(temp);
			}
		}
	}

	private void recFindConceptsForInstances(TaskNode node, Set<String> temp) {
		// It is an output node
		List<TaskNode> children = node.getChildren();

		if (children == null) {
			Set<String> taskOutputs = ((OutputNode)node).outputs;
			for (String s : taskOutputs) {
				temp.add(taxonomyMap.get(s).parents.get(0).value);
			}
			taskOutputs.clear();
			taskOutputs.addAll(temp);
			temp.clear();
		}
		else {
			for (TaskNode n : children) {
				recFindConceptsForInstances(n, temp);
			}
		}
	}

	public void removeDanglingNodes(GraphIndividual graph) {
	    List<Node> dangling = new ArrayList<Node>();
	    for (Node g : graph.nodeMap.values()) {
	        if (!g.getName().startsWith("end") && !g.getName().startsWith("cond") && g.getOutgoingEdgeList().isEmpty())
	            dangling.add( g );
	    }

	    for (Node d: dangling) {
	        removeDangling(d, graph);
	    }
	}

	private void removeDangling(Node n, GraphIndividual graph) {
	    if (n.getOutgoingEdgeList().isEmpty()) {
	        graph.nodeMap.remove( n.getName() );
	        graph.considerableNodeMap.remove( n.getName() );
	        for (Edge e : n.getIncomingEdgeList()) {
	        	removeAllOccurrences(e.getFromNode().getOutgoingEdgeList(), e);
	        	removeAllOccurrences(graph.edgeList, e);
	        	removeAllOccurrences(graph.considerableEdgeList, e);
	        	if (!e.getFromNode().getName().startsWith("cond"))
	        		removeDangling(e.getFromNode(), graph);
	        }
	    }
	}

	private <T> void removeAllOccurrences(List<T> list, T item) {
		int i = 1;
		while(list.remove(item)){}
	}

	/**
	 * Goes through the service list and retrieves only those services which
	 * could be part of the composition task requested by the user.
	 *
	 * @param serviceMap
	 * @return relevant services
	 */
	private Set<Node> getRelevantServices(Map<String,Node> serviceMap, Set<String> inputs, Set<String> outputs) {
		// Copy service map values to retain original
		Collection<Node> services = new ArrayList<Node>(serviceMap.values());

		Set<String> cSearch = new HashSet<String>(inputs);
		Set<Node> sSet = new HashSet<Node>();
		Set<Node> sFound = discoverService(services, cSearch);
		while (!sFound.isEmpty()) {
			sSet.addAll(sFound);
			services.removeAll(sFound);
			for (Node s: sFound) {
				for (List<String> outPoss : s.getOutputPossibilities()) {
					cSearch.addAll(outPoss);
				}
			}
			sFound.clear();
			sFound = discoverService(services, cSearch);
		}

		if (isSubsumed(outputs, cSearch)) {
			return sSet;
		}
		else {
			String message = "It is impossible to perform a composition using the services and settings provided.";
			System.out.println(message);
			System.exit(0);
			return null;
		}
	}

	private void calculateNormalisationBounds(Set<Node> services) {
		for(Node service: services) {
			double[] qos = service.getQos();

			// Availability
			double availability = qos[AVAILABILITY];
			if (availability > maxAvailability)
				maxAvailability = availability;

			// Reliability
			double reliability = qos[RELIABILITY];
			if (reliability > maxReliability)
				maxReliability = reliability;

			// Time
			double time = qos[TIME];
			if (time > maxTime)
				maxTime = time;
			if (time < minTime)
				minTime = time;

			// Cost
			double cost = qos[COST];
			if (cost > maxCost)
				maxCost = cost;
			if (cost < minCost)
				minCost = cost;
		}
		// Adjust max. cost and max. time based on the number of services in shrunk repository
		maxCost *= services.size();
		maxTime *= services.size();

	}

	/**
	 * Discovers all services from the provided collection whose
	 * input can be satisfied either (a) by the input provided in
	 * searchSet or (b) by the output of services whose input is
	 * satisfied by searchSet (or a combination of (a) and (b)).
	 *
	 * @param services
	 * @param searchSet
	 * @return set of discovered services
	 */
	private Set<Node> discoverService(Collection<Node> services, Set<String> searchSet) {
		Set<Node> found = new HashSet<Node>();
		for (Node s: services) {
			if (isSubsumed(s.getInputs(), searchSet))
				found.add(s);
		}
		return found;
	}

	/**
	 * Parses the WSC Web service file with the given name, creating Web
	 * services based on this information and saving them to the service map.
	 *
	 * @param fileName
	 */
	private void parseWSCServiceFile(String fileName) {
        Set<String> inputs = new HashSet<String>();
        List<List<String>> outputPossibilities = new ArrayList<List<String>>();
        List<Float> probabilities = new ArrayList<Float>();
        double[] qos = new double[4];

        try {
        	File fXmlFile = new File(fileName);
        	DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        	Document doc = dBuilder.parse(fXmlFile);

        	NodeList nList = doc.getElementsByTagName("service");

        	for (int i = 0; i < nList.getLength(); i++) {
        		org.w3c.dom.Node nNode = nList.item(i);
        		Element eElement = (Element) nNode;

        		String name = eElement.getAttribute("name");
        		if (!runningOwls) {
        		    qos[TIME] = Double.valueOf(eElement.getAttribute("Res"));
        		    qos[COST] = Double.valueOf(eElement.getAttribute("Pri"));
        		    qos[AVAILABILITY] = Double.valueOf(eElement.getAttribute("Ava"));
        		    qos[RELIABILITY] = Double.valueOf(eElement.getAttribute("Rel"));
        		}

				// Get inputs
				org.w3c.dom.Node inputNode = eElement.getElementsByTagName("inputs").item(0);
				NodeList inputNodes = ((Element)inputNode).getElementsByTagName("instance");
				for (int j = 0; j < inputNodes.getLength(); j++) {
					org.w3c.dom.Node in = inputNodes.item(j);
					Element e = (Element) in;
					inputs.add(e.getAttribute("name"));
				}

				// Get outputs -- the general possibility comes first by convention
				org.w3c.dom.Node outputNode = eElement.getElementsByTagName("outputs-possibilities").item(0);
				NodeList possList = ((Element)outputNode).getElementsByTagName("outputs");
				for (int j = 0; j < possList.getLength(); j++) {
					org.w3c.dom.Node out = possList.item(j);
					Element e = (Element) out;
					probabilities.add(Float.valueOf(e.getAttribute("prob")));

					List<String> outputs = new ArrayList<String>();
					NodeList valueList = e.getElementsByTagName("instance");
					for (int k = 0; k < valueList.getLength(); k++) {
						org.w3c.dom.Node outVal = valueList.item(k);
						outputs.add(((Element)outVal).getAttribute("name"));
					}
					outputPossibilities.add(outputs);
				}

                Node ws = new Node(name, name, qos, inputs, outputPossibilities, probabilities);
                serviceMap.put(name, ws);
                inputs = new HashSet<String>();
                outputPossibilities = new ArrayList<List<String>>();
                probabilities = new ArrayList<Float>();
                qos = new double[4];
        	}
        }
        catch(IOException ioe) {
            System.out.println("Service file parsing failed...");
        }
        catch (ParserConfigurationException e) {
            System.out.println("Service file parsing failed...");
		}
        catch (SAXException e) {
            System.out.println("Service file parsing failed...");
		}
    }

	/**
	 * Parses the WSC task file with the given name, extracting input and
	 * output values to be used as the composition task.
	 *
	 * @param fileName
	 */
	private void parseWSCTaskFile(String fileName) {
		try {
	    	File fXmlFile                    = new File(fileName);
	    	DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	    	DocumentBuilder dBuilder         = dbFactory.newDocumentBuilder();
	    	Document doc                     = dBuilder.parse(fXmlFile);

	    	// Create tree root
            InputNode root = new InputNode();
            taskTree = root;

            org.w3c.dom.Node provided = getFirstDocNode(doc, "provided");
	    	addInputs(root, provided, false);

	    	org.w3c.dom.Node options = getFirstDocNode(doc, "options");
            if (options != null) {
                recursiveParse(root, options, false, 0);
            }
		}
		catch (ParserConfigurationException e) {
            System.out.println("Task file parsing failed...");
            e.printStackTrace();
		}
		catch (SAXException e) {
            System.out.println("Task file parsing failed...");
            e.printStackTrace();
		}
		catch (IOException e) {
            System.out.println("Task file parsing failed...");
            e.printStackTrace();
		}
	}

    private org.w3c.dom.Node getFirstDocNode(org.w3c.dom.Node parent, String tagName) {
        NodeList nodeList = ((Document) parent).getElementsByTagName(tagName);
        for (int i = 0, len = nodeList.getLength(); i < len; ++i) {
            org.w3c.dom.Node node = nodeList.item( i );
            if (node.getOwnerDocument().equals( parent ))
                return node;
        }
        return null;
    }

	private org.w3c.dom.Node getFirstNode(org.w3c.dom.Node parent, String tagName) {
        NodeList nodeList = ((Element) parent).getElementsByTagName(tagName);
        for (int i = 0, len = nodeList.getLength(); i < len; ++i) {
            Element el = ((Element)nodeList.item( i ));
            if (el.getParentNode().equals( parent ))
                return el;
        }
        return null;
	}

    private void addInputs(InputNode inputNode, org.w3c.dom.Node parent, boolean isGeneral) {
        NodeList instanceList = ((Element) parent).getElementsByTagName("instance");

        for (int i = 0; i < instanceList.getLength(); i++) {
            org.w3c.dom.Node item = instanceList.item(i);
            Element el = (Element) item;
            if (el.getParentNode().equals( parent ))
                inputNode.inputs.add(el.getAttribute("name"));
        }
    }

	private void addOutputs(TaskNode taskNode, org.w3c.dom.Node parent, boolean isGeneral) {
        NodeList instanceList = ((Element) parent).getElementsByTagName("instance");

        OutputNode outNode = new OutputNode();
        for (int i = 0; i < instanceList.getLength(); i++) {
            org.w3c.dom.Node item = instanceList.item(i);
            Element el = (Element) item;
            if (el.getParentNode().equals( parent ))
                outNode.outputs.add(el.getAttribute("name"));
        }
        taskNode.addChild( outNode, isGeneral );
        outNode.parent = taskNode;
	}

	private void recursiveParse(TaskNode taskNode, org.w3c.dom.Node parent, boolean isGeneral, int level) {
	    org.w3c.dom.Node condNode = getFirstNode(parent, "condition");
	    ConditionNode cond = null;
	    if (condNode != null) {
	        cond = new ConditionNode();
            taskNode.addChild( cond, isGeneral );
            cond.parent = taskNode;
	        org.w3c.dom.Node genNode = getFirstNode(condNode, "general");
	        if (genNode != null)
                cond.general = ((Element)genNode).getAttribute("concept");
	        org.w3c.dom.Node specNode = getFirstNode(condNode, "specific");
	        if (specNode != null)
	            cond.specific = ((Element)specNode).getAttribute("concept");
	    }
	    org.w3c.dom.Node ifNode = getFirstNode(parent, "if");
	    if (ifNode != null) {
	        org.w3c.dom.Node subOptions = getFirstNode(ifNode, "options");
	        if (subOptions != null) {
	            recursiveParse(cond, subOptions, false, level + 1);
	        }
	        else {
	            addOutputs(cond, ifNode, false);
	        }
	        org.w3c.dom.Node elseNode = getFirstNode(parent, "else");
	        if (elseNode != null) {
	            subOptions = getFirstNode(elseNode, "options");
	            if (subOptions != null) {
	                recursiveParse(cond, subOptions, true, level + 1);
	            }
	            else {
	                addOutputs(cond, elseNode, true);
	            }
	        }
	    }
	}

	/**
	 * Parses the WSC taxonomy file with the given name, building a
	 * graph-like structure.
	 *
	 * @param fileName
	 */
	private void parseWSCTaxonomyFile(String fileName) {
		try {
	    	File fXmlFile = new File(fileName);
	    	DocumentBuilderFactory dbFactory     = DocumentBuilderFactory.newInstance();
	    	DocumentBuilder        dBuilder      = dbFactory.newDocumentBuilder();
	    	Document               doc           = dBuilder.parse(fXmlFile);
	    	NodeList               taxonomyRoots = doc.getChildNodes();

	    	processTaxonomyChildren(null, taxonomyRoots);
		}

		catch (ParserConfigurationException e) {
            System.err.println("Taxonomy file parsing failed...");
		}
		catch (SAXException e) {
            System.err.println("Taxonomy file parsing failed...");
		}
		catch (IOException e) {
            System.err.println("Taxonomy file parsing failed...");
		}
	}

	/**
	 * Recursive function for recreating taxonomy structure from file.
	 *
	 * @param parent - Nodes' parent
	 * @param nodes
	 */
	private void processTaxonomyChildren(TaxonomyNode parent, NodeList nodes) {
		if (nodes != null && nodes.getLength() != 0) {
			for (int i = 0; i < nodes.getLength(); i++) {
				org.w3c.dom.Node ch = nodes.item(i);

				if (!(ch instanceof Text)) {
					Element currNode = (Element) nodes.item(i);
					String value = currNode.getAttribute("name");
    					TaxonomyNode taxNode = taxonomyMap.get( value );
    					if (taxNode == null) {
    					    taxNode = new TaxonomyNode(value);
    					    taxonomyMap.put( value, taxNode );
    					}
    					if (parent != null) {
    					    taxNode.parents.add(parent);
    						parent.children.add(taxNode);
    					}

    					NodeList children = currNode.getChildNodes();
    					processTaxonomyChildren(taxNode, children);
					}
				}
		}
	}

}

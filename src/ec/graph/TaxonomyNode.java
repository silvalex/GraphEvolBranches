package ec.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a node in the input/output taxonomy
 * used by the WSC dataset.
 *
 * @author sawczualex
 */
public class TaxonomyNode {
	public Map<String, Set<String>> endNodeInputs = new HashMap<String, Set<String>>();
	public Map<String, Set<String>> condNodeGeneralInputs = new HashMap<String, Set<String>>();
	public Map<String, Set<String>> condNodeSpecificInputs = new HashMap<String, Set<String>>();
	public List<Node> servicesWithOutput = new ArrayList<Node>();
	public List<Node> servicesWithInput = new ArrayList<Node>();
	public String value;
	public List<TaxonomyNode> parents = new ArrayList<TaxonomyNode>();
	public List<TaxonomyNode> children = new ArrayList<TaxonomyNode>();

	public TaxonomyNode(String value) {
		this.value = value;
	}

	/**
	 * Gets all concepts subsumed by this node (i.e. all
	 * concepts in its subtree).
	 *
	 * @return Set of concepts
	 */
	public Set<String> getSubsumedConcepts() {
		Set<String> concepts = new HashSet<String>();
        _getSubsumedConcepts( concepts );
		return concepts;
	}

    private void _getSubsumedConcepts(Set<String> concepts) {
        if (!concepts.contains( value )) {
            concepts.add(value);
            for (TaxonomyNode child : children) {
                child._getSubsumedConcepts(concepts);
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof TaxonomyNode) {
            return ((TaxonomyNode)other).value.equals( value );
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

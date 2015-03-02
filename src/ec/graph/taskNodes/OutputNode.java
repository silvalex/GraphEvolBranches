package ec.graph.taskNodes;

import java.util.HashSet;
import java.util.Set;

public class OutputNode implements TaskNode {
	public Set<String> outputs = new HashSet<String>();
    public void addChild( TaskNode child, boolean isGeneral ) {
    }
}

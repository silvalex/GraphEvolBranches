package ec.graph.taskNodes;

import java.util.HashSet;
import java.util.Set;

public class InputNode implements TaskNode {
	public TaskNode child;
	public Set<String> inputs = new HashSet<String>();
    public void addChild( TaskNode child, boolean isGeneral ) {
        this.child = child;
    }
}

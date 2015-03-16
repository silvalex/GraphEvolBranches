package ec.graph.taskNodes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ec.graph.Node;

public class OutputNode implements TaskNode {
	public TaskNode parent;
	public Set<String> outputs = new HashSet<String>();
	public Node correspondingNode;

	@Override
    public void addChild( TaskNode child, boolean isGeneral ) {
    }
	@Override
	public List<TaskNode> getChildren() {
		return null;
	}
	@Override
	public void getAllOutputNodes(Set<OutputNode> o) {
		o.add(this);
	}

	@Override
	public void getAllConditionNodes(Set<ConditionNode> c){}

	@Override
	public void setCorrespondingNode(Node corresponding) {
		correspondingNode = corresponding;
	}

	@Override
	public Node getCorrespondingNode() {
		return correspondingNode;
	}
}

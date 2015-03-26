package ec.graph.taskNodes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ec.graph.Node;

public class InputNode implements TaskNode {
	public TaskNode parent;
	public TaskNode child;
	public Set<String> inputs = new HashSet<String>();
	public Node correspondingNode;

	@Override
    public void addChild( TaskNode child, boolean isGeneral ) {
        this.child = child;
    }

	@Override
	public List<TaskNode> getChildren() {
		List<TaskNode> l = new ArrayList<TaskNode>();
		l.add(child);
		return l;
	}

	@Override
	public void getAllOutputNodes(Set<OutputNode> outputs) {
		child.getAllOutputNodes(outputs);
	}

	@Override
	public void getAllConditionNodes(Set<ConditionNode> conditions) {
		child.getAllConditionNodes(conditions);
	}

	@Override
	public void setCorrespondingNode(Node corresponding) {
		correspondingNode = corresponding;
	}

	@Override
	public Node getCorrespondingNode() {
		return correspondingNode;
	}

	@Override
	public TaskNode getParent(){
		return parent;
	}
}

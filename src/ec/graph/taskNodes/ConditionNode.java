package ec.graph.taskNodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ec.graph.Node;

public class ConditionNode implements TaskNode {
	public TaskNode parent;
	public String general;
	public String specific;
	public TaskNode generalChild;
	public TaskNode specificChild;
	public Node correspondingNode;

	@Override
    public void addChild( TaskNode child, boolean isGeneral ) {
        if (isGeneral)
            generalChild = child;
        else
            specificChild = child;
    }

    @Override
	public List<TaskNode> getChildren() {
		List<TaskNode> l = new ArrayList<TaskNode>();
		l.add(generalChild);
		l.add(specificChild);
		return l;
	}

	@Override
	public void getAllOutputNodes(Set<OutputNode> outputs) {
		generalChild.getAllOutputNodes(outputs);
		specificChild.getAllOutputNodes(outputs);
	}

	@Override
	public void getAllConditionNodes(Set<ConditionNode> conditions) {
		conditions.add(this);
		generalChild.getAllConditionNodes(conditions);
		specificChild.getAllConditionNodes(conditions);
	}

	@Override
	public void setCorrespondingNode(Node corresponding) {
		correspondingNode = corresponding;
	}

	@Override
	public Node getCorrespondingNode() {
		return correspondingNode;
	}
}

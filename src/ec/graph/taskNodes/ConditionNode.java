package ec.graph.taskNodes;

public class ConditionNode implements TaskNode {
	public String general;
	public String specific;
	public TaskNode generalChild;
	public TaskNode specificChild;
    public void addChild( TaskNode child, boolean isGeneral ) {
        if (isGeneral)
            generalChild = child;
        else
            specificChild = child;
    }
}

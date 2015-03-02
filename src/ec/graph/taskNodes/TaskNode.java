package ec.graph.taskNodes;

public interface TaskNode {
    void addChild(TaskNode child, boolean isGeneral);
}

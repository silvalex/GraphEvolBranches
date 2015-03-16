package ec.graph.taskNodes;

import java.util.List;
import java.util.Set;

import ec.graph.Node;

public interface TaskNode {
    void addChild(TaskNode child, boolean isGeneral);
    List<TaskNode> getChildren();
    void getAllOutputNodes(Set<OutputNode> outputs);
    void getAllConditionNodes(Set<ConditionNode> conditions);
    void setCorrespondingNode(Node corresponding);
    Node getCorrespondingNode();
}

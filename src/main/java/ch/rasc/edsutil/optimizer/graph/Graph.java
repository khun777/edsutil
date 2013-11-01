package ch.rasc.edsutil.optimizer.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Graph {

	private final Map<String, Node> nodesDb = new HashMap<>();

	public Node createNode(String name) {
		Node newNode = nodesDb.get(name);
		if (newNode == null) {
			newNode = new Node(name);
			nodesDb.put(name, newNode);
		}
		return newNode;
	}

	public List<Node> resolveDependencies() throws CircularReferenceException {
		List<Node> resolved = new ArrayList<>();
		Set<Node> unresolved = new HashSet<>();
		for (Node node : nodesDb.values()) {
			if (!resolved.contains(node)) {
				depResolve(node, resolved, unresolved);
			}
		}
		return resolved;
	}

	private void depResolve(Node node, List<Node> resolved, Set<Node> unresolved) throws CircularReferenceException {
		unresolved.add(node);
		for (Node edge : node.getEdges()) {
			if (!resolved.contains(edge)) {
				if (unresolved.contains(edge)) {
					throw new CircularReferenceException(node, edge);
				}
				depResolve(edge, resolved, unresolved);
			}
		}
		resolved.add(node);
		unresolved.remove(node);
	}
}

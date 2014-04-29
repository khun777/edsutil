/**
 * Copyright 2013-2014 Ralph Schaer <ralphschaer@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

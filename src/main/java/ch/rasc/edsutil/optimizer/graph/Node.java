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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Node {

	private final String name;

	private final Set<Node> edges = new HashSet<>();

	public Node(String name) {
		this.name = name;
	}

	public void addEdge(Node edge) {
		edges.add(edge);
	}

	public void removeEdge(Node edge) {
		edges.remove(edge);
	}

	public String getName() {
		return name;
	}

	public Set<Node> getEdges() {
		return Collections.unmodifiableSet(edges);
	}

	@Override
	public String toString() {
		return "Node [name=" + name + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (name == null ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Node other = (Node) obj;
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		return true;
	}

}

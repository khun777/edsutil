package ch.rasc.edsutil.optimizer.graph;

public class CircularReferenceException extends Exception {

	private static final long serialVersionUID = 1L;

	public CircularReferenceException(Node node, Node edge) {
		super(String.format("Circular reference detected: %s -> %s", node.getName(), edge.getName()));
	}

}

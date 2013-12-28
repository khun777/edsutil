package ch.rasc.edsutil.optimizer;

public class WebResource {

	private final String path;

	private final boolean minify;

	public WebResource(String path, boolean minify) {
		this.path = path;
		this.minify = minify;
	}

	public String getPath() {
		return path;
	}

	public boolean isMinify() {
		return minify;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (minify ? 1231 : 1237);
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		WebResource other = (WebResource) obj;
		if (minify != other.minify) {
			return false;
		}
		if (path == null) {
			if (other.path != null) {
				return false;
			}
		} else if (!path.equals(other.path)) {
			return false;
		}
		return true;
	}

}

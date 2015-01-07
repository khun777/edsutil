/**
 * Copyright 2013-2015 Ralph Schaer <ralphschaer@gmail.com>
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
package ch.rasc.edsutil.optimizer;

import org.springframework.core.io.Resource;

public class WebResource {

	private final String varName;

	private final Resource resource;

	private final boolean minify;

	private final String path;

	public WebResource(String varName, String path, Resource resource, boolean minify) {
		this.varName = varName;
		this.resource = resource;
		this.minify = minify;
		this.path = path;
	}

	public String getVarName() {
		return varName;
	}

	public Resource getResource() {
		return resource;
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
		result = prime * result
				+ (resource == null ? 0 : resource.getDescription().hashCode());
		result = prime * result + (varName == null ? 0 : varName.hashCode());
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
		if (resource == null) {
			if (other.resource != null) {
				return false;
			}
		}
		else if (!resource.getDescription().equals(other.resource.getDescription())) {
			return false;
		}
		if (varName == null) {
			if (other.varName != null) {
				return false;
			}
		}
		else if (!varName.equals(other.varName)) {
			return false;
		}
		return true;
	}

}

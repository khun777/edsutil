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
package ch.rasc.edsutil.optimizer;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final byte[] data;

	private final String contentType;

	private final String etag;

	private final Integer cacheInSeconds;

	public ResourceServlet(final byte[] data, final String etag, final Integer cacheInSeconds, final String contentType) {
		this.data = data;
		this.contentType = contentType;
		this.etag = "\"" + etag + "\"";

		if (cacheInSeconds != null) {
			this.cacheInSeconds = cacheInSeconds;
		} else {
			// set it to one year
			this.cacheInSeconds = 31536000;
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		handleCacheableResponse(request, response);
	}

	public void handleCacheableResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String ifNoneMatch = request.getHeader("If-None-Match");

		if (etag.equals(ifNoneMatch)) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return;
		}

		response.setContentType(contentType);
		response.setContentLength(data.length);

		response.setDateHeader("Expires", System.currentTimeMillis() + cacheInSeconds * 1000L);
		response.setHeader("ETag", etag);
		response.setHeader("Cache-Control", "public, max-age=" + cacheInSeconds);

		@SuppressWarnings("resource")
		ServletOutputStream out = response.getOutputStream();
		out.write(data);
		out.flush();
	}
}

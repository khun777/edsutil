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

		response.setDateHeader("Expires", System.currentTimeMillis() + (cacheInSeconds * 1000L));
		response.setHeader("ETag", etag);
		response.setHeader("Cache-Control", "public, max-age=" + cacheInSeconds);

		@SuppressWarnings("resource")
		ServletOutputStream out = response.getOutputStream();
		out.write(data);
		out.flush();
	}
}

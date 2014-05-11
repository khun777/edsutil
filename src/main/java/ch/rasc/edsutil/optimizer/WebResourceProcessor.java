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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletContext;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import ch.rasc.edsutil.optimizer.graph.CircularReferenceException;
import ch.rasc.edsutil.optimizer.graph.Graph;
import ch.rasc.edsutil.optimizer.graph.Node;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class WebResourceProcessor {

	private final static Logger log = LoggerFactory.getLogger("ch.rasc.edsutil");

	private static final String HTML_SCRIPT_OR_LINK = "s";

	private static final String MODE_PRODUCTION = "p";

	private static final String MODE_DEVELOPMENT = "d";

	private static final String CSS_EXTENSION = "_css";

	private static final String JS_EXTENSION = "_js";

	private final static Pattern DEV_CODE_PATTERN = Pattern.compile("/\\* <debug> \\*/.*?/\\* </debug> \\*/",
			Pattern.DOTALL);

	private final static Pattern CSS_URL_PATTERN = Pattern.compile("(.*?url.*?\\(\\s*'?)(.*?)(\\?.*?)??('?\\s*\\))",
			Pattern.CASE_INSENSITIVE);

	private final static String REQUIRES_PATTERN = "(?s)\\brequires\\s*?:\\s*?\\[.*?\\]\\s*?,";

	private final static String USES_PATTERN = "(?s)\\buses\\s*?:\\s*?\\[.*?\\]\\s*?,";

	private final static String JAVASCRIPT_TAG = "<script src=\"%s\"></script>";

	private final static String CSSLINK_TAG = "<link rel=\"stylesheet\" href=\"%s\">";

	private String webResourcesConfigName = "/webresources.txt";

	private String versionPropertiesName = "/version.properties";

	private final boolean production;

	private int cacheInSeconds = 31536000;

	private int cssLinebreakPos = 120;

	private int jsLinebreakPos = 120;

	private boolean jsCompressorMunge = false;

	private boolean jsCompressorVerbose = false;

	private boolean jsCompressorPreserveAllSemiColons = true;

	private boolean jsCompressordisableOptimizations = true;

	private String resourceServletPath = null;

	private final Set<String> ignoreJsResourceFromReordering = new HashSet<>();

	private final ErrorReporter errorReporter = new JavaScriptCompressorErrorReporter();

	private final ServletContext servletContext;

	private final String classpathPrefix;

	private final PathMatchingResourcePatternResolver resolver;

	public WebResourceProcessor(final ServletContext servletContext, final boolean production,
			final String classpathPrefix) {
		this.servletContext = servletContext;
		this.production = production;
		this.classpathPrefix = classpathPrefix;
		this.resolver = new PathMatchingResourcePatternResolver();
	}

	public void setResourceServletPath(String path) {
		if (path != null) {
			this.resourceServletPath = path.trim();
		} else {
			this.resourceServletPath = null;
		}
	}

	public void ignoreJsResourceFromReordering(String resource) {
		ignoreJsResourceFromReordering.add(resource);
	}

	public void setCacheInSeconds(int cacheInSeconds) {
		this.cacheInSeconds = cacheInSeconds;
	}

	public void setWebResourcesConfigName(final String webResourcesConfigName) {
		this.webResourcesConfigName = webResourcesConfigName;
	}

	public void setVersionPropertiesName(final String versionPropertiesName) {
		this.versionPropertiesName = versionPropertiesName;
	}

	public void setCssLinebreakPos(final int cssLinebreakPos) {
		this.cssLinebreakPos = cssLinebreakPos;
	}

	public void setJsLinebreakPos(final int jsLinebreakPos) {
		this.jsLinebreakPos = jsLinebreakPos;
	}

	public void setJsCompressorMunge(final boolean jsCompressorMunge) {
		this.jsCompressorMunge = jsCompressorMunge;
	}

	public void setJsCompressorVerbose(final boolean jsCompressorVerbose) {
		this.jsCompressorVerbose = jsCompressorVerbose;
	}

	public void setJsCompressorPreserveAllSemiColons(final boolean jsCompressorPreserveAllSemiColons) {
		this.jsCompressorPreserveAllSemiColons = jsCompressorPreserveAllSemiColons;
	}

	public void setJsCompressordisableOptimizations(final boolean jsCompressordisableOptimizations) {
		this.jsCompressordisableOptimizations = jsCompressordisableOptimizations;
	}

	public void process() throws IOException {

		Map<String, List<WebResource>> varResources = readVariableResources();
		Map<String, List<String>> linksAndScripts = minify(varResources, true);

		for (String var : linksAndScripts.keySet()) {
			StringBuilder sb = new StringBuilder();
			if (var.endsWith(JS_EXTENSION)) {
				for (String res : linksAndScripts.get(var)) {
					sb.append(String.format(JAVASCRIPT_TAG, servletContext.getContextPath() + res));
				}
			} else {
				for (String res : linksAndScripts.get(var)) {
					sb.append(String.format(CSSLINK_TAG, servletContext.getContextPath() + res));
				}
			}
			servletContext.setAttribute(var, sb.toString());
		}

	}

	public List<String> getJsAndCssResources() throws IOException {
		Map<String, List<WebResource>> varResources = readVariableResources();
		Map<String, List<String>> linksAndScripts = minify(varResources, false);

		List<String> jsResources = new ArrayList<>();
		List<String> cssResources = new ArrayList<>();

		for (String var : linksAndScripts.keySet()) {
			if (var.endsWith(JS_EXTENSION)) {
				jsResources.addAll(linksAndScripts.get(var));
			} else {
				cssResources.addAll(linksAndScripts.get(var));

			}
		}

		cssResources.addAll(jsResources);
		return cssResources;
	}

	private Map<String, List<String>> minify(Map<String, List<WebResource>> varResources, boolean addServlet) {

		Map<String, List<String>> linksAndScripts = new LinkedHashMap<>();

		for (String var : varResources.keySet()) {
			List<String> resources = new ArrayList<>();

			StringBuilder minifiedSource = new StringBuilder();

			boolean jsProcessing = var.endsWith(JS_EXTENSION);
			for (WebResource resource : varResources.get(var)) {
				if (resource.isMinify()) {

					InputStream lis = null;

					try {
						if (classpathPrefix != null) {
							lis = new ClassPathResource(classpathPrefix + resource.getPath()).getInputStream();
						} else {
							lis = servletContext.getResourceAsStream(resource.getPath());
						}

						String sourcecode = inputStream2String(lis, StandardCharsets.UTF_8);
						if (jsProcessing) {
							minifiedSource.append(minifyJs(cleanCode(sourcecode))).append('\n');
						} else {
							minifiedSource.append(compressCss(changeImageUrls(servletContext.getContextPath(),
									sourcecode, resource.getPath())));
						}
					} catch (IOException ioe) {
						log.error("web resource processing: " + resource.getPath(), ioe);
					} finally {
						try {
							if (lis != null) {
								lis.close();
							}
						} catch (IOException e) {
							// ignore this
						}
					}

				} else {
					resources.add(resource.getPath());
				}
			}

			if (minifiedSource.length() > 0) {
				byte[] content = minifiedSource.toString().getBytes(StandardCharsets.UTF_8);

				if (jsProcessing) {
					String root = var.substring(0, var.length() - JS_EXTENSION.length());

					String crc = computeMD5andEncodeWithURLSafeBase64(content);
					String jsFileName = root + crc + ".js";
					String servletPath = constructServletPath(jsFileName);

					if (addServlet) {
						servletContext.addServlet(jsFileName,
								new ResourceServlet(content, crc, cacheInSeconds, "application/javascript"))
								.addMapping(servletPath);
					}

					resources.add(servletPath);

				} else {
					String root = var.substring(0, var.length() - CSS_EXTENSION.length());
					String crc = computeMD5andEncodeWithURLSafeBase64(content);
					String cssFileName = root + crc + ".css";
					String servletPath = constructServletPath(cssFileName);

					if (addServlet) {
						servletContext.addServlet(cssFileName,
								new ResourceServlet(content, crc, cacheInSeconds, "text/css")).addMapping(servletPath);
					}

					resources.add(servletPath);
				}
			}

			if (!resources.isEmpty()) {
				linksAndScripts.put(var, resources);
			}
		}

		return linksAndScripts;

	}

	private Map<String, List<WebResource>> readVariableResources() throws IOException {
		Stream.Builder<WebResource> streamBuilder = Stream.builder();

		Map<String, String> variables = readVariablesFromPropertyResource();
		List<String> webResourceLines = readAllLinesFromWebResourceConfigFile();

		String varName = null;

		for (String webResourceLine : webResourceLines) {
			String line = webResourceLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.endsWith(":")) {
				varName = line.substring(0, line.length() - 1);
				continue;
			}

			if (varName == null) {
				continue;
			}

			int pos = line.lastIndexOf("[");
			String mode = MODE_PRODUCTION;
			if (pos != -1) {
				mode = line.substring(pos + 1, line.length() - 1);
				line = line.substring(0, pos);
			}

			line = replaceVariables(variables, line);

			if (!production && mode.contains(MODE_DEVELOPMENT)) {
				streamBuilder.accept(new WebResource(varName, line, false));
			} else if (production && mode.contains(MODE_PRODUCTION)) {
				if (mode.contains(HTML_SCRIPT_OR_LINK)) {
					streamBuilder.accept(new WebResource(varName, line, false));
				} else {
					boolean jsProcessing = varName.endsWith(JS_EXTENSION);
					List<String> enumeratedResources = enumerateResources(line, jsProcessing ? ".js" : ".css");
					if (jsProcessing && enumeratedResources.size() > 1) {
						enumeratedResources = reorder(enumeratedResources);
					}

					for (String resource : enumeratedResources) {
						streamBuilder.accept(new WebResource(varName, resource, true));
					}
				}
			}
		}

		return streamBuilder.build().collect(Collectors.groupingBy(WebResource::getVarName));
	}

	private String constructServletPath(String path) {
		if (StringUtils.hasText(resourceServletPath)) {
			if (!resourceServletPath.endsWith("/")) {
				return resourceServletPath + "/" + path;
			}
			return resourceServletPath + path;
		}

		return "/" + path;

	}

	private List<String> enumerateResources(final String line, final String suffix) throws IOException {
		if (line.endsWith("/")) {
			List<String> resources = new ArrayList<>();

			if (classpathPrefix != null) {

				for (Resource resource : resolver.getResources("classpath:" + classpathPrefix + line + "*")) {
					System.out.println(resource);
					resources.addAll(enumerateResources("/" + resource.getFilename(), suffix));
				}
			} else {
				Set<String> resourcePaths = servletContext.getResourcePaths(line);
				if (resourcePaths != null) {

					for (String resource : resourcePaths) {
						resources.addAll(enumerateResources(resource, suffix));
					}
				}
			}

			return resources;
		}

		if (line.endsWith(suffix)) {
			return Collections.singletonList(line);
		}

		return Collections.emptyList();
	}

	private final static Pattern definePattern = Pattern.compile("Ext\\.define\\s*?\\(\\s*?['\"](.*?)['\"]");

	private final static Pattern extendPattern = Pattern.compile("extend\\s*?:\\s*?['\"](.*?)['\"]");

	private final static Pattern controllerPattern = Pattern.compile("controller\\s*?:\\s*?['\"](.*?)['\"]");

	private final static Pattern modelPattern = Pattern.compile("model\\s*?:\\s*?['\"](.*?)['\"]");

	private final static Pattern requiresPattern = Pattern.compile("(?s)requires\\s*?:\\s*?\\[(.*?)\\]");

	private final static Pattern usesPattern = Pattern.compile("(?s)uses\\s*?:\\s*?\\[(.*?)\\]");

	private final static Pattern requireUsePattern = Pattern.compile("(?s)['\"](.*?)['\"]");

	private List<String> reorder(List<String> resources) {
		if (resources.isEmpty() || resources.size() == 1) {
			return resources;
		}

		Map<String, String> classToFileMap = new HashMap<>();
		Map<String, Set<String>> resourceRequires = new HashMap<>();
		Graph g = new Graph();

		for (String resource : resources) {

			if (ignoreJsResourceFromReordering.contains(resource)) {
				continue;
			}

			g.createNode(resource);

			InputStream lis = null;
			try {

				if (classpathPrefix != null) {
					lis = new ClassPathResource(classpathPrefix + resource).getInputStream();
				} else {
					lis = servletContext.getResourceAsStream(resource);
				}

				Set<String> requires = new HashSet<>();

				String sourcecode = inputStream2String(lis, StandardCharsets.UTF_8);

				Matcher matcher = definePattern.matcher(sourcecode);
				if (matcher.find()) {
					classToFileMap.put(matcher.group(1), resource);
				}

				matcher = extendPattern.matcher(sourcecode);
				if (matcher.find()) {
					requires.add(matcher.group(1));
				}

				matcher = controllerPattern.matcher(sourcecode);
				if (matcher.find()) {
					requires.add(matcher.group(1));
				}

				matcher = modelPattern.matcher(sourcecode);
				if (matcher.find()) {
					requires.add(matcher.group(1));
				}

				matcher = requiresPattern.matcher(sourcecode);
				if (matcher.find()) {
					String all = matcher.group(1);
					matcher = requireUsePattern.matcher(all);
					while (matcher.find()) {
						requires.add(matcher.group(1));
					}
				}

				matcher = usesPattern.matcher(sourcecode);
				if (matcher.find()) {
					String all = matcher.group(1);
					matcher = requireUsePattern.matcher(all);
					while (matcher.find()) {
						requires.add(matcher.group(1));
					}
				}

				resourceRequires.put(resource, requires);

			} catch (IOException ioe) {
				log.error("web resource processing: " + resource, ioe);
			} finally {
				try {
					if (lis != null) {
						lis.close();
					}
				} catch (IOException e) {
					// ignore this
				}

			}
		}

		for (String key : resourceRequires.keySet()) {
			Node node = g.createNode(key);
			for (String r : resourceRequires.get(key)) {
				String rr = classToFileMap.get(r);
				if (rr != null) {
					node.addEdge(g.createNode(rr));
				}
			}
		}

		try {
			List<Node> resolved = g.resolveDependencies();
			for (String ignoredRes : ignoreJsResourceFromReordering) {
				resolved.add(0, new Node(ignoredRes));
			}

			return resolved.stream().sorted((o1, o2) -> o1.getEdges().size() == 0 ? -1 : 0).map(Node::getName)
					.collect(Collectors.toList());

		} catch (CircularReferenceException e) {
			log.error("circular reference", e);
			return null;
		}

	}

	private static String cleanCode(String sourcecode) {
		Matcher matcher = DEV_CODE_PATTERN.matcher(sourcecode);
		StringBuffer cleanCode = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(cleanCode, "");
		}
		matcher.appendTail(cleanCode);

		return cleanCode.toString().replaceAll(REQUIRES_PATTERN, "").replaceAll(USES_PATTERN, "");
	}

	private List<String> readAllLinesFromWebResourceConfigFile() {
		try (InputStream is = new ClassPathResource(webResourcesConfigName).getInputStream()) {
			return readAllLines(is, StandardCharsets.UTF_8);
		} catch (IOException ioe) {
			log.error("read lines from web resource config '" + webResourcesConfigName + "'", ioe);
		}
		return Collections.emptyList();
	}

	private static List<String> readAllLines(InputStream is, Charset cs) throws IOException {
		try (Reader inputStreamReader = new InputStreamReader(is, cs.newDecoder());
				BufferedReader reader = new BufferedReader(inputStreamReader)) {
			List<String> result = new ArrayList<>();
			for (;;) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				result.add(line);
			}
			return result;
		}
	}

	private static String inputStream2String(InputStream is, Charset cs) throws IOException {
		StringBuilder to = new StringBuilder();
		try (Reader from = new InputStreamReader(is, cs.newDecoder())) {
			CharBuffer buf = CharBuffer.allocate(0x800);
			while (from.read(buf) != -1) {
				buf.flip();
				to.append(buf);
				buf.clear();
			}
			return to.toString();
		}
	}

	private static String changeImageUrls(String contextPath, String cssSourceCode, String cssPath) {
		Matcher matcher = CSS_URL_PATTERN.matcher(cssSourceCode);
		StringBuffer sb = new StringBuffer();

		Path basePath = Paths.get(contextPath + cssPath);

		while (matcher.find()) {
			String url = matcher.group(2);
			url = url.trim();
			if (url.equals("#default#VML")) {
				continue;
			}
			Path pa = basePath.resolveSibling(url).normalize();
			matcher.appendReplacement(sb, "$1" + pa.toString().replace("\\", "/") + "$3$4");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String minifyJs(final String jsSourceCode) throws EvaluatorException, IOException {
		JavaScriptCompressor jsc = new JavaScriptCompressor(new StringReader(jsSourceCode), errorReporter);
		StringWriter sw = new StringWriter();
		jsc.compress(sw, jsLinebreakPos, jsCompressorMunge, jsCompressorVerbose, jsCompressorPreserveAllSemiColons,
				jsCompressordisableOptimizations);
		return sw.toString();

	}

	private String compressCss(final String css) throws EvaluatorException, IOException {
		CssCompressor cc = new CssCompressor(new StringReader(css));
		StringWriter sw = new StringWriter();
		cc.compress(sw, cssLinebreakPos);
		return sw.toString();
	}

	private static String replaceVariables(final Map<String, String> variables, final String inputLine) {
		String processedLine = inputLine;
		for (Entry<String, String> entry : variables.entrySet()) {
			String var = "{" + entry.getKey() + "}";
			processedLine = processedLine.replace(var, entry.getValue());
		}
		return processedLine;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, String> readVariablesFromPropertyResource() {
		if (versionPropertiesName != null) {
			try (InputStream is = new ClassPathResource(versionPropertiesName).getInputStream()) {
				Properties properties = new Properties();
				properties.load(is);
				return (Map) properties;
			} catch (IOException ioe) {
				log.error("read variables from property '" + versionPropertiesName + "'", ioe);
			}
		}
		return Collections.emptyMap();
	}

	private static String computeMD5andEncodeWithURLSafeBase64(final byte[] content) {
		try {
			MessageDigest md5Digest = MessageDigest.getInstance("MD5");
			md5Digest.update(content);
			byte[] md5 = md5Digest.digest();

			return Base64.getUrlEncoder().encodeToString(md5);

		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private final static class JavaScriptCompressorErrorReporter implements ErrorReporter {
		@Override
		public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
			if (line < 0) {
				log.warn("JavaScriptCompressor warning: {}", message);
			} else {
				log.warn("JavaScriptCompressor warning: {}:{}:{}", line, lineOffset, message);
			}
		}

		@Override
		public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
			if (line < 0) {
				log.error("JavaScriptCompressor error: {}", message);
			} else {
				log.error("JavaScriptCompressor error: {}:{}:{}", line, lineOffset, message);
			}
		}

		@Override
		public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource,
				int lineOffset) {
			error(message, sourceName, line, lineSource, lineOffset);
			return new EvaluatorException(message);
		}
	}

}

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.xml.bind.DatatypeConverter;

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import ch.rasc.edsutil.optimizer.graph.CircularReferenceException;
import ch.rasc.edsutil.optimizer.graph.Graph;
import ch.rasc.edsutil.optimizer.graph.Node;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
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

	private int cacheInMonths = 12;

	private int cssLinebreakPos = 120;

	private int jsLinebreakPos = 120;

	private boolean jsCompressorMunge = false;

	private boolean jsCompressorVerbose = false;

	private boolean jsCompressorPreserveAllSemiColons = true;

	private boolean jsCompressordisableOptimizations = true;

	private String resourceServletPath = null;

	public WebResourceProcessor(final boolean production) {
		this.production = production;
	}

	public void setResourceServletPath(String path) {
		if (path != null) {
			this.resourceServletPath = path.trim();
		} else {
			this.resourceServletPath = null;
		}
	}

	public void setCacheInMonths(int cacheInMonths) {
		this.cacheInMonths = cacheInMonths;
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

	public void process(final ServletContext container) {

		Map<String, StringBuilder> scriptAndLinkTags = new HashMap<>();
		Map<String, StringBuilder> sourceCodes = new HashMap<>();

		Map<String, String> variables = readVariablesFromPropertyResource();
		List<String> webResourceLines = readAllLinesFromWebResourceConfigFile();

		String varName = null;
		Set<String> processedResource = new HashSet<>();

		for (String webResourceLine : webResourceLines) {
			String line = webResourceLine.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}

			if (line.endsWith(":")) {
				varName = line.substring(0, line.length() - 1);
				scriptAndLinkTags.put(varName, new StringBuilder());
				sourceCodes.put(varName, new StringBuilder());
				processedResource.clear();
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
				scriptAndLinkTags.get(varName).append(createHtmlCode(container, line, varName));
			} else if (production && mode.contains(MODE_PRODUCTION)) {
				if (mode.contains(HTML_SCRIPT_OR_LINK)) {
					scriptAndLinkTags.get(varName).append(createHtmlCode(container, line, varName));
				} else {
					boolean jsProcessing = varName.endsWith(JS_EXTENSION);
					List<String> enumeratedResources = enumerateResources(container, line, jsProcessing ? ".js"
							: ".css");
					if (jsProcessing) {
						enumeratedResources = reorder(container, enumeratedResources);
					}

					for (String resource : enumeratedResources) {
						if (!processedResource.contains(resource)) {
							processedResource.add(resource);
							try (InputStream lis = container.getResourceAsStream(resource)) {
								String sourcecode = inputStream2String(lis, StandardCharsets.UTF_8);
								if (jsProcessing) {
									sourceCodes.get(varName).append(minifyJs(cleanCode(sourcecode))).append('\n');
								} else {
									sourceCodes.get(varName).append(compressCss(changeImageUrls(container.getContextPath(), sourcecode, line)));
								}
							} catch (IOException ioe) {
								log.error("web resource processing: " + line, ioe);
							}
						}
					}
				}
			}
		}

		for (Map.Entry<String, StringBuilder> entry : sourceCodes.entrySet()) {
			String key = entry.getKey();
			if (entry.getValue().length() > 0) {
				byte[] content = entry.getValue().toString().getBytes(StandardCharsets.UTF_8);

				if (key.endsWith(JS_EXTENSION)) {
					String root = key.substring(0, key.length() - JS_EXTENSION.length());

					String crc = computeMD5andEncodeWithURLSafeBase64(content);
					String jsFileName = root + crc + ".js";
					String servletPath = constructServletPath(jsFileName);

					container.addServlet(jsFileName,
							new ResourceServlet(content, crc, cacheInMonths, "application/javascript")).addMapping(
							servletPath);

					scriptAndLinkTags.get(key).append(
							String.format(JAVASCRIPT_TAG, container.getContextPath() + servletPath));

				} else if (key.endsWith(CSS_EXTENSION)) {
					String root = key.substring(0, key.length() - CSS_EXTENSION.length());
					String crc = computeMD5andEncodeWithURLSafeBase64(content);
					String cssFileName = root + crc + ".css";
					String servletPath = constructServletPath(cssFileName);
					container.addServlet(cssFileName, new ResourceServlet(content, crc, cacheInMonths, "text/css"))
							.addMapping(servletPath);

					scriptAndLinkTags.get(key).append(
							String.format(CSSLINK_TAG, container.getContextPath() + servletPath));
				}
			}
		}

		for (Map.Entry<String, StringBuilder> entry : scriptAndLinkTags.entrySet()) {
			container.setAttribute(entry.getKey(), entry.getValue());
		}

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

	private List<String> enumerateResources(final ServletContext container, final String line, final String suffix) {
		if (line.endsWith("/")) {
			List<String> resources = new ArrayList<>();

			Set<String> resourcePaths = container.getResourcePaths(line);
			if (resourcePaths != null) {

				for (String resource : resourcePaths) {
					resources.addAll(enumerateResources(container, resource, suffix));
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

	private static List<String> reorder(ServletContext container, List<String> resources) {
		if (resources.isEmpty() || resources.size() == 1) {
			return resources;
		}

		Map<String, String> classToFileMap = Maps.newHashMap();
		Multimap<String, String> resourceRequires = HashMultimap.create();
		Graph g = new Graph();

		for (String resource : resources) {

			g.createNode(resource);

			try (InputStream lis = container.getResourceAsStream(resource)) {
				String sourcecode = inputStream2String(lis, StandardCharsets.UTF_8);

				Matcher matcher = definePattern.matcher(sourcecode);
				if (matcher.find()) {
					classToFileMap.put(matcher.group(1), resource);
				}

				matcher = extendPattern.matcher(sourcecode);
				if (matcher.find()) {
					resourceRequires.put(resource, matcher.group(1));
				}

				matcher = controllerPattern.matcher(sourcecode);
				if (matcher.find()) {
					resourceRequires.put(resource, matcher.group(1));
				}

				matcher = modelPattern.matcher(sourcecode);
				if (matcher.find()) {
					resourceRequires.put(resource, matcher.group(1));
				}

				matcher = requiresPattern.matcher(sourcecode);
				if (matcher.find()) {
					String all = matcher.group(1);
					matcher = requireUsePattern.matcher(all);
					while (matcher.find()) {
						resourceRequires.put(resource, matcher.group(1));
					}
				}

				matcher = usesPattern.matcher(sourcecode);
				if (matcher.find()) {
					String all = matcher.group(1);
					matcher = requireUsePattern.matcher(all);
					while (matcher.find()) {
						resourceRequires.put(resource, matcher.group(1));
					}
				}

			} catch (IOException ioe) {
				log.error("web resource processing: " + resource, ioe);
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
			Collections.sort(resolved, new Comparator<Node>() {
				@Override
				public int compare(Node o1, Node o2) {
					return o1.getEdges().size() == 0 ? -1 : 0;
				}
			});

			return Lists.transform(resolved, new Function<Node, String>() {
				@Override
				public String apply(Node input) {
					return input.getName();
				}
			});

		} catch (CircularReferenceException e) {
			log.error("circular reference", e);
		}

		return resources;

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
		try (InputStream is = getClass().getResourceAsStream(webResourcesConfigName)) {
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

	private static String createHtmlCode(ServletContext container, String line, String varName) {
		String url = container.getContextPath() + line;
		if (varName.endsWith(JS_EXTENSION)) {
			return String.format(JAVASCRIPT_TAG, url);
		} else if (varName.endsWith(CSS_EXTENSION)) {
			return String.format(CSSLINK_TAG, url);
		}
		log.warn("Variable has to end with {} or {}", JS_EXTENSION, CSS_EXTENSION);
		return null;
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
		ErrorReporter errorReporter = new JavaScriptCompressorErrorReporter();

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
			try (InputStream is = getClass().getResourceAsStream(versionPropertiesName)) {
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

			String base64 = DatatypeConverter.printBase64Binary(md5);
			return base64.replace('+', '-').replace('/', '_').replace("=", "");

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

package com.opencgl.curl.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple curl command parser. Supports -X, -H, -d, --data-raw, -G, URL.
 */
public class CurlParser {

    public static class ParsedCurl {
        private String method = "GET";
        private String url;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String body;

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method != null ? method.toUpperCase() : "GET"; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Map<String, String> getHeaders() { return headers; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    private static final Pattern ARG_X = Pattern.compile("\\s+-X\\s+(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARG_H = Pattern.compile("\\s+-H\\s+['\"]?([^'\"\\\\]*(?:\\\\.[^'\"\\\\]*)*)['\"]?", Pattern.CASE_INSENSITIVE);
    // Single-quoted body: content can contain " and newlines, match until closing '
    private static final Pattern ARG_D_SINGLE = Pattern.compile("\\s+(?:-d|--data|--data-raw|--data-binary)\\s+'((?:[^'\\\\]|\\\\.)*)'", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // Double-quoted body: content can contain ' and newlines, match until closing "
    private static final Pattern ARG_D_DOUBLE = Pattern.compile("\\s+(?:-d|--data|--data-raw|--data-binary)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s'\"\\\\]+");

    public static ParsedCurl parse(String curlLine) {
        if (curlLine == null || curlLine.isBlank()) {
            throw new IllegalArgumentException("Empty curl command");
        }
        String line = normalizeLine(curlLine);
        ParsedCurl out = new ParsedCurl();

        // -X METHOD
        Matcher mx = ARG_X.matcher(line);
        if (mx.find()) {
            out.setMethod(mx.group(1).trim());
        }

        // URL (first http(s) URL)
        Matcher urlMatcher = URL_PATTERN.matcher(line);
        if (urlMatcher.find()) {
            out.setUrl(urlMatcher.group().replace("\\", ""));
        } else {
            throw new IllegalArgumentException("No URL found in curl command");
        }

        // -H "Key: Value"
        Matcher mh = ARG_H.matcher(line);
        while (mh.find()) {
            String h = mh.group(1).replace("\\\"", "\"").trim();
            int colon = h.indexOf(':');
            if (colon > 0) {
                String key = h.substring(0, colon).trim();
                String value = h.substring(colon + 1).trim();
                out.getHeaders().put(key, value);
            }
        }

        // -d or --data-raw: try single-quoted first (common for JSON), then double-quoted
        Matcher mdSingle = ARG_D_SINGLE.matcher(line);
        Matcher mdDouble = ARG_D_DOUBLE.matcher(line);
        if (mdSingle.find()) {
            String data = mdSingle.group(1);
            if (data != null) {
                out.setBody(unescapeBody(data));
            }
        } else if (mdDouble.find()) {
            String data = mdDouble.group(1);
            if (data != null) {
                out.setBody(unescapeBody(data));
            }
        }

        if ("GET".equalsIgnoreCase(out.getMethod()) && out.getBody() != null && !out.getBody().isEmpty()) {
            out.setBody(null);
        }
        return out;
    }

    private static String normalizeLine(String curlLine) {
        return curlLine.replace("\\\n", " ").replace("\\\r\n", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    private static String unescapeBody(String s) {
        if (s == null) return null;
        return s.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
    }
}

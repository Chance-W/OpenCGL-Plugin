package com.opencgl.curl.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.yaml.snakeyaml.Yaml;

import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format request/response bodies based on Content-Type.
 * - If formatting fails, returns original body (no exception to UI).
 */
public class BodyFormatter {

    private static final SerializerFeature[] JSON_FEATURES = new SerializerFeature[] {
        SerializerFeature.PrettyFormat,
        SerializerFeature.WriteDateUseDateFormat
    };

    public static String formatByContentType(String contentType, String body) {
        if (body == null) return null;
        String raw = body;
        if (raw.isEmpty()) return raw;

        try {
            if (contentType == null || contentType.isBlank()) {
                String trimmed = raw.trim();
                if (looksLikeJson(trimmed)) {
                    return formatJson(trimmed);
                }
                return raw;
            }

            String ct = contentType.toLowerCase(Locale.ROOT).trim();

            if (isJsonContentType(ct)) return formatJson(raw);
            if (isXmlContentType(ct)) return formatXml(raw);
            if (isFormUrlEncodedContentType(ct)) return formatFormUrlEncoded(raw);
            if (isMultipartFormDataContentType(ct)) return formatMultipartFormData(contentType, raw);
            if (isYamlContentType(ct)) return formatYaml(raw);

            return raw;
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static boolean looksLikeJson(String s) {
        return (s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"));
    }

    private static boolean isJsonContentType(String ctLower) {
        return ctLower.startsWith("application/json")
            || ctLower.startsWith("text/json")
            || ctLower.contains("+json");
    }

    private static boolean isXmlContentType(String ctLower) {
        return ctLower.startsWith("application/xml")
            || ctLower.startsWith("text/xml")
            || ctLower.contains("+xml")
            || ctLower.contains("/xml");
    }

    private static boolean isYamlContentType(String ctLower) {
        return ctLower.contains("yaml") || ctLower.contains("yml");
    }

    private static boolean isFormUrlEncodedContentType(String ctLower) {
        return ctLower.contains("application/x-www-form-urlencoded")
            || ctLower.contains("x-www-form-urlencoded");
    }

    private static boolean isMultipartFormDataContentType(String ctLower) {
        return ctLower.startsWith("multipart/form-data");
    }

    private static String formatJson(String body) {
        Object parsed = JSON.parse(body);
        return JSON.toJSONString(parsed, JSON_FEATURES);
    }

    private static String formatXml(String body) {
        try {
            Document document = DocumentHelper.parseText(body);
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding("UTF-8");

            StringWriter writer = new StringWriter();
            XMLWriter xmlWriter = new XMLWriter(writer, format);
            xmlWriter.write(document);
            xmlWriter.close();

            // Remove xml declaration line (if any).
            return writer.toString()
                .replaceAll("^\\s*<\\?xml[^?]*\\?>\\s*", "")
                .trim();
        } catch (Exception ignored) {
            return body;
        }
    }

    private static String formatYaml(String body) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(body);
        return yaml.dump(loaded).trim();
    }

    private static String formatFormUrlEncoded(String body) {
        String normalized = body.trim();
        if (normalized.contains("\n")) {
            // Some clients format form bodies with newlines; treat them as separators.
            normalized = normalized.replace("\r\n", "&").replace("\n", "&").replace("\r", "&");
        }

        String[] pairs = normalized.split("&");
        StringBuilder out = new StringBuilder();
        for (String pair : pairs) {
            if (pair == null) continue;
            String p = pair.trim();
            if (p.isEmpty()) continue;

            int idx = p.indexOf('=');
            String keyEnc = idx >= 0 ? p.substring(0, idx) : p;
            String valueEnc = idx >= 0 ? p.substring(idx + 1) : "";

            String key = URLDecoder.decode(keyEnc, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(valueEnc, StandardCharsets.UTF_8);

            if (out.length() > 0) out.append('\n');
            out.append(key).append(": ").append(value);
        }
        return out.length() > 0 ? out.toString() : body;
    }

    private static String formatMultipartFormData(String contentType, String body) {
        String boundary = extractBoundary(contentType);
        if (boundary == null || boundary.isBlank()) return body;

        // Avoid trying to format obviously binary-ish data.
        if (body.indexOf('\u0000') >= 0) return body;

        String delimiter = "--" + boundary;
        String[] segments = body.split(Pattern.quote(delimiter), -1);
        StringBuilder out = new StringBuilder();

        int partIndex = 0;
        for (String seg : segments) {
            if (seg == null) continue;
            String segment = seg;
            // Final boundary ends with "--".
            if (segment.trim().startsWith("--")) break;

            // Strip the leading CRLF after the delimiter.
            segment = segment.replaceFirst("^(\\r\\n|\\n|\\r)+", "");
            if (segment.isBlank()) continue;

            int headerEnd = segment.indexOf("\r\n\r\n");
            int sepLen = 4;
            if (headerEnd < 0) {
                headerEnd = segment.indexOf("\n\n");
                sepLen = 2;
            }
            if (headerEnd < 0) {
                if (out.length() > 0) out.append('\n');
                out.append(segment.trim());
                continue;
            }

            String headerStr = segment.substring(0, headerEnd);
            String partBody = segment.substring(headerEnd + sepLen).trim();

            Map<String, String> partHeaders = parseHeaders(headerStr);
            String partContentType = getHeaderValueIgnoreCase(partHeaders, "Content-Type");

            String formattedPartBody = formatByContentType(partContentType, partBody);

            String contentDisposition = getHeaderValueIgnoreCase(partHeaders, "Content-Disposition");
            String name = extractDispositionParam(contentDisposition, "name");
            String filename = extractDispositionParam(contentDisposition, "filename");

            if (out.length() > 0) out.append("\n\n");
            out.append("---- Multipart Part ").append(partIndex).append(" ----\n");
            if (name != null) out.append("name: ").append(name).append('\n');
            if (filename != null) out.append("filename: ").append(filename).append('\n');
            if (partContentType != null && !partContentType.isBlank()) {
                out.append("content-type: ").append(partContentType).append('\n');
            }
            out.append("body:\n").append(formattedPartBody);

            partIndex++;
        }

        return out.length() > 0 ? out.toString().trim() : body;
    }

    private static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        Matcher m = Pattern.compile("boundary\\s*=\\s*(\"([^\"]+)\"|([^;\\s]+))", Pattern.CASE_INSENSITIVE)
            .matcher(contentType);
        if (!m.find()) return null;
        if (m.group(2) != null) return m.group(2).trim();
        if (m.group(3) != null) return m.group(3).trim();
        return null;
    }

    private static Map<String, String> parseHeaders(String headerStr) {
        Map<String, String> out = new LinkedHashMap<>();
        if (headerStr == null || headerStr.isBlank()) return out;

        String[] lines = headerStr.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String l = line.trim();
            if (l.isEmpty()) continue;
            int idx = l.indexOf(':');
            if (idx <= 0) continue;

            String key = l.substring(0, idx).trim();
            String value = l.substring(idx + 1).trim();
            out.put(key, value);
        }
        return out;
    }

    private static String getHeaderValueIgnoreCase(Map<String, String> headers, String headerName) {
        if (headers == null || headers.isEmpty() || headerName == null || headerName.isBlank()) return null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && headerName.equalsIgnoreCase(e.getKey().trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String extractDispositionParam(String contentDisposition, String paramName) {
        if (contentDisposition == null || contentDisposition.isBlank() || paramName == null || paramName.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile(paramName + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(contentDisposition);
        if (m.find()) return m.group(1);

        Pattern p2 = Pattern.compile(paramName + "\\s*=\\s*([^;\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(contentDisposition);
        if (m2.find()) return m2.group(1);

        return null;
    }
}


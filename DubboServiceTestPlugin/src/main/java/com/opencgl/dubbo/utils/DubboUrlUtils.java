package com.opencgl.dubbo.utils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.StringUtils;

/**
 * Utility for parsing and generating dubbo:// URLs.
 */
public class DubboUrlUtils {

    /**
     * Generates a dubbo:// URL from parameters.
     */
    public static String generateUrl(String address, String interfaceName, String method, String version, String group) {
        if (StringUtils.isEmpty(address) || StringUtils.isEmpty(interfaceName)) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("dubbo://").append(address).append("/").append(interfaceName).append("?");
        
        if (StringUtils.isNotEmpty(version)) {
            sb.append("version=").append(version).append("&");
        }
        if (StringUtils.isNotEmpty(group)) {
            sb.append("group=").append(group).append("&");
        }
        if (StringUtils.isNotEmpty(method)) {
            sb.append("method=").append(method).append("&");
        }
        
        // Remove trailing & or ? if empty params
        if (sb.charAt(sb.length() - 1) == '&' || sb.charAt(sb.length() - 1) == '?') {
            sb.setLength(sb.length() - 1);
        }
        
        return sb.toString();
    }

    /**
     * Parses a dubbo:// URL and returns a Map of properties.
     * Keys: address, interface, method, version, group
     */
    public static Map<String, String> parseUrl(String urlString) {
        Map<String, String> result = new HashMap<>();
        try {
            if (!urlString.startsWith("dubbo://")) {
                // Try adding prefix if missing? Or just return empty
                if(urlString.startsWith("zookeeper://")) {
                     // Handle ZK url? Usually importing provider URL
                }
            }
            
            URI uri = URI.create(urlString);
            result.put("address", uri.getAuthority());
            result.put("interface", uri.getPath().substring(1)); // Remove leading /
            
            String query = uri.getQuery();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            // Log?
        }
        return result;
    }
    
    /**
     * Generates Java code snippet for ReferenceConfig.
     */
    public static String generateJavaCode(String address, String interfaceName, String method, String version, String group) {
        StringBuilder sb = new StringBuilder();
        sb.append("ReferenceConfig<GenericService> reference = new ReferenceConfig<>();\n");
        sb.append("reference.setInterface(\"").append(interfaceName).append("\");\n");
        if (StringUtils.isNotEmpty(version)) sb.append("reference.setVersion(\"").append(version).append("\");\n");
        if (StringUtils.isNotEmpty(group)) sb.append("reference.setGroup(\"").append(group).append("\");\n");
        sb.append("reference.setUrl(\"dubbo://").append(address).append("\");\n");
        sb.append("reference.setGeneric(\"true\");\n");
        sb.append("\n");
        sb.append("GenericService genericService = reference.get();\n");
        sb.append("Object result = genericService.$invoke(\"").append(method).append("\", new String[]{}, new Object[]{});\n");
        return sb.toString();
    }
}

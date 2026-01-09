package com.opencgl.base.hook.script;

import com.opencgl.base.hook.HookContext;
import com.opencgl.base.hook.RequestHook;
import com.opencgl.base.hook.ScriptEngine;
import com.opencgl.base.hook.TlsConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * JavaScript 脚本引擎 (基于 GraalJS)
 *
 * @author Chance.W
 */
public class JavaScriptEngine implements ScriptEngine {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptEngine.class);

    @Override
    public String getType() {
        return "javascript";
    }

    @Override
    public String[] getExtensions() {
        return new String[]{".js"};
    }

    @Override
    public RequestHook loadFromFile(Path scriptPath) throws Exception {
        String content = Files.readString(scriptPath);
        return loadFromString(content);
    }

    @Override
    public RequestHook loadFromString(String scriptContent) throws Exception {
        Context jsContext = Context.newBuilder("js")
                .allowAllAccess(true)
                .build();
        jsContext.eval("js", scriptContent);
        return new JsRequestHook(jsContext);
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.graalvm.polyglot.Context");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * JavaScript 脚本包装为 RequestHook
     */
    private static class JsRequestHook implements RequestHook {
        private final Context jsContext;

        public JsRequestHook(Context jsContext) {
            this.jsContext = jsContext;
        }

        @Override
        public String preProcess(String content, HookContext context) {
            try {
                exposeContext(context);
                Value func = jsContext.getBindings("js").getMember("preProcess");
                if (func != null && func.canExecute()) {
                    Value result = func.execute(content, context);
                    return result.asString();
                }
            } catch (Exception e) {
                logger.error("JS preProcess 执行失败", e);
            }
            return content;
        }

        @Override
        public String postProcess(String content, HookContext context) {
            try {
                exposeContext(context);
                Value func = jsContext.getBindings("js").getMember("postProcess");
                if (func != null && func.canExecute()) {
                    Value result = func.execute(content, context);
                    return result.asString();
                }
            } catch (Exception e) {
                logger.error("JS postProcess 执行失败", e);
            }
            return content;
        }

        @Override
        public TlsConfig configureTls(HookContext context) {
            try {
                exposeContext(context);
                Value func = jsContext.getBindings("js").getMember("configureTls");
                if (func != null && func.canExecute()) {
                    Value result = func.execute(context);
                    if (result == null || result.isNull()) {
                        return null;
                    }
                    // 如果脚本直接返回 Java TlsConfig 对象
                    if (result.isHostObject()) {
                        Object hostObj = result.asHostObject();
                        if (hostObj instanceof TlsConfig) {
                            return (TlsConfig) hostObj;
                        }
                    }
                    // 如果是 JavaScript 对象，手动转换
                    if (result.hasMembers()) {
                        return convertToTlsConfig(result);
                    }
                }
            } catch (Exception e) {
                logger.debug("JS configureTls 执行失败: {}", e.getMessage());
            }
            return null;
        }

        private void exposeContext(HookContext context) {
            Value bindings = jsContext.getBindings("js");
            bindings.putMember("context", context);
            bindings.putMember("scriptLog", context.getScriptLog());
            Object history = context.get("history");
            if (history != null) {
                bindings.putMember("history", history);
            }
        }

        private TlsConfig convertToTlsConfig(Value jsObj) {
            TlsConfig.TlsConfigBuilder builder = TlsConfig.builder();

            if (jsObj.hasMember("enabled")) {
                builder.enabled(jsObj.getMember("enabled").asBoolean());
            }
            if (jsObj.hasMember("mutualAuth")) {
                builder.mutualAuth(jsObj.getMember("mutualAuth").asBoolean());
            }
            if (jsObj.hasMember("keystorePath")) {
                builder.keystorePath(jsObj.getMember("keystorePath").asString());
            }
            if (jsObj.hasMember("keystorePassword")) {
                builder.keystorePassword(jsObj.getMember("keystorePassword").asString());
            }
            if (jsObj.hasMember("truststorePath")) {
                builder.truststorePath(jsObj.getMember("truststorePath").asString());
            }
            if (jsObj.hasMember("truststorePassword")) {
                builder.truststorePassword(jsObj.getMember("truststorePassword").asString());
            }
            if (jsObj.hasMember("clientCertPath")) {
                builder.clientCertPath(jsObj.getMember("clientCertPath").asString());
            }
            if (jsObj.hasMember("clientKeyPath")) {
                builder.clientKeyPath(jsObj.getMember("clientKeyPath").asString());
            }
            if (jsObj.hasMember("caCertPath")) {
                builder.caCertPath(jsObj.getMember("caCertPath").asString());
            }

            return builder.build();
        }

        @Override
        public String getName() {
            try {
                Value func = jsContext.getBindings("js").getMember("getName");
                if (func != null && func.canExecute()) {
                    return func.execute().asString();
                }
            } catch (Exception e) {
                // ignore
            }
            return "JavaScriptHook";
        }
    }
}

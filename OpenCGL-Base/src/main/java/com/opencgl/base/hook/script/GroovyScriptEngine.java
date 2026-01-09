package com.opencgl.base.hook.script;

import com.opencgl.base.hook.HookContext;
import com.opencgl.base.hook.RequestHook;
import com.opencgl.base.hook.ScriptEngine;
import com.opencgl.base.hook.TlsConfig;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Groovy 脚本引擎
 *
 * @author Chance.W
 */
public class GroovyScriptEngine implements ScriptEngine {

    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptEngine.class);

    @Override
    public String getType() {
        return "groovy";
    }

    @Override
    public String[] getExtensions() {
        return new String[]{".groovy"};
    }

    @Override
    public RequestHook loadFromFile(Path scriptPath) throws Exception {
        String content = Files.readString(scriptPath);
        return loadFromString(content);
    }

    @Override
    public RequestHook loadFromString(String scriptContent) throws Exception {
        GroovyShell shell = new GroovyShell();
        Script script = shell.parse(scriptContent);
        return new GroovyRequestHook(script);
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("groovy.lang.GroovyShell");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Groovy 脚本包装为 RequestHook
     */
    private static class GroovyRequestHook implements RequestHook {
        private final Script script;

        public GroovyRequestHook(Script script) {
            this.script = script;
        }

        @Override
        public String preProcess(String content, HookContext context) {
            try {
                Binding binding = new Binding();
                bind(binding, context);
                binding.setVariable("content", content);
                script.setBinding(binding);
                Object result = script.invokeMethod("preProcess", new Object[]{content, context});
                return result != null ? result.toString() : content;
            } catch (Exception e) {
                logger.error("Groovy preProcess 执行失败", e);
                return content;
            }
        }

        @Override
        public String postProcess(String content, HookContext context) {
            try {
                Binding binding = new Binding();
                bind(binding, context);
                binding.setVariable("content", content);
                script.setBinding(binding);
                Object result = script.invokeMethod("postProcess", new Object[]{content, context});
                return result != null ? result.toString() : content;
            } catch (Exception e) {
                logger.error("Groovy postProcess 执行失败", e);
                return content;
            }
        }

        @Override
        public TlsConfig configureTls(HookContext context) {
            try {
                Binding binding = new Binding();
                bind(binding, context);
                script.setBinding(binding);
                Object result = script.invokeMethod("configureTls", new Object[]{context});
                if (result instanceof TlsConfig) {
                    return (TlsConfig) result;
                }
                if (result != null) {
                    logger.warn("configureTls 返回了非 TlsConfig 类型: {}", result.getClass().getName());
                }
            } catch (Exception e) {
                // configureTls 是可选的，记录详细错误
                logger.debug("Groovy configureTls 执行失败: {}", e.getMessage());
                if (logger.isTraceEnabled()) {
                    logger.trace("详细错误", e);
                }
            }
            return null;
        }

        private void bind(Binding binding, HookContext context) {
            binding.setVariable("context", context);
            binding.setVariable("scriptLog", context.getScriptLog());
            binding.getVariables().putAll(context.getExtra());
        }

        @Override
        public String getName() {
            try {
                Object result = script.invokeMethod("getName", new Object[]{});
                return result != null ? result.toString() : "GroovyScript";
            } catch (Exception e) {
                return "GroovyScript";
            }
        }
    }
}

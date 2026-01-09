package com.opencgl.base.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptLogTest {

    @Test
    void scriptLogWritesToHookOutputAndKeepsLevel() {
        HookContext context = new HookContext();

        context.getScriptLog().info("MD5 签名: abc123");

        List<String> output = context.getScriptOutput();
        assertEquals(1, output.size());
        assertTrue(output.get(0).contains("MD5 签名: abc123"));
        assertTrue(output.get(0).startsWith("[INFO]"));
    }
}

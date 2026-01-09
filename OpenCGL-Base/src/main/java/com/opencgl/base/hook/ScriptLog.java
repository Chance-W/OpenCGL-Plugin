package com.opencgl.base.hook;

import com.opencgl.base.utils.OperationHisRecord;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 脚本统一日志入口：同时写入当前 Hook 输出和全局操作历史。
 */
public final class ScriptLog {
    private final HookContext context;

    ScriptLog(HookContext context) {
        this.context = context;
    }

    public void info(Object... messages) {
        write("INFO", messages);
    }

    public void warn(Object... messages) {
        write("WARN", messages);
    }

    public void error(Object... messages) {
        write("ERROR", messages);
    }

    public void debug(Object... messages) {
        write("DEBUG", messages);
    }

    public void print(Object... messages) {
        write("INFO", messages);
    }

    public void println(Object... messages) {
        write("INFO", messages);
    }

    private void write(String level, Object... messages) {
        String text = Arrays.stream(messages == null ? new Object[0] : messages)
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
        String line = "[" + level + "] " + text;
        context.println(line);
        OperationHisRecord.record(line);
    }
}

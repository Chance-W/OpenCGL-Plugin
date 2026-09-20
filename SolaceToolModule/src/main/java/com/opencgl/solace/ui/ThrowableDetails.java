package com.opencgl.solace.ui;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Produces useful, bounded error text for async JCSMP operations. */
final class ThrowableDetails {
    private static final int MAX_CAUSES = 12;

    private ThrowableDetails() {
    }

    static String summary(Throwable error) {
        Throwable current = unwrapAsync(error);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable deepest = current;
        while (deepest != null && seen.add(deepest)) {
            if (deepest.getCause() == null) break;
            deepest = deepest.getCause();
        }
        return message(deepest == null ? current : deepest);
    }

    static String describe(Throwable error) {
        Throwable current = unwrapAsync(error);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        StringBuilder details = new StringBuilder();
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSES && seen.add(current)) {
            if (!details.isEmpty()) details.append("\n由以下异常引起：");
            details.append(current.getClass().getSimpleName()).append(": ").append(message(current));
            current = current.getCause();
        }
        String text = details.toString();
        if (text.contains("GSSException") || text.toLowerCase().contains("kerberos")) {
            details.append("\n\n建议检查 Solace 认证方案；用户名/密码连接应使用 BASIC，而不是 GSS/Kerberos。");
        }
        return details.toString();
    }

    private static Throwable unwrapAsync(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String message(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getName() : message;
    }
}

package com.opencgl.decompiler;

import com.opencgl.decompiler.service.SymbolNavigationService;
import java.util.List;

/** Run with the shaded JAR as the only classpath entry to verify dependency isolation. */
public final class PackagedNavigationSmoke {
    public static void main(String[] args) {
        String code = "class A { void f(int x){} void f(String x){} void g(){ f(1); } }";
        var service = new SymbolNavigationService(List.of(new SymbolNavigationService.Source("A", "A", () -> code)));
        var result = service.resolve("A", code.indexOf("f(1)"), false);
        if (result.targets().size() != 1 || result.targets().get(0).start() != code.indexOf("f(int")) {
            throw new AssertionError(result);
        }
        System.out.println("PACKAGED_NAVIGATION_OK");
    }
}

package com.opencgl.decompiler;

import com.opencgl.decompiler.service.SymbolNavigationService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SymbolNavigationServiceTest {
    private SymbolNavigationService.Source source(String id, String name, String text) {
        return new SymbolNavigationService.Source(id, name, () -> text);
    }

    private void targets(String code, String usage, String declaration) {
        var service = new SymbolNavigationService(List.of(source("A", "A", code)));
        var result = service.resolve("A", code.indexOf(usage), false);
        assertEquals(1, result.targets().size(), result.message());
        assertEquals(code.indexOf(declaration), result.targets().get(0).start());
    }

    @Test void parameterShadowsField() {
        targets("class A { int value; void f(int value) { System.out.println(value); } }", "value);", "value) {");
    }
    @Test void qualifiedThisFindsFieldNotParameter() {
        targets("class A { int value; void f(int value) { this.value = value; } }", "value =", "value;");
    }
    @Test void localReferenceStaysInItsMethod() {
        targets("class A { void a(){ int x=1; } void b(){ int x=2; System.out.println(x); } }", "x);", "x=2");
    }
    @Test void nestedBlockVariableAndForVariableAreResolved() {
        targets("class A { void f(){ for(int i=0;i<3;i++){ System.out.println(i); } } }", "i);", "i=0");
        targets("class A { void f(){ { String s=\"a\"; System.out.println(s); } } }", "s);", "s=\"a\"");
    }
    @Test void overloadUsesArgumentTypeNotFirstNameMatch() {
        targets("class A { void f(int a){} void f(String a){} void g(){ f(\"text\"); } }", "f(\"text", "f(String");
    }
    @Test void declarationsCanShowTheirLocalUsages() {
        String code = "class A { int x; void f(){ this.x=1; System.out.println(x); } }";
        var service = new SymbolNavigationService(List.of(source("A", "A", code)));
        var result = service.resolve("A", code.indexOf("x;"), true);
        assertEquals(2, result.targets().size());
        assertEquals(List.of(code.indexOf("x=1"), code.indexOf("x);")), result.targets().stream().map(t -> t.start()).toList());
    }
    @Test void importedClassAndCrossClassMethodResolveLazily() {
        String a = "package p; import q.B; class A { void g(){ B b=new B(); b.run(1); } }";
        String b = "package q; public class B { public void run(String s){} public void run(int i){} }";
        var service = new SymbolNavigationService(List.of(source("one.jar!A", "p.A", a), source("two.jar!B", "q.B", b)));
        var result = service.resolve("one.jar!A", a.indexOf("run(1)"), false);
        assertEquals(1, result.targets().size(), result.message());
        assertEquals("two.jar!B", result.targets().get(0).sourceId());
        assertEquals(b.indexOf("run(int"), result.targets().get(0).start());
    }
    @Test void inheritedFieldResolvesToParent() {
        String a = "package p; class A extends B { int f(){ return value; } }";
        String b = "package p; class B { protected int value; }";
        var service = new SymbolNavigationService(List.of(source("A", "p.A", a), source("B", "p.B", b)));
        var result = service.resolve("A", a.indexOf("value;"), false);
        assertEquals(1, result.targets().size(), result.message());
        assertEquals("B", result.targets().get(0).sourceId());
        assertEquals(b.indexOf("value;"), result.targets().get(0).start());
    }
    @Test void duplicateQualifiedClassesOfferCandidatesNotArbitraryFirst() {
        String a = "package p; class A { q.B b; }";
        var service = new SymbolNavigationService(List.of(source("A", "p.A", a),
                source("one!B", "q.B", "package q; public class B {}"), source("two!B", "q.B", "package q; public class B {}")));
        var result = service.resolve("A", a.indexOf("B b"), false);
        assertEquals(2, result.targets().size(), result.message());
    }
    @Test void commentsStringsAndUnresolvedNamesNeverGuess() {
        String code = "class A { int value; void f(){ String s=\"value\"; /* value */ missing(); } }";
        var service = new SymbolNavigationService(List.of(source("A", "A", code)));
        assertTrue(service.resolve("A", code.indexOf("value\""), false).targets().isEmpty());
        assertTrue(service.resolve("A", code.indexOf("value */"), false).targets().isEmpty());
        assertTrue(service.resolve("A", code.indexOf("missing"), false).targets().isEmpty());
    }
    @Test void malformedDecompiledSourceReportsFailureWithoutGuessing() {
        var service = new SymbolNavigationService(List.of(source("A", "A", "class A { this is not java")));
        var result = service.resolve("A", 10, false);
        assertTrue(result.targets().isEmpty());
        assertFalse(result.message().isBlank());
    }
    @Test void constructorCallResolvesSelectedOverload() {
        targets("class A { A(int i){} A(String s){} void f(){ new A(1); } }", "A(1)", "A(int");
    }
    @Test void multiVariableDeclarationSelectsTheCorrectName() {
        targets("class A { void f(){ int a=1,b=2; System.out.println(b); } }", "b);", "b=2");
    }
    @Test void lambdaAndCatchParametersAreResolved() {
        targets("class A { void f(){ java.util.function.Function<String,String> fn = x -> x.trim(); } }", "x.trim", "x ->");
        targets("class A { void f(){ try {} catch(Exception ex){ System.out.println(ex); } } }", "ex);", "ex){");
    }
    @Test void classQualifierInStaticCallIsNavigable() {
        targets("class A { static void f(){} void g(){ A.f(); } }", "A.f", "A {");
    }
    @Test void ambiguousMethodOffersExactMemberInEachArchive() {
        String a = "package p; class A { void f(q.B b){ b.run(1); } }";
        String b = "package q; public class B { public void run(int i){} }";
        var service = new SymbolNavigationService(List.of(source("A", "p.A", a), source("one!B", "q.B", b), source("two!B", "q.B", b)));
        var result = service.resolve("A", a.indexOf("run(1)"), false);
        assertEquals(2, result.targets().size(), result.message());
        assertTrue(result.targets().stream().allMatch(t -> t.start() == b.indexOf("run(int")));
    }
    @Test void resolvingCurrentClassDoesNotDecompileUnrelatedClasses() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        String code = "class A { int x; int f(){ return x; } }";
        var service = new SymbolNavigationService(List.of(source("A", "A", code),
                new SymbolNavigationService.Source("B", "B", () -> { calls.incrementAndGet(); return "class B {}"; })));
        assertEquals(1, service.resolve("A", code.indexOf("x; }"), false).targets().size());
        assertEquals(0, calls.get());
    }
}

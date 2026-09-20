package com.opencgl.decompiler.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.util.*;
import java.util.function.Supplier;

/** Workspace-scoped semantic navigation. All parsing/resolution is called on a worker, never on FX. */
public final class SymbolNavigationService {
    public record Source(String id, String qualifiedName, Supplier<String> loader) {}
    public record Target(String sourceId, int start, int end, String label) {}
    public record Result(List<Target> targets, String message) {
        static Result missing(String message) { return new Result(List.of(), message); }
    }
    private record Document(Source source, String text, CompilationUnit unit) {}
    private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Map<String, List<Source>> types = new HashMap<>();
    private final Map<String, Document> documents = new LinkedHashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final LazySolver solver = new LazySolver();
    private final JavaParser parser;
    private List<Source> ambiguity = List.of();

    public SymbolNavigationService(List<Source> sources) {
        for (Source source : sources) {
            this.sources.put(source.id(), source);
            types.computeIfAbsent(source.qualifiedName().replace('$', '.'), key -> new ArrayList<>()).add(source);
        }
        parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setSymbolResolver(new JavaSymbolSolver(solver)).setTabSize(1));
    }

    public synchronized String sourceText(String id) { return document(id).text(); }

    public synchronized Result resolve(String id, int offset, boolean usagesAtDeclaration) {
        ambiguity = List.of();
        try {
            Document doc = document(id);
            SimpleName name = nameAt(doc, offset);
            if (name == null) return Result.missing("当前位置不是可跳转的符号");
            Node parent = name.getParentNode().orElse(null);
            if (isDeclaration(parent) && usagesAtDeclaration) return usages(doc, name);
            try {
                Target target = resolveName(doc, name);
                if (target != null) return new Result(List.of(target), "跳转到定义");
            } catch (RuntimeException ignored) {
                // Missing dependencies or unsupported decompiler syntax must not fall back to text guesses.
            }
            if (!ambiguity.isEmpty()) {
                List<Source> candidates = List.copyOf(ambiguity);
                List<Target> targets = new ArrayList<>();
                for (Source candidate : candidates) {
                    // A resolver caches inferred types on AST nodes. Isolate candidates so one JAR's
                    // inferred receiver type cannot leak into the next JAR's method selection.
                    List<Source> branchSources = sources.values().stream()
                            .filter(s -> !candidates.contains(s) || s == candidate)
                            .map(s -> new Source(s.id(), s.qualifiedName(), () -> document(s.id()).text())).toList();
                    var branch = new SymbolNavigationService(branchSources);
                    for (Target target : branch.resolve(id, offset, false).targets()) {
                        if (!targets.contains(target)) targets.add(target);
                    }
                }
                if (!targets.isEmpty()) return new Result(List.copyOf(targets), "存在多个定义，请选择来源");
            }
            return Result.missing("无法确定定义：依赖未加载、符号有歧义或反编译语法不受支持");
        } catch (RuntimeException ex) {
            return Result.missing("无法解析该类：" + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()));
        } finally { ambiguity = List.of(); }
    }

    private Result usages(Document doc, SimpleName declaration) {
        Target definition = target(doc, declaration);
        List<Target> found = new ArrayList<>();
        for (SimpleName name : doc.unit().findAll(SimpleName.class)) {
            if (!name.asString().equals(declaration.asString()) || isDeclaration(name.getParentNode().orElse(null))) continue;
            try {
                Target resolved = resolveName(doc, name);
                if (resolved != null && resolved.sourceId().equals(definition.sourceId()) && resolved.start() == definition.start()) {
                    found.add(target(doc, name));
                }
            } catch (RuntimeException ignored) { }
        }
        return new Result(List.copyOf(found), found.isEmpty() ? "当前类中未找到引用" : "当前类中的引用");
    }

    private Target resolveName(Document doc, SimpleName name) {
        Node parent = name.getParentNode().orElse(null);
        if (isDeclaration(parent)) return target(doc, name);
        ResolvedDeclaration resolved;
        if (parent instanceof NameExpr expr) {
            try { resolved = expr.resolve(); }
            catch (com.github.javaparser.resolution.UnsolvedSymbolException missingValue) {
                resolved = com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory.getContext(expr, solver)
                        .solveType(expr.getNameAsString()).getCorrespondingDeclaration();
            }
        }
        else if (parent instanceof FieldAccessExpr expr && expr.getName() == name) resolved = expr.resolve();
        else if (parent instanceof MethodCallExpr expr && expr.getName() == name) resolved = expr.resolve();
        else if (parent instanceof ClassOrInterfaceType type && type.getName() == name) {
            if (type.getParentNode().orElse(null) instanceof ObjectCreationExpr creation && creation.getType() == type) {
                resolved = creation.resolve();
            } else resolved = type.resolve().asReferenceType().getTypeDeclaration().orElseThrow();
        } else return null;
        Node ast = resolved.toAst().orElse(null);
        if (ast == null) return null; // JDK/library with no loaded source: no fabricated location.
        SimpleName declaration = declarationName(ast, resolved.getName());
        if (declaration == null) return null;
        CompilationUnit unit = declaration.findCompilationUnit().orElse(null);
        for (Document candidate : documents.values()) {
            if (candidate.unit() == unit) return target(candidate, declaration);
        }
        return null;
    }

    private static SimpleName declarationName(Node node, String name) {
        if (node instanceof VariableDeclarator n) return n.getName();
        if (node instanceof VariableDeclarationExpr n) return n.getVariables().stream()
                .filter(v -> v.getNameAsString().equals(name)).map(VariableDeclarator::getName).findFirst().orElse(null);
        if (node instanceof Parameter n) return n.getName();
        if (node instanceof MethodDeclaration n) return n.getName();
        if (node instanceof ConstructorDeclaration n) return n.getName();
        if (node instanceof TypeDeclaration<?> n) return n.getName();
        if (node instanceof EnumConstantDeclaration n) return n.getName();
        if (node instanceof FieldDeclaration n) return n.getVariables().stream()
                .filter(v -> v.getNameAsString().equals(name)).map(VariableDeclarator::getName).findFirst().orElse(null);
        return null;
    }

    private static boolean isDeclaration(Node parent) {
        return parent instanceof VariableDeclarator || parent instanceof Parameter || parent instanceof MethodDeclaration
                || parent instanceof ConstructorDeclaration || parent instanceof TypeDeclaration<?> || parent instanceof EnumConstantDeclaration;
    }

    private SimpleName nameAt(Document doc, int offset) {
        return doc.unit().findAll(SimpleName.class).stream().filter(name -> name.getRange().map(range -> {
            int start = offset(doc.text(), range.begin), end = offset(doc.text(), range.end) + 1;
            return offset >= start && offset < end;
        }).orElse(false)).findFirst().orElse(null);
    }

    private Target target(Document doc, SimpleName name) {
        var range = name.getRange().orElseThrow();
        int start = offset(doc.text(), range.begin), end = offset(doc.text(), range.end) + 1;
        return new Target(doc.source().id(), start, end, name + " — " + doc.source().qualifiedName()
                + ":" + range.begin.line + "  [" + doc.source().id() + "]");
    }

    private static int offset(String text, Position position) {
        int at = 0;
        for (int line = 1; line < position.line; line++) {
            int next = text.indexOf('\n', at);
            if (next < 0) return text.length();
            at = next + 1;
        }
        return Math.min(text.length(), at + position.column - 1);
    }

    private Document document(String id) {
        Document cached = documents.get(id);
        if (cached != null) return cached;
        Source source = sources.get(id);
        if (source == null) throw new IllegalArgumentException("类已从工作区移除");
        if (!loading.add(id)) throw new IllegalStateException("循环解析 " + source.qualifiedName());
        try {
            String text = source.loader().get();
            var parsed = parser.parse(text);
            if (!parsed.isSuccessful()) throw new IllegalArgumentException("反编译内容不是可解析的 Java 源码");
            Document document = new Document(source, text, parsed.getResult().orElseThrow());
            documents.put(id, document);
            return document;
        } finally { loading.remove(id); }
    }

    private final class LazySolver implements TypeSolver {
        private final ReflectionTypeSolver reflection = new ReflectionTypeSolver(true);
        private TypeSolver parent;
        LazySolver() { reflection.setParent(this); }
        @Override public TypeSolver getParent() { return parent; }
        @Override public void setParent(TypeSolver parent) { this.parent = parent; }
        @Override public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeInModule(String name, String moduleName) {
            return tryToSolveType(name);
        }
        @Override public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
            List<Source> candidates = types.getOrDefault(name.replace('$', '.'), List.of());
            if (candidates.size() > 1) {
                ambiguity = candidates;
                return SymbolReference.unsolved();
            }
            if (!candidates.isEmpty()) {
                Source source = candidates.get(0);
                Document doc = document(source.id());
                for (TypeDeclaration<?> type : doc.unit().findAll(TypeDeclaration.class)) {
                    if (type.getFullyQualifiedName().orElse("").equals(name.replace('$', '.'))) {
                        return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(type));
                    }
                }
                return SymbolReference.unsolved();
            }
            return reflection.tryToSolveType(name);
        }
    }
}

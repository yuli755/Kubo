package cn.hamm.spms.tools;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <h1>全量 Java 17 语法分析及已知非重复注解的 AST 检查</h1>
 *
 * <p>只调用 javac parse，不伪造 Spring/JPA 类、不运行 analyze。它不是依赖完整的类型检查，
 * 用来让离线验证至少不再停留在“数括号”；完整 Maven compile/test 仍是必需的门禁。</p>
 *
 * @author Hamm.cn
 */
public final class JavaSourceCheck {
    private static final Set<String> NON_REPEATABLE = Set.of(
            "cn.hamm.airpower.core.annotation.Description",
            "cn.hamm.airpower.core.annotation.Export",
            "jakarta.persistence.ManyToOne",
            "jakarta.persistence.Column");

    private JavaSourceCheck() {
    }

    public static void main(String[] args) throws IOException {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            throw new IllegalStateException("需要完整 JDK 17，不能使用 JRE");
        }
        int checks = selfCheck();
        List<Path> files = new ArrayList<>();
        for (String arg : args) {
            try (var paths = Files.walk(Path.of(arg))) {
                files.addAll(paths.filter(path -> path.toString().endsWith(".java")).sorted().toList());
            }
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("没有 Java 源文件，拒绝空转");
        }
        try (StandardJavaFileManager manager = ToolProvider.getSystemJavaCompiler()
                .getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<String> problems = inspect(manager, manager.getJavaFileObjectsFromPaths(files));
            if (!problems.isEmpty()) {
                problems.forEach(System.err::println);
                System.exit(1);
            }
        }
        System.out.println("Java 17 source parse: " + files.size() + " files passed; " + checks
                + " self-checks (syntax + known annotations only; NOT Maven type checking)");
    }

    private static List<String> inspect(StandardJavaFileManager manager, Iterable<? extends JavaFileObject> sources) throws IOException {
        List<String> problems = new ArrayList<>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(
                null, manager, diagnostics, List.of("--release", "17", "-proc:none"), null, sources);
        Trees trees = Trees.instance(task);
        for (CompilationUnitTree unit : task.parse()) {
            Map<String, String> imports = new HashMap<>();
            List<String> wildcardPackages = new ArrayList<>();
            unit.getImports().stream().filter(value -> !value.isStatic()).forEach(value -> {
                String name = value.getQualifiedIdentifier().toString();
                if (name.endsWith(".*")) {
                    wildcardPackages.add(name.substring(0, name.length() - 1));
                } else {
                    imports.put(name.substring(name.lastIndexOf('.') + 1), name);
                }
            });
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitModifiers(ModifiersTree modifiers, Void unused) {
                    Set<String> seen = new HashSet<>();
                    for (AnnotationTree annotation : modifiers.getAnnotations()) {
                        String name = annotation.getAnnotationType().toString();
                        String qualified = imports.getOrDefault(name, name);
                        if (!NON_REPEATABLE.contains(qualified) && !imports.containsKey(name) && name.indexOf('.') < 0) {
                            for (String prefix : wildcardPackages) {
                                if (NON_REPEATABLE.contains(prefix + name)) {
                                    qualified = prefix + name;
                                    break;
                                }
                            }
                        }
                        if (NON_REPEATABLE.contains(qualified) && !seen.add(qualified)) {
                            long position = trees.getSourcePositions().getStartPosition(unit, annotation);
                            long line = unit.getLineMap().getLineNumber(position);
                            problems.add(unit.getSourceFile().getName() + ':' + line
                                    + ": duplicate non-repeatable annotation " + qualified);
                        }
                    }
                    return super.visitModifiers(modifiers, unused);
                }
            }.scan(unit, null);
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                problems.add((diagnostic.getSource() == null ? "<javac>" : diagnostic.getSource().getName()) + ':' + diagnostic.getLineNumber()
                        + ": " + diagnostic.getMessage(null));
            }
        }
        return problems;
    }

    private static int selfCheck() throws IOException {
        record Case(String source, boolean fails) {
        }
        List<Case> cases = List.of(
                new Case("class Fixture { void broken( }", true),
                new Case("import missing.Dependency; class Fixture { Dependency value; }", false),
                new Case("import jakarta.persistence.ManyToOne; class Fixture { @ManyToOne @ManyToOne Object ref; }", true),
                new Case("import cn.hamm.airpower.core.annotation.*; import another.Description; class Fixture { @Description @Description Object value; }", false),
                new Case("import cn.hamm.airpower.core.annotation.*; class Fixture { @Description(\"旧字段\") @Description(\"新字段\") String value; }", true),
                new Case("import cn.hamm.airpower.core.annotation.Description; class Fixture { @Description(\"值\") String value; @Description(\"值\") String getValue() { return value; } }", false),
                new Case("import java.lang.annotation.Repeatable; @interface Tags { Tag[] value(); } @Repeatable(Tags.class) @interface Tag {} @Tag @Tag class Fixture {}", false),
                new Case("class Fixture { String value = \"\"\"\n @ManyToOne @ManyToOne \n\"\"\"; }", false));
        for (Case sample : cases) {
            JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///Fixture.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return sample.source();
                }
            };
            try (StandardJavaFileManager manager = ToolProvider.getSystemJavaCompiler()
                    .getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
                boolean failed = !inspect(manager, List.of(source)).isEmpty();
                if (failed != sample.fails()) {
                    throw new AssertionError("Java source gate self-check failed: " + sample.source());
                }
            }
        }
        return cases.size();
    }
}

package com.seleniumboot.migrator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.seleniumboot.migrator.Finding.Status.AUTO;
import static com.seleniumboot.migrator.Finding.Status.MANUAL;

/** Read-only static analysis: parses Java sources and reports patterns that map onto Selenium Boot. */
public final class Analyzer {

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    public Report analyze(Path root) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        List<Finding> findings = new ArrayList<>();
        int parsed = 0;
        List<String> unparsable = new ArrayList<>();
        for (Path f : files) {
            var result = parser.parse(f);
            if (result.getResult().isEmpty() || !result.isSuccessful()) {
                unparsable.add(root.relativize(f).toString());
                continue;
            }
            parsed++;
            scan(result.getResult().get(), root.relativize(f).toString(), findings);
        }
        return new Report(files.size(), parsed, unparsable, findings);
    }

    /** Analyze a single pasted source string. */
    public Report analyzeSource(String source) {
        var result = parser.parse(source);
        List<Finding> findings = new ArrayList<>();
        if (result.getResult().isEmpty() || !result.isSuccessful()) {
            return new Report(1, 0, List.of("<pasted>"), findings);
        }
        scan(result.getResult().get(), "<pasted>", findings);
       return new Report(1, 1, List.of(), findings, "not detected");
    }

    private void scan(CompilationUnit cu, String file, List<Finding> out) {
        // MIG-001: ThreadLocal<WebDriver> driver factory
        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            String type = fd.getElementType().toString();
            if (type.startsWith("ThreadLocal<") && type.contains("WebDriver")) {
                out.add(new Finding("MIG-001", AUTO, file, line(fd), "ThreadLocal<WebDriver>",
                        "Delete the driver factory; extend BaseTest (per-thread isolation is built in)."));
            }
        });
        // MIG-002: WebDriverManager.*.setup()
        cu.findAll(NameExpr.class).stream()
                .filter(n -> n.getNameAsString().equals("WebDriverManager"))
                .forEach(n -> out.add(new Finding("MIG-002", AUTO, file, line(n), "WebDriverManager",
                        "Delete; Selenium Manager fetches drivers automatically.")));
        // MIG-003: WebDriverWait / ExpectedConditions
        cu.findAll(ObjectCreationExpr.class).stream()
                .filter(o -> o.getType().getNameAsString().equals("WebDriverWait"))
                .forEach(o -> out.add(new Finding("MIG-003", MANUAL, file, line(o), "new WebDriverWait(...)",
                        "Use $(locator) auto-wait or getWait(); review the condition by hand.")));
        cu.findAll(NameExpr.class).stream()
                .filter(n -> n.getNameAsString().equals("ExpectedConditions"))
                .forEach(n -> out.add(new Finding("MIG-003", MANUAL, file, line(n), "ExpectedConditions",
                        "Map to $(locator) auto-wait or a WaitEngine call.")));
        // MIG-004: retry analyzer / annotation transformer
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
            boolean retry = c.getImplementedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals("IRetryAnalyzer")
                            || t.getNameAsString().equals("IAnnotationTransformer"));
            if (retry) {
                out.add(new Finding("MIG-004", AUTO, file, line(c), c.getNameAsString(),
                        "Delete; set retry.enabled in selenium-boot.yml or use @Retryable."));
            }
            // MIG-005: screenshot-on-failure listener
            boolean listener = c.getImplementedTypes().stream().anyMatch(t -> t.getNameAsString().equals("ITestListener"));
            boolean shots = c.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals("TakesScreenshot"))
                    || c.toString().contains("TakesScreenshot");
            if (listener && shots) {
                out.add(new Finding("MIG-005", AUTO, file, line(c), c.getNameAsString(),
                        "Delete; failure screenshots are captured automatically."));
            }
        });
        // MIG-014: hard-coded sleeps, MIG-016: implicit waits (flag only)
        cu.findAll(MethodCallExpr.class).forEach(m -> {
            if (m.getNameAsString().equals("sleep") && m.getScope().map(s -> s.toString().equals("Thread")).orElse(false)) {
                out.add(new Finding("MIG-014", MANUAL, file, line(m), "Thread.sleep(...)",
                        "Replace with an auto-waiting locator or getWait()."));
            }
            if (m.getNameAsString().equals("implicitlyWait")) {
                out.add(new Finding("MIG-016", MANUAL, file, line(m), "implicitlyWait(...)",
                        "Remove; mixing implicit and explicit waits causes flakiness."));
            }
        });
        // MIG-015: custom DriverManager (flag only)
        cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> c.getNameAsString().endsWith("DriverManager") || c.getNameAsString().endsWith("DriverFactory"))
                .forEach(c -> out.add(new Finding("MIG-015", MANUAL, file, line(c), c.getNameAsString(),
                        "Custom driver lifecycle: review, then replace with BaseTest.")));
    }

    private static int line(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }
}

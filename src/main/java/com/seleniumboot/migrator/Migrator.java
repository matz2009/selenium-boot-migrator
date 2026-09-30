package com.seleniumboot.migrator;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Copies a project and applies only transformations that can be performed mechanically. */
public final class Migrator {

    private static final String SELENIUM_BOOT_VERSION = "3.5.0";
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", "target", "node_modules");

    public record Result(Path output, List<String> applied, List<String> notes, Report remaining) { }

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    public Result migrate(Path project, Path output) throws IOException {
        Path source = project.toAbsolutePath().normalize();
        Path destination = output.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("not a directory: " + source);
        }
        if (destination.equals(source) || destination.startsWith(source)) {
            throw new IllegalArgumentException("output must not be the source directory or inside it: " + destination);
        }
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("output already exists: " + destination);
        }
        Report sourceAnalysis = new Analyzer().analyze(source);

        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        copyProject(source, destination);

        List<String> applied = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> removedTypes = transformJava(destination, applied);
        List<Finding> danglingReferences = findDanglingReferences(destination, removedTypes);
        if (!migratePom(destination.resolve("pom.xml"))) {
            notes.add("pom.xml: no org.seleniumhq.selenium:selenium-java dependency found to replace.");
        } else {
            applied.add("pom.xml: replaced selenium-java with io.github.seleniumboot:selenium-boot:" + SELENIUM_BOOT_VERSION);
        }
        Report outputAnalysis = new Analyzer().analyze(destination);
        return new Result(destination, List.copyOf(applied), List.copyOf(notes),
                includeSourceManualFindings(sourceAnalysis, outputAnalysis, danglingReferences));
    }

    private static Report includeSourceManualFindings(Report source, Report output, List<Finding> additionalFindings) {
        List<Finding> findings = new ArrayList<>(source.findings().stream()
                .filter(finding -> finding.status() == Finding.Status.MANUAL).toList());
        output.findings().stream().filter(finding -> !findings.contains(finding)).forEach(findings::add);
        additionalFindings.stream().filter(finding -> !findings.contains(finding)).forEach(findings::add);
        Set<String> unparsable = new LinkedHashSet<>(source.unparsable());
        unparsable.addAll(output.unparsable());
        return new Report(output.filesFound(), output.filesParsed(), List.copyOf(unparsable), List.copyOf(findings));
    }

    private static void copyProject(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(source) && EXCLUDED_DIRECTORIES.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)),
                    StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Set<String> transformJava(Path root, List<String> applied) throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(root)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        Set<String> removedTypes = new LinkedHashSet<>();
        for (Path file : sources) {
            var parsed = parser.parse(file);
            if (parsed.getResult().isEmpty() || !parsed.isSuccessful()) continue;
            CompilationUnit unit = parsed.getResult().get();
            LexicalPreservingPrinter.setup(unit);
            boolean changed = removeAutoTypes(unit, applied, removedTypes, root.relativize(file).toString());
            changed |= removeWebDriverManagerSetup(unit, applied, root.relativize(file).toString());
            if (changed) changed |= removeUnusedExplicitImports(unit);
            if (changed && unit.getTypes().isEmpty()) {
                Files.delete(file);
            } else if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(unit));
            }
        }
        return removedTypes;
    }

    private static boolean removeAutoTypes(CompilationUnit unit, List<String> applied, Set<String> removedTypes, String file) {
        boolean changed = false;
        for (var type : new ArrayList<>(unit.getTypes())) {
            if (!(type instanceof ClassOrInterfaceDeclaration declaration)) continue;
            boolean driverFactory = declaration.getNameAsString().endsWith("DriverFactory")
                    && declaration.findAll(FieldDeclaration.class).stream().anyMatch(Migrator::isThreadLocalDriver);
            boolean retry = declaration.getImplementedTypes().stream()
                    .anyMatch(implemented -> implemented.getNameAsString().equals("IRetryAnalyzer")
                            || implemented.getNameAsString().equals("IAnnotationTransformer"));
            boolean screenshotListener = declaration.getImplementedTypes().stream()
                    .anyMatch(implemented -> implemented.getNameAsString().equals("ITestListener"))
                    && (declaration.toString().contains("TakesScreenshot")
                    || declaration.toString().contains("getScreenshotAs"));
            if (driverFactory || retry || screenshotListener) {
                String packageName = unit.getPackageDeclaration()
                    .map(packageDeclaration -> packageDeclaration.getNameAsString() + ".").orElse("");
                removedTypes.add(packageName + declaration.getNameAsString());
                declaration.remove();
                applied.add(file + ": removed " + declaration.getNameAsString());
                changed = true;
            }
        }
        return changed;
    }

    private List<Finding> findDanglingReferences(Path root, Set<String> removedTypes) throws IOException {
        if (removedTypes.isEmpty()) return List.of();
        List<Finding> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                var parsed = parser.parse(file);
                if (parsed.getResult().isEmpty() || !parsed.isSuccessful()) continue;
                CompilationUnit unit = parsed.getResult().get();
                for (String removedType : removedTypes) {
                    NodeReference reference = findTypeReference(unit, removedType);
                    if (reference != null) {
                        findings.add(new Finding("MIG-017", Finding.Status.MANUAL,
                                root.relativize(file).toString(), reference.line(), removedType,
                                "This file still references a class removed during migration; update the caller before compiling."));
                    }
                }
            }
        }
        return findings;
    }

    private static NodeReference findTypeReference(CompilationUnit unit, String targetType) {
        String simpleName = targetType.substring(targetType.lastIndexOf('.') + 1);
        String packageName = targetType.contains(".")
                ? targetType.substring(0, targetType.lastIndexOf('.')) : "";
        boolean samePackage = unit.getPackageDeclaration()
                .map(declaration -> declaration.getNameAsString().equals(packageName)).orElse(packageName.isEmpty());
        boolean imported = unit.getImports().stream().anyMatch(declaration -> {
            String importedName = declaration.getNameAsString();
            return (!declaration.isAsterisk() && (importedName.equals(targetType)
                    || declaration.isStatic() && importedName.startsWith(targetType + ".")))
                    || declaration.isAsterisk() && (importedName.equals(packageName)
                    || declaration.isStatic() && importedName.equals(targetType));
        });

        for (var declaration : unit.getImports()) {
            String importedName = declaration.getNameAsString();
            if (importedName.equals(targetType) || declaration.isStatic()
                    && (importedName.startsWith(targetType + ".")
                    || declaration.isAsterisk() && importedName.equals(targetType))) {
                return new NodeReference(declaration.getBegin().map(position -> position.line).orElse(0));
            }
        }
        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            if (matchesTypeReference(type.toString(), targetType, simpleName, samePackage, imported)) {
                return new NodeReference(type.getBegin().map(position -> position.line).orElse(0));
            }
        }
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            if (matchesTypeReference(annotation.getNameAsString(), targetType, simpleName, samePackage, imported)) {
                return new NodeReference(annotation.getBegin().map(position -> position.line).orElse(0));
            }
        }
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            if (call.getScope().map(scope -> matchesTypeReference(scope.toString(), targetType,
                    simpleName, samePackage, imported)).orElse(false)) {
                return new NodeReference(call.getBegin().map(position -> position.line).orElse(0));
            }
        }
        for (FieldAccessExpr access : unit.findAll(FieldAccessExpr.class)) {
            if (matchesTypeReference(access.getScope().toString(), targetType, simpleName, samePackage, imported)) {
                return new NodeReference(access.getBegin().map(position -> position.line).orElse(0));
            }
        }
        return null;
    }

    private static boolean matchesTypeReference(String reference, String targetType, String simpleName,
                                                boolean samePackage, boolean imported) {
        return reference.equals(targetType) || reference.equals(simpleName) && (samePackage || imported);
    }

    private record NodeReference(int line) { }

    private static boolean removeUnusedExplicitImports(CompilationUnit unit) {
        boolean changed = false;
        for (var declaration : new ArrayList<>(unit.getImports())) {
            if (declaration.isAsterisk() || declaration.isStatic()) continue;
            String simpleName = declaration.getName().getIdentifier();
            boolean used = unit.findAll(ClassOrInterfaceType.class).stream()
                    .anyMatch(type -> type.getNameAsString().equals(simpleName))
                    || unit.findAll(NameExpr.class).stream().anyMatch(name -> name.getNameAsString().equals(simpleName))
                    || unit.findAll(AnnotationExpr.class).stream()
                    .anyMatch(annotation -> annotation.getName().getIdentifier().equals(simpleName));
            if (!used) {
                declaration.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isThreadLocalDriver(FieldDeclaration field) {
        String type = field.getElementType().toString();
        return type.startsWith("ThreadLocal<") && type.contains("WebDriver");
    }

    private static boolean removeWebDriverManagerSetup(CompilationUnit unit, List<String> applied, String file) {
        boolean changed = false;
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("setup")
                    || call.getScope().map(scope -> !scope.toString().contains("WebDriverManager")).orElse(true)) {
                continue;
            }
            var statement = call.findAncestor(ExpressionStmt.class);
            if (statement.isPresent() && statement.get().getExpression() == call) {
                statement.get().remove();
                applied.add(file + ": removed WebDriverManager setup call");
                changed = true;
            }
        }
        if (changed) {
            unit.getImports().removeIf(importDeclaration ->
                    importDeclaration.getNameAsString().endsWith("WebDriverManager"));
        }
        return changed;
    }

    private static boolean migratePom(Path pom) throws IOException {
        if (!Files.isRegularFile(pom)) return false;
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = builderFactory.newDocumentBuilder().parse(pom.toFile());
            NodeList dependencies = document.getElementsByTagName("dependency");
            boolean changed = false;
            for (int index = 0; index < dependencies.getLength(); index++) {
                Element dependency = (Element) dependencies.item(index);
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (groupId.equals("org.seleniumhq.selenium") && artifactId.equals("selenium-java")) {
                    setChildText(dependency, "groupId", "io.github.seleniumboot");
                    setChildText(dependency, "artifactId", "selenium-boot");
                    setChildText(dependency, "version", SELENIUM_BOOT_VERSION);
                    changed = true;
                }
            }
            if (!changed) return false;
            var transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(document), new StreamResult(pom.toFile()));
            return true;
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("could not update " + pom + ": " + exception.getMessage(), exception);
        }
    }

    private static String childText(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child && child.getTagName().equals(name)) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private static void setChildText(Element parent, String name, String value) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child && child.getTagName().equals(name)) {
                child.setTextContent(value);
                return;
            }
        }
        Element child = parent.getOwnerDocument().createElement(name);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
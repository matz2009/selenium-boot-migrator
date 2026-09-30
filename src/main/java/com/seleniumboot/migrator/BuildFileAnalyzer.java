package com.seleniumboot.migrator;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BuildFileAnalyzer {

    private static final Set<String> IGNORED_DIRECTORIES = Set.of("target", "build", ".gradle", "node_modules");
    private static final Pattern GRADLE_DEPENDENCY = Pattern.compile(
            "(?m)^\\s*(implementation|api|compileOnly|runtimeOnly|testImplementation|testCompileOnly|"
                    + "testRuntimeOnly|classpath|annotationProcessor|kapt)\\s*(?:\\(\\s*)?['\\\"]([^'\\\"]+)['\\\"]");

    private BuildFileAnalyzer() { }

    static List<String> detect(Path root) throws IOException {
        List<String> detected = new ArrayList<>();
        Set<String> dependencies = new LinkedHashSet<>();
        List<Path> buildFiles = findBuildFiles(root);

        for (Path buildFile : buildFiles) {
            String name = buildFile.getFileName().toString();
            if (name.equals("pom.xml")) {
                try {
                    dependencies.addAll(mavenDependencies(buildFile));
                    detected.add("Build system: Maven");
                } catch (IOException exception) {
                    detected.add("Build system: Maven (could not parse pom.xml)");
                }
            } else if (name.equals("build.gradle")) {
                detected.add("Build system: Gradle (Groovy DSL)");
                dependencies.addAll(gradleDependencies(buildFile));
            } else {
                detected.add("Build system: Gradle (Kotlin DSL)");
                dependencies.addAll(gradleDependencies(buildFile));
            }
        }

        dependencies.stream().sorted().map(dependency -> "Dependency: " + dependency).forEach(detected::add);
        return List.copyOf(detected);
    }

    private static List<Path> findBuildFiles(Path root) throws IOException {
        List<Path> buildFiles = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root) && IGNORED_DIRECTORIES.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && isBuildFile(file.getFileName().toString())) {
                    buildFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        buildFiles.sort(Path::compareTo);
        return buildFiles;
    }

    private static boolean isBuildFile(String name) {
        return name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts");
    }

    private static List<String> gradleDependencies(Path buildFile) throws IOException {
        String source = Files.readString(buildFile);
        Matcher matcher = GRADLE_DEPENDENCY.matcher(source);
        List<String> dependencies = new ArrayList<>();
        while (matcher.find()) {
            String coordinate = matcher.group(2).trim();
            if (coordinate.matches("[^\\s:]+:[^\\s:]+:[^\\s:]+(?::[^\\s:]+)?")) {
                dependencies.add(coordinate);
            }
        }
        return dependencies;
    }

    private static List<String> mavenDependencies(Path pom) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            var document = factory.newDocumentBuilder().parse(pom.toFile());
            Element project = document.getDocumentElement();
            Element dependencies = directChild(project, "dependencies");
            if (dependencies == null) return List.of();

            List<String> coordinates = new ArrayList<>();
            for (Node node = dependencies.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element dependency && localName(dependency).equals("dependency")) {
                    String group = childText(dependency, "groupId");
                    String artifact = childText(dependency, "artifactId");
                    String version = childText(dependency, "version");
                    if (!group.isBlank() && !artifact.isBlank()) {
                        coordinates.add(group + ":" + artifact + (version.isBlank() ? "" : ":" + version));
                    }
                }
            }
            return coordinates;
        } catch (Exception exception) {
            throw new IOException("Could not parse Maven build file: " + pom, exception);
        }
    }

    private static Element directChild(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName(element).equals(name)) return element;
        }
        return null;
    }

    private static String childText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }
}
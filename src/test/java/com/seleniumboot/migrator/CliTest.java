package com.seleniumboot.migrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliTest {

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(CliTest.class.getResource("/" + name).toURI());
    }

    private record RunResult(int exitCode, String out, String err) { }

    private static RunResult runCli(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int code = Cli.run(args, out, err);
            return new RunResult(code, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void analyzeDefaultsToTextAndExitsZero() throws Exception {
        Path path = fixture("maven");
        RunResult result = runCli("analyze", path.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Selenium Boot Migration Analysis"));
        assertTrue(result.out().contains("Files found:"));
        assertTrue(result.err().isEmpty());
    }

    @Test
    void analyzeWithFormatJsonOutputsValidJsonAndExitsZero() throws Exception {
        Path path = fixture("maven");
        RunResult result = runCli("analyze", path.toString(), "--format", "json");

        assertEquals(0, result.exitCode());
        assertTrue(result.out().trim().startsWith("{"));
        assertTrue(result.out().trim().endsWith("}"));
        assertTrue(result.out().contains("\"filesFound\":"));
        assertTrue(result.out().contains("\"summary\":"));
        assertTrue(result.out().contains("\"estimatedConfidence\":"));
        assertTrue(result.err().isEmpty());
    }

    @Test
    void analyzeAcceptsFormatWithEqualSignAndCaseInsensitive() throws Exception {
        Path path = fixture("maven");
        RunResult result = runCli("analyze", "--format=JSON", path.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("\"filesFound\":"));
        assertTrue(result.err().isEmpty());
    }

    @Test
    void analyzeFailUnderExitsZeroWhenConfidenceMeetsThreshold() throws Exception {
        Path path = fixture("maven");
        RunResult result = runCli("analyze", path.toString(), "--fail-under", "50");

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("Estimated migration confidence: 100%"));
    }

    @Test
    void analyzeFailUnderExitsOneWhenConfidenceBelowThresholdAndStillOutputsReport(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("Test.java"), """
                class Test {
                    void m() {
                        new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(1));
                    }
                }
                """);

        RunResult result = runCli("analyze", temp.toString(), "--fail-under", "80");

        assertEquals(1, result.exitCode());
        assertTrue(result.out().contains("Estimated migration confidence: 50%"));
        assertTrue(result.out().contains("Findings"));
    }

    @Test
    void analyzeFailUnderWithJsonExitsOneWhenConfidenceBelowThreshold(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("Test.java"), """
                class Test {
                    void m() {
                        new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(1));
                    }
                }
                """);

        RunResult result = runCli("analyze", temp.toString(), "--format", "json", "--fail-under", "90");

        assertEquals(1, result.exitCode());
        assertTrue(result.out().contains("\"estimatedConfidence\": 50"));
        assertTrue(result.out().contains("\"ruleId\": \"MIG-003\""));
    }

    @Test
    void analyzeSupportsAnyOrderOfArgumentsAndFlags() throws Exception {
        Path path = fixture("maven");
        RunResult result = runCli("analyze", "--fail-under=10", "--format", "json", path.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.out().contains("\"estimatedConfidence\": 100"));
    }

    @Test
    void analyzeThresholdBoundaryValues() throws Exception {
        Path path = fixture("maven");

        assertEquals(0, runCli("analyze", path.toString(), "--fail-under", "0").exitCode());
        assertEquals(0, runCli("analyze", path.toString(), "--fail-under", "100").exitCode());
    }

    @Test
    void returnsExitCodeTwoOnEmptyArguments() {
        RunResult result = runCli();
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("usage:"));
    }

    @Test
    void returnsExitCodeTwoOnUnknownCommand() {
        RunResult result = runCli("unknown-command");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("unknown command: unknown-command"));
        assertTrue(result.err().contains("usage:"));
    }

    @Test
    void returnsExitCodeTwoOnMissingDirectory() {
        RunResult result = runCli("analyze");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("missing project directory"));
        assertTrue(result.err().contains("usage:"));
    }

    @Test
    void returnsExitCodeTwoOnNonExistentDirectory() {
        RunResult result = runCli("analyze", "this-directory-does-not-exist-12345");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("not a directory:"));
    }

    @Test
    void returnsExitCodeTwoOnUnknownOption() throws Exception {
        RunResult result = runCli("analyze", fixture("maven").toString(), "--bogus");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("unknown option: --bogus"));
    }

    @Test
    void returnsExitCodeTwoOnUnsupportedFormat() throws Exception {
        RunResult result = runCli("analyze", fixture("maven").toString(), "--format", "yaml");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("unsupported format: yaml (expected 'text' or 'json')"));
    }

    @Test
    void returnsExitCodeTwoOnMissingFormatValue() throws Exception {
        RunResult result = runCli("analyze", fixture("maven").toString(), "--format");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("missing value for --format"));
    }

    @Test
    void returnsExitCodeTwoOnMissingFailUnderValue() throws Exception {
        RunResult result = runCli("analyze", fixture("maven").toString(), "--fail-under");
        assertEquals(2, result.exitCode());
        assertTrue(result.err().contains("missing value for --fail-under"));
    }

    @Test
    void returnsExitCodeTwoOnInvalidFailUnderThreshold() throws Exception {
        Path path = fixture("maven");

        RunResult notNumber = runCli("analyze", path.toString(), "--fail-under", "not-a-number");
        assertEquals(2, notNumber.exitCode());
        assertTrue(notNumber.err().contains("invalid threshold for --fail-under: not-a-number"));

        RunResult negative = runCli("analyze", path.toString(), "--fail-under", "-1");
        assertEquals(2, negative.exitCode());
        assertTrue(negative.err().contains("invalid threshold for --fail-under: -1"));

        RunResult overHundred = runCli("analyze", path.toString(), "--fail-under", "101");
        assertEquals(2, overHundred.exitCode());
        assertTrue(overHundred.err().contains("invalid threshold for --fail-under: 101"));
    }

    @Test
    void returnsExitCodeThreeOnRuntimeError(@TempDir Path temp) throws Exception {
        Path file = Files.writeString(temp.resolve("file.txt"), "hello");
        Path invalidOutput = file.resolve("child");

        RunResult result = runCli("migrate", fixture("maven").toString(), "--out", invalidOutput.toString());
        assertEquals(3, result.exitCode());
        assertTrue(result.err().contains("migration failed:"));
    }

    @Test
    void processExitCodes(@TempDir Path temp) throws Exception {
        Files.writeString(temp.resolve("Wait.java"), """
                class Wait {
                    void test() {
                        new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(1));
                    }
                }
                """);

        String java = ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        String classpath = System.getProperty("java.class.path");

        // Success process: exit code 0
        Process passProcess = new ProcessBuilder(java, "-cp", classpath,
                "com.seleniumboot.migrator.Cli", "analyze", temp.toString(), "--fail-under", "50")
                .start();
        assertEquals(0, passProcess.waitFor());

        // Quality gate failure process: exit code 1
        Process failProcess = new ProcessBuilder(java, "-cp", classpath,
                "com.seleniumboot.migrator.Cli", "analyze", temp.toString(), "--fail-under", "90")
                .start();
        assertEquals(1, failProcess.waitFor());

        // Usage error process: exit code 2
        Process errorProcess = new ProcessBuilder(java, "-cp", classpath,
                "com.seleniumboot.migrator.Cli", "analyze", temp.toString(), "--format", "invalid")
                .start();
        assertEquals(2, errorProcess.waitFor());

        // Runtime error process: exit code 3
        Path blockingFile = Files.writeString(temp.resolve("blocking-file.txt"), "hello");
        Path invalidOutput = blockingFile.resolve("child");
        Process runtimeErrorProcess = new ProcessBuilder(java, "-cp", classpath,
                "com.seleniumboot.migrator.Cli", "migrate", fixture("maven").toString(), "--out", invalidOutput.toString())
                .start();
        assertEquals(3, runtimeErrorProcess.waitFor());
    }
}

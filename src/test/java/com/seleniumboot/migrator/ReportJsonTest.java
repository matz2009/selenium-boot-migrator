package com.seleniumboot.migrator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportJsonTest {

    @Test
    void rendersEmptyReport() {
        Report report = new Report(0, 0, List.of(), List.of(), List.of(), List.of(), Map.of());
        String json = report.toJson();

        assertTrue(json.contains("\"filesFound\": 0"));
        assertTrue(json.contains("\"filesParsed\": 0"));
        assertTrue(json.contains("\"unparsableFiles\": []"));
        assertTrue(json.contains("\"detectedTechnologies\": []"));
        assertTrue(json.contains("\"recognizedTechnologies\": []"));
        assertTrue(json.contains("\"locatorCounts\": {}"));
        assertTrue(json.contains("\"mapsCleanly\": 0"));
        assertTrue(json.contains("\"manualReviewRequired\": 0"));
        assertTrue(json.contains("\"unparsableFiles\": 0"));
        assertTrue(json.contains("\"byRule\": {}"));
        assertTrue(json.contains("\"estimatedConfidence\": 100"));
        assertTrue(json.contains("\"findings\": []"));
    }

    @Test
    void rendersFullReportWithFindingsAndLocators() {
        Report report = new Report(
                10,
                8,
                List.of("src/broken/Bad.java", "src\\windows\\Path.java"),
                List.of(
                        new Finding("MIG-001", Finding.Status.AUTO, "src/Driver.java", 15,
                                "ThreadLocal<WebDriver>", "Delete the driver factory; extend BaseTest."),
                        new Finding("MIG-003", Finding.Status.MANUAL, "src\\Wait.java", 42,
                                "new WebDriverWait(driver, 10)", "Use $(locator) auto-wait or \"getWait()\".")
                ),
                List.of("Build system: Maven", "Dependency: org.testng:testng:7.10.0"),
                List.of("TestNG"),
                Map.of("By.id", 5L, "By.xpath", 3L)
        );

        String json = report.toJson();

        assertTrue(json.contains("\"filesFound\": 10"));
        assertTrue(json.contains("\"filesParsed\": 8"));
        assertTrue(json.contains("\"src/broken/Bad.java\""));
        assertTrue(json.contains("\"src/windows/Path.java\""), "Windows path backslashes should be normalized");
        assertTrue(json.contains("\"Build system: Maven\""));
        assertTrue(json.contains("\"Dependency: org.testng:testng:7.10.0\""));
        assertTrue(json.contains("\"TestNG\""));
        assertTrue(json.contains("\"By.id\": 5"));
        assertTrue(json.contains("\"By.xpath\": 3"));
        assertTrue(json.contains("\"mapsCleanly\": 1"));
        assertTrue(json.contains("\"manualReviewRequired\": 1"));
        assertTrue(json.contains("\"unparsableFiles\": 2"));
        assertTrue(json.contains("\"MIG-001\": 1"));
        assertTrue(json.contains("\"MIG-003\": 1"));
        assertTrue(json.contains("\"estimatedConfidence\":"));
        assertTrue(json.contains("\"ruleId\": \"MIG-001\""));
        assertTrue(json.contains("\"status\": \"AUTO\""));
        assertTrue(json.contains("\"file\": \"src/Driver.java\""));
        assertTrue(json.contains("\"line\": 15"));
        assertTrue(json.contains("\"detected\": \"ThreadLocal<WebDriver>\""));
        assertTrue(json.contains("\"file\": \"src/Wait.java\""), "Finding path should be normalized");
        assertTrue(json.contains("\"advice\": \"Use $(locator) auto-wait or \\\"getWait()\\\".\""), "Quotes in advice must be escaped");
    }

    @Test
    void escapesSpecialCharactersProperly() {
        Report report = new Report(
                1,
                1,
                List.of(),
                List.of(
                        new Finding("MIG-TEST", Finding.Status.MANUAL, "file.java", 1,
                                "line1\nline2\t\"quoted\"\\backslash", "advice\r\n\b\f")
                )
        );

        String json = report.toJson();

        assertTrue(json.contains("\\nline2\\t\\\"quoted\\\"\\\\backslash"));
        assertTrue(json.contains("advice\\r\\n\\b\\f"));
    }

    @Test
    void rendersLineZeroWhenPositionUnavailable() {
        Report report = new Report(
                1,
                1,
                List.of(),
                List.of(
                        new Finding("MIG-015", Finding.Status.MANUAL, "Driver.java", 0,
                                "CustomDriverManager", "Review and replace with BaseTest.")
                )
        );

        String json = report.toJson();
        assertTrue(json.contains("\"line\": 0"), "Line 0 should be rendered validly when position is unavailable");
    }

    @Test
    void ruleCountsProvidesSharedAggregation() {
        Report report = new Report(
                1,
                1,
                List.of(),
                List.of(
                        new Finding("MIG-001", Finding.Status.AUTO, "A.java", 1, "d", "a"),
                        new Finding("MIG-001", Finding.Status.AUTO, "B.java", 2, "d", "a"),
                        new Finding("MIG-003", Finding.Status.MANUAL, "C.java", 3, "d", "a")
                )
        );

        Map<String, Long> counts = report.ruleCounts();
        assertEquals(2L, counts.get("MIG-001"));
        assertEquals(1L, counts.get("MIG-003"));
        assertEquals(2, counts.size());
    }
}

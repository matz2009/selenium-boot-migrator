package com.seleniumboot.migrator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Report(int filesFound, int filesParsed, List<String> unparsable, List<Finding> findings, String technologies) {

    public long count(Finding.Status s) {
        return findings.stream().filter(f -> f.status() == s).count();
    }

    /**
     * Estimate confidence per affected file instead of per finding.
     * Auto-only files score 100, files requiring manual review score 50,
     * and unparsable files score 0. Repeated findings in one file therefore
     * do not dominate the estimate.
     */
    public int estimatedConfidence() {
        Map<String, Finding.Status> statusByFile = new TreeMap<>();
        findings.forEach(finding -> statusByFile.merge(
                finding.file(),
                finding.status(),
                (current, next) -> current == Finding.Status.MANUAL || next == Finding.Status.MANUAL
                        ? Finding.Status.MANUAL
                        : Finding.Status.AUTO));

        long affectedFiles = statusByFile.size();
        long total = affectedFiles + unparsable.size();
        if (total == 0) return 100;

        long manualFiles = statusByFile.values().stream()
                .filter(status -> status == Finding.Status.MANUAL)
                .count();
        long autoOnlyFiles = affectedFiles - manualFiles;

        double score = autoOnlyFiles * 100.0 + manualFiles * 50.0;
        return (int) Math.round(score / total);
    }

    public String render() {
        StringBuilder sb = new StringBuilder("Selenium Boot Migration Analysis\n\n");
        sb.append(String.format("Files found:              %d%n", filesFound));
        sb.append(String.format("Files parsed:             %d%n", filesParsed));
        sb.append(String.format("%nDetected technologies:   %s%n", technologies));
        Map<String, Long> byRule = new TreeMap<>();
        findings.forEach(f -> byRule.merge(f.ruleId(), 1L, Long::sum));
        byRule.forEach((r, n) -> sb.append(String.format("%-40s %d%n", ruleLabel(r) + ":", n)));
        sb.append(String.format("%nMaps cleanly:             %d%n", count(Finding.Status.AUTO)));
        sb.append(String.format("Manual review required:   %d%n", count(Finding.Status.MANUAL)));
        sb.append(String.format("Unparsable files:         %d%n", unparsable.size()));
        sb.append(String.format("%nEstimated migration confidence: %d%% (an estimate, not a guarantee)%n", estimatedConfidence()));
        if (!findings.isEmpty()) sb.append("\nFindings\n");
        for (Finding f : findings) {
            sb.append(String.format("  %s %s  %s:%d  %s%n      -> %s%n",
                    f.status() == Finding.Status.AUTO ? "[auto]  " : "[manual]",
                    f.ruleId(), f.file(), f.line(), f.detected(), f.advice()));
        }
        unparsable.forEach(u -> sb.append("  [unparsable] ").append(u).append('\n'));
        return sb.toString();
    }

    public Map<String, Long> ruleCounts() {
        Map<String, Long> byRule = new TreeMap<>();
        findings.forEach(f -> byRule.merge(f.ruleId(), 1L, Long::sum));
        return Collections.unmodifiableMap(byRule);
    }


    public String toJson() {
        return JsonRenderer.render(this);
    }


    private static String ruleLabel(String ruleId) {
        return switch (ruleId) {
            case "MIG-010" -> "MIG-010 (Page objects)";
            case "MIG-011" -> "MIG-011 (@FindBy fields)";
            case "MIG-012" -> "MIG-012 (PageFactory.initElements calls)";
            default -> ruleId;
        };
    }
}

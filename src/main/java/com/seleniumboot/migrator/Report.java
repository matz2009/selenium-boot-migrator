package com.seleniumboot.migrator;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record Report(int filesFound, int filesParsed, List<String> unparsable, List<Finding> findings, String technologies) {

    public long count(Finding.Status s) {
        return findings.stream().filter(f -> f.status() == s).count();
    }

    /** Share of detected patterns that map cleanly. Estimate only; never a guarantee. */
    public int estimatedConfidence() {
        long total = findings.size();
        long penalty = unparsable.size();
        if (total + penalty == 0) return 100;
        return (int) Math.round(100.0 * count(Finding.Status.AUTO) / (total + penalty));
    }

    public String render() {
        StringBuilder sb = new StringBuilder("Selenium Boot Migration Analysis\n\n");
        sb.append(String.format("Files found:              %d%n", filesFound));
        sb.append(String.format("Files parsed:             %d%n", filesParsed));
        sb.append(String.format("%nDetected technologies:   %s%n", technologies));
        Map<String, Long> byRule = new TreeMap<>();
        findings.forEach(f -> byRule.merge(f.ruleId(), 1L, Long::sum));
        byRule.forEach((r, n) -> sb.append(String.format("%-25s %d%n", r + ":", n)));
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
}

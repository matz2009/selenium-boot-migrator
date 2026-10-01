package com.seleniumboot.migrator;

import java.util.List;
import java.util.Map;


final class JsonRenderer {

    private JsonRenderer() { }

    static String render(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"filesFound\": ").append(report.filesFound()).append(",\n");
        sb.append("  \"filesParsed\": ").append(report.filesParsed()).append(",\n");

        sb.append("  \"unparsableFiles\": ");
        appendNormalizedStringList(sb, report.unparsable(), "  ");
        sb.append(",\n");

        sb.append("  \"detectedTechnologies\": ");
        appendStringList(sb, report.detectedTechnologies(), "  ");
        sb.append(",\n");

        sb.append("  \"recognizedTechnologies\": ");
        appendStringList(sb, report.recognizedTechnologies(), "  ");
        sb.append(",\n");

        sb.append("  \"locatorCounts\": ");
        appendNumberMap(sb, report.locatorCounts(), "  ");
        sb.append(",\n");

        sb.append("  \"summary\": {\n");
        sb.append("    \"mapsCleanly\": ").append(report.count(Finding.Status.AUTO)).append(",\n");
        sb.append("    \"manualReviewRequired\": ").append(report.count(Finding.Status.MANUAL)).append(",\n");
        sb.append("    \"unparsableFiles\": ").append(report.unparsable().size()).append(",\n");
        sb.append("    \"byRule\": ");
        appendNumberMap(sb, report.ruleCounts(), "    ");
        sb.append("\n  },\n");


        sb.append("  \"estimatedConfidence\": ").append(report.estimatedConfidence()).append(",\n");

        sb.append("  \"findings\": ");
        if (report.findings().isEmpty()) {
            sb.append("[]\n");
        } else {
            sb.append("[\n");
            for (int i = 0; i < report.findings().size(); i++) {
                Finding finding = report.findings().get(i);
                sb.append("    {\n");
                sb.append("      \"ruleId\": ").append(escapeString(finding.ruleId())).append(",\n");
                sb.append("      \"status\": ").append(escapeString(finding.status().name())).append(",\n");
                sb.append("      \"file\": ").append(escapeString(normalizePath(finding.file()))).append(",\n");
                sb.append("      \"line\": ").append(finding.line()).append(",\n");
                sb.append("      \"detected\": ").append(escapeString(finding.detected())).append(",\n");
                sb.append("      \"advice\": ").append(escapeString(finding.advice())).append("\n");
                sb.append("    }");
                if (i < report.findings().size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ]\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static void appendStringList(StringBuilder sb, List<String> list, String indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append(indent).append("  ").append(escapeString(list.get(i)));
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("]");
    }

    private static void appendNormalizedStringList(StringBuilder sb, List<String> list, String indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append(indent).append("  ").append(escapeString(normalizePath(list.get(i))));
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("]");
    }

    private static void appendNumberMap(StringBuilder sb, Map<String, Long> map, String indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int count = 0;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            sb.append(indent).append("  ").append(escapeString(entry.getKey()))
                    .append(": ").append(entry.getValue());
            if (++count < map.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append(indent).append("}");
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    static String escapeString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}

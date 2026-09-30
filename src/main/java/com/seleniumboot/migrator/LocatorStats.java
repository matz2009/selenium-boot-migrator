package com.seleniumboot.migrator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.Collections;
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;

final class LocatorStats {

    private static final Set<String> LOCATOR_METHODS = Set.of(
            "id",
            "name",
            "className",
            "cssSelector",
            "xpath",
            "linkText",
            "partialLinkText",
            "tagName");

    private final Map<String, Long> counts = new TreeMap<>();

    void scan(MethodCallExpr call) {
        if (!LOCATOR_METHODS.contains(call.getNameAsString())) {
            return;
        }

        if (call.getScope().filter(LocatorStats::isByScope).isEmpty()) {
            return;
        }

        counts.merge("By." + call.getNameAsString(), 1L, Long::sum);
    }

    Map<String, Long> counts() {
        return Collections.unmodifiableMap(counts);
    }

    private static boolean isByScope(Expression scope) {
        String value = scope.toString();
        return value.equals("By") || value.equals("org.openqa.selenium.By");
    }
}

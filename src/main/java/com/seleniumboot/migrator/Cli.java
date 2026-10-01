package com.seleniumboot.migrator;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Cli {

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args, System.out, System.err);
        } catch (Throwable throwable) {
            System.err.println("runtime error: " + (throwable.getMessage() != null ? throwable.getMessage() : throwable.toString()));
            exitCode = 3;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0) {
                printUsage(err);
                return 2;
            }

            String command = args[0];
            if (command.equals("analyze")) {
                return runAnalyze(args, out, err);
            } else if (command.equals("migrate")) {
                return runMigrate(args, out, err);
            } else {
                err.println("unknown command: " + command);
                printUsage(err);
                return 2;
            }
        } catch (Throwable throwable) {
            err.println("runtime error: " + (throwable.getMessage() != null ? throwable.getMessage() : throwable.toString()));
            return 3;
        }
    }

    private static int runAnalyze(String[] args, PrintStream out, PrintStream err) {
        Path dir = null;
        String format = "text";
        Integer failUnder = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--format")) {
                if (i + 1 >= args.length) {
                    err.println("missing value for --format");
                    printUsage(err);
                    return 2;
                }
                format = args[++i];
            } else if (arg.startsWith("--format=")) {
                format = arg.substring("--format=".length());
            } else if (arg.equals("--fail-under")) {
                if (i + 1 >= args.length) {
                    err.println("missing value for --fail-under");
                    printUsage(err);
                    return 2;
                }
                String val = args[++i];
                Integer parsed = parseThreshold(val, err);
                if (parsed == null) return 2;
                failUnder = parsed;
            } else if (arg.startsWith("--fail-under=")) {
                String val = arg.substring("--fail-under=".length());
                Integer parsed = parseThreshold(val, err);
                if (parsed == null) return 2;
                failUnder = parsed;
            } else if (arg.startsWith("-")) {
                err.println("unknown option: " + arg);
                printUsage(err);
                return 2;
            } else {
                if (dir == null) {
                    dir = Path.of(arg);
                } else {
                    err.println("unexpected argument: " + arg);
                    printUsage(err);
                    return 2;
                }
            }
        }

        if (!format.equalsIgnoreCase("text") && !format.equalsIgnoreCase("json")) {
            err.println("unsupported format: " + format + " (expected 'text' or 'json')");
            printUsage(err);
            return 2;
        }

        if (dir == null) {
            err.println("missing project directory");
            printUsage(err);
            return 2;
        }

        if (!Files.isDirectory(dir)) {
            err.println("not a directory: " + dir);
            return 2;
        }

        Report report;
        try {
            report = new Analyzer().analyze(dir);
        } catch (Exception exception) {
            err.println("analysis failed: " + (exception.getMessage() != null ? exception.getMessage() : exception.toString()));
            return 3;
        }

        if (format.equalsIgnoreCase("json")) {
            out.print(report.toJson());
        } else {
            out.print(report.render());
        }

        if (failUnder != null && report.estimatedConfidence() < failUnder) {
            return 1;
        }

        return 0;
    }

    private static Integer parseThreshold(String value, PrintStream err) {
        try {
            int threshold = Integer.parseInt(value);
            if (threshold < 0 || threshold > 100) {
                err.println("invalid threshold for --fail-under: " + value + " (must be between 0 and 100)");
                return null;
            }
            return threshold;
        } catch (NumberFormatException exception) {
            err.println("invalid threshold for --fail-under: " + value + " (must be an integer between 0 and 100)");
            return null;
        }
    }

    private static int runMigrate(String[] args, PrintStream out, PrintStream err) {
        Path projectDir = null;
        Path outputDir = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--out")) {
                if (i + 1 >= args.length) {
                    err.println("missing value for --out");
                    printUsage(err);
                    return 2;
                }
                outputDir = Path.of(args[++i]);
            } else if (arg.startsWith("--out=")) {
                outputDir = Path.of(arg.substring("--out=".length()));
            } else if (arg.startsWith("-")) {
                err.println("unknown option: " + arg);
                printUsage(err);
                return 2;
            } else {
                if (projectDir == null) {
                    projectDir = Path.of(arg);
                } else {
                    err.println("unexpected argument: " + arg);
                    printUsage(err);
                    return 2;
                }
            }
        }

        if (projectDir == null || outputDir == null) {
            printUsage(err);
            return 2;
        }

        try {
            var result = new Migrator().migrate(projectDir, outputDir);
            out.println("Migrated copy: " + result.output());
            out.println("Applied changes: " + result.applied().size());
            result.applied().forEach(change -> out.println("  [applied] " + change));
            result.notes().forEach(note -> out.println("  [note] " + note));
            out.println("\nItems still requiring review:");
            out.print(result.remaining().render());
            return 0;
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            return 2;
        } catch (Exception exception) {
            err.println("migration failed: " + (exception.getMessage() != null ? exception.getMessage() : exception.toString()));
            return 3;
        }
    }

    private static void printUsage(PrintStream err) {
        err.println("usage: selenium-boot-migrator analyze <project-dir> [--format <text|json>] [--fail-under <percent>]\n"
                + "       selenium-boot-migrator migrate <project-dir> --out <output-dir>");
    }
}

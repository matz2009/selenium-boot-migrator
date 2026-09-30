package com.seleniumboot.migrator;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Cli {

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("analyze")) {
            Path dir = Path.of(args[1]);
            if (!Files.isDirectory(dir)) {
                System.err.println("not a directory: " + dir);
                System.exit(2);
            }
            System.out.print(new Analyzer().analyze(dir).render());
            return;
        }
        if (args.length != 4 || !args[0].equals("migrate") || !args[2].equals("--out")) {
            System.err.println("usage: selenium-boot-migrator analyze <project-dir>\n"
                    + "       selenium-boot-migrator migrate <project-dir> --out <output-dir>");
            System.exit(2);
        }
        try {
            var result = new Migrator().migrate(Path.of(args[1]), Path.of(args[3]));
            System.out.println("Migrated copy: " + result.output());
            System.out.println("Applied changes: " + result.applied().size());
            result.applied().forEach(change -> System.out.println("  [applied] " + change));
            result.notes().forEach(note -> System.out.println("  [note] " + note));
            System.out.println("\nItems still requiring review:");
            System.out.print(result.remaining().render());
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            System.exit(2);
        }
    }
}

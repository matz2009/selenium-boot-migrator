# Selenium Boot Migrator

Already have Selenium tests? Don't rewrite them. This tool scans an existing Selenium Java
project and reports which parts map onto [Selenium Boot](https://github.com/seleniumboot/selenium-boot)
and which need a human.

**Status: early.** `analyze` is read-only. `migrate` creates a separate copy and applies only
mechanical rules; it never edits the source directory. It runs locally; your source never leaves
your machine.

## Use

```bash
mvn package
java -jar target/selenium-boot-migrator.jar analyze ./my-selenium-project
java -jar target/selenium-boot-migrator.jar migrate ./my-selenium-project --out ./my-selenium-project-migrated
```


The output directory must not already exist and cannot be the source directory or one of its
children. Migration output lists applied changes, compatibility notes, and any findings still
requiring manual review. The POM rewrite uses the published Selenium Boot `3.5.0` release.

`analyze` reports counts per rule, what maps cleanly vs. needs review, and an *estimated* confidence.
The estimate is a guide, not a guarantee. The report also lists dependencies found in Maven
`pom.xml` files and Gradle `build.gradle` / `build.gradle.kts` files. Gradle files are inspected
as text; a Gradle installation is not required.


## Rules

Each rule follows the [Selenium + TestNG migration guide](https://docs.seleniumboot.com).

| ID | Detects | Suggested change |
|---|---|---|
| MIG-001 | `ThreadLocal<WebDriver>` | Delete a matching top-level `*DriverFactory`; extend `BaseTest` |
| MIG-002 | `WebDriverManager` | Delete standalone `.setup()` calls; Selenium Manager handles drivers |
| MIG-003 | `WebDriverWait`, `ExpectedConditions` | Auto-waiting locators / `getWait()` (manual review) |
| MIG-004 | `IRetryAnalyzer`, `IAnnotationTransformer` | Delete matching top-level classes; use `retry:` config / `@Retryable` |
| MIG-005 | Screenshot `ITestListener` | Delete matching top-level listener; captured automatically |
| MIG-010 | Class with a `WebDriver` constructor parameter | Page-object candidate; review against `BasePage` |
| MIG-011 | `@FindBy` fields | Manual review; Selenium Boot documents `By` locator fields |
| MIG-012 | `PageFactory.initElements(...)` | Manual review; page initialization mapping is not documented |
| MIG-014 | `Thread.sleep` | Manual review |
| MIG-015 | Custom `*DriverManager` / `*DriverFactory` | Manual review |
| MIG-016 | `implicitlyWait` | Remove; manual review |
| MIG-017 | References to classes removed by migration | Update the caller before compiling |

The Selenium Boot [getting-started guide](https://docs.seleniumboot.com/docs/getting-started) documents page objects extending `BasePage`, with a `WebDriver` constructor and `By` locator fields. It does not document `@FindBy` or `PageFactory.initElements`; the analyzer therefore reports their counts for review rather than treating them as a direct `BasePage` mapping.

## Adding a rule

1. Add detection in `Analyzer.scan(...)` and emit a `Finding` with a new `MIG-` ID.
2. Add a test in `AnalyzerTest` with a small inline source string.
3. Add a row to the table above.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Good first tasks are labelled
[`good first issue`](https://github.com/seleniumboot/selenium-boot-migrator/labels/good%20first%20issue).

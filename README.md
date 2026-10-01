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

# Text report (default)
java -jar target/selenium-boot-migrator.jar analyze ./my-selenium-project

# JSON output for tooling / CI
java -jar target/selenium-boot-migrator.jar analyze ./my-selenium-project --format json

# Enforce a minimum confidence threshold in CI
java -jar target/selenium-boot-migrator.jar analyze ./my-selenium-project --fail-under 80

# Combine format and threshold flags
java -jar target/selenium-boot-migrator.jar analyze ./my-selenium-project --format json --fail-under 80

# Apply mechanical migrations
java -jar target/selenium-boot-migrator.jar migrate ./my-selenium-project --out ./my-selenium-project-migrated
```

The output directory must not already exist and cannot be the source directory or one of its
children. Migration output lists applied changes, compatibility notes, and any findings still
requiring manual review. The POM rewrite uses the published Selenium Boot `3.5.0` release.

`analyze` reports counts per rule, what maps cleanly vs. needs review, and an *estimated* confidence.
The estimate is a guide, not a guarantee. The report also lists dependencies found in Maven
`pom.xml` files and Gradle `build.gradle` / `build.gradle.kts` files. Gradle files are inspected
as text; a Gradle installation is not required.

### CLI Options

#### `analyze <project-dir>`

- `--format <text|json>`: Output format. Defaults to `text`.
- `--fail-under <percent>`: Threshold percentage between `0` and `100`. If estimated confidence is strictly less than this threshold, the command exits with code `1`. The report is always printed before exiting.

#### `migrate <project-dir> --out <output-dir>`

- `--out <output-dir>`: Target directory for migrated copy. Must not already exist.

### Exit Codes

| Exit Code | Description |
|---|---|
| `0` | Success: command completed normally and estimated confidence meets `--fail-under` (if specified). |
| `1` | Quality gate failed: estimated confidence is strictly below the `--fail-under` percentage. |
| `2` | Usage or input error: invalid arguments, unsupported format, invalid threshold, missing directory, or directory not found. |
| `3` | Runtime error: analysis or migration failed due to an I/O error or unexpected runtime failure. |


## Machine-Readable Output (JSON)

Use `--format json` to integrate analysis with CI pipelines, code review bots, and migration dashboards:

```bash
java -jar target/selenium-boot-migrator.jar analyze ./my-selenium-project --format json
```

### JSON Schema

The output adheres to the following stable schema:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "SeleniumBootMigrationReport",
  "type": "object",
  "required": [
    "filesFound",
    "filesParsed",
    "unparsableFiles",
    "detectedTechnologies",
    "recognizedTechnologies",
    "locatorCounts",
    "summary",
    "estimatedConfidence",
    "findings"
  ],
  "properties": {
    "filesFound": {
      "type": "integer",
      "minimum": 0,
      "description": "Total number of .java files discovered."
    },
    "filesParsed": {
      "type": "integer",
      "minimum": 0,
      "description": "Number of .java files successfully parsed."
    },
    "unparsableFiles": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Normalized paths of files that could not be parsed."
    },
    "detectedTechnologies": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Build systems and dependency coordinates detected."
    },
    "recognizedTechnologies": {
      "type": "array",
      "items": { "type": "string" },
      "description": "Recognized testing frameworks and libraries (e.g. Selenium 4.21.0, TestNG)."
    },
    "locatorCounts": {
      "type": "object",
      "additionalProperties": { "type": "integer" },
      "description": "Counts per Selenium locator method (e.g. By.id, By.xpath)."
    },
    "summary": {
      "type": "object",
      "required": ["mapsCleanly", "manualReviewRequired", "unparsableFiles", "byRule"],
      "properties": {
        "mapsCleanly": {
          "type": "integer",
          "minimum": 0,
          "description": "Total count of AUTO findings that map cleanly to Selenium Boot."
        },
        "manualReviewRequired": {
          "type": "integer",
          "minimum": 0,
          "description": "Total count of MANUAL findings requiring human attention."
        },
        "unparsableFiles": {
          "type": "integer",
          "minimum": 0,
          "description": "Count of unparsable source files."
        },
        "byRule": {
          "type": "object",
          "additionalProperties": { "type": "integer" },
          "description": "Counts of findings grouped by rule ID."
        }
      }
    },
    "estimatedConfidence": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100,
      "description": "Confidence score percentage (0-100)."
    },
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["ruleId", "status", "file", "line", "detected", "advice"],
        "properties": {
          "ruleId": { "type": "string", "description": "Migration rule ID (e.g. MIG-001)." },
          "status": { "type": "string", "enum": ["AUTO", "MANUAL"], "description": "Mapping status." },
          "file": { "type": "string", "description": "Normalized relative file path." },
          "line": { "type": "integer", "minimum": 0, "description": "1-based line number of finding, or 0 if position is unavailable." },
          "detected": { "type": "string", "description": "Code snippet or construct detected." },
          "advice": { "type": "string", "description": "Suggested migration action." }
        }
      }
    }
  }
}
```

### Example JSON Output

```json
{
  "filesFound": 4,
  "filesParsed": 4,
  "unparsableFiles": [],
  "detectedTechnologies": [
    "Build system: Maven",
    "Dependency: org.seleniumhq.selenium:selenium-java:4.21.0",
    "Dependency: org.testng:testng:7.10.2"
  ],
  "recognizedTechnologies": [
    "Selenium 4.21.0",
    "TestNG"
  ],
  "locatorCounts": {
    "By.id": 3,
    "By.xpath": 1
  },
  "summary": {
    "mapsCleanly": 1,
    "manualReviewRequired": 1,
    "unparsableFiles": 0,
    "byRule": {
      "MIG-001": 1,
      "MIG-003": 1
    }
  },
  "estimatedConfidence": 75,
  "findings": [
    {
      "ruleId": "MIG-001",
      "status": "AUTO",
      "file": "src/test/java/DriverFactory.java",
      "line": 12,
      "detected": "ThreadLocal<WebDriver>",
      "advice": "Delete the driver factory; extend BaseTest (per-thread isolation is built in)."
    },
    {
      "ruleId": "MIG-003",
      "status": "MANUAL",
      "file": "src/test/java/LoginPage.java",
      "line": 28,
      "detected": "WebDriverWait / ExpectedConditions",
      "advice": "Use $(locator) auto-wait or getWait(); review the condition by hand."
    }
  ]
}
```

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

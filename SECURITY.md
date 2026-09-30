# Security Policy

## Reporting a vulnerability

If you believe you've found a security vulnerability in Selenium Boot Migrator, please
report it privately — **do not open a public GitHub issue.**

Email **security@seleniumboot.com** with:

- a description of the issue and its impact,
- the command and input project that triggers it (a minimal repro is ideal),
- steps to reproduce, and a proof of concept if you have one.

You can expect an initial acknowledgement within *# Security Policy

## Supported Versions

Use this section to tell people about which versions of your project are
currently being supported with security updates.

| Version | Supported          |
| ------- | ------------------ |
| 5.1.x   | :white_check_mark: |
| 5.0.x   | :x:                |
| 4.0.x   | :white_check_mark: |
| < 4.0   | :x:                |

## Reporting a Vulnerability

Use this section to tell people how to report a vulnerability.

Tell them where to go, how often they can expect to get an update on a
reported vulnerability, what to expect if the vulnerability is accepted or
declined, etc.
*72 hours**. We'll keep you updated as
we investigate, and we'll credit you in the release notes once a fix ships (unless you'd
prefer to remain anonymous).

Please give us a reasonable window to release a fix before any public disclosure.

## Supported versions

Selenium Boot Migrator is pre-release (no tagged version yet) and built from source.
Security fixes go to the `main` branch; please build and test against the latest commit
before reporting.

## Scope

Selenium Boot Migrator is a local CLI: it reads an existing Selenium Java project,
reports what maps onto [Selenium Boot](https://github.com/seleniumboot/selenium-boot),
and — for `migrate` — writes a transformed copy to a separate output directory. It never
sends your source anywhere or executes code from the project it analyzes.

The most relevant classes of issue are, for example:
- path handling in `analyze`/`migrate` that could read or write outside the intended
  source/output directories (path traversal, symlink escapes),
- unsafe parsing of project files (`pom.xml`, `build.gradle`, `build.gradle.kts`) that
  could be exploited by a maliciously crafted input project,
- dependency vulnerabilities in the tool's own build.

Reports about the tool's own code and its parsing/generation logic are in scope. Issues
in Selenium Boot itself, Maven, Gradle, or JavaParser should go to their respective
projects.

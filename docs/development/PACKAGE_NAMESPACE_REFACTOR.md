# Package / Maven Namespace Refactor (public copy)

Status: COMPLETE (uncommitted)
Scope: mechanical Java-package + Maven-coordinate rename, plus removal of the
last personal absolute paths from tracked benchmark provenance. No algorithm,
metric, benchmark result, ONNX model or scientific conclusion was changed.

## 1. Motivation

The project had accidentally inherited the `org.vaimee.vsa` / `vaimee`
namespace when it was first created. Anomalib4j is not a VAIMEE project, so the
Java package root and the Maven coordinates were renamed.

## 2. Old -> new identifiers

| Kind | Old | New |
| --- | --- | --- |
| Java package root | `org.vaimee.vsa.anomalib` | `io.github.antctrlwin.anomalib4j` |
| Source path root | `src/{main,test,jmh}/java/org/vaimee/vsa/anomalib/` | `src/{main,test,jmh}/java/io/github/antctrlwin/anomalib4j/` |
| Maven groupId | `org.vaimee.vsa` | `io.github.antctrlwin` |
| Maven artifactId | `vsa-anomalib-core` | `anomalib4j` |
| Maven version | `0.1.0-SNAPSHOT` | `0.1.0-SNAPSHOT` (unchanged) |

## 3. Affected source trees

Source trees were moved with `git mv` (history preserving), keeping the
sub-package layout. Old `org/vaimee` directories were removed.

| Module | New path | Java files |
| --- | --- | ---: |
| main | `src/main/java/io/github/antctrlwin/anomalib4j` | 23 |
| test | `src/test/java/io/github/antctrlwin/anomalib4j` | 54 |
| jmh | `src/jmh/java/io/github/antctrlwin/anomalib4j` | 8 |
| **Total** | | **85** |

Every `package` and `import` statement was rewritten to the new root. Actual
class names were not changed (only their package).

Non-Java text updated in the same pass:

- `pom.xml` — `groupId` / `artifactId` (Maven coordinates).
- `tools/comparison/common.py`, `tools/prerun/local_memory_ablation.py`,
  `tools/prerun/positional_global_variance_diagnostic.py`,
  `tools/prerun/workspace_identity.py` — fully-qualified class names and
  tracked source paths used by launch/identity checks.
- 21 tracked documentation files with package, path or launch-command
  references (`docs/PROJECT_STATE.md`, `docs/TECHNICAL_OVERVIEW.md`,
  `docs/benchmark/BENCHMARK_PHASE{1,2}*.md`,
  `docs/development/**` and `docs/diagnostics/**` reports).

## 4. Personal-path sanitization

Two tracked provenance files still carried the original machine path
`C:\Users\<user>\IdeaProjects\Anomalib4j\...` (present at HEAD, unrelated to
the namespace rename). Per the public-copy sanitization policy the personal
prefix was stripped, leaving repo-relative paths:

- `docs/benchmark/benchmark-prerun/data-sha256.json` — 8 `"Path"` values
  (`SHA-256` digests unchanged).
- `docs/benchmark/benchmark-prerun/environment.json` — `"executable"`.

This is safe: `tools/comparison/common.py` only uses the substring after
`/data/` of each `Path`, and `tools/comparison/native.py` reads only the
`versions` object. Both files still parse as valid JSON.

## 5. Validation performed

| Check | Command / method | Result |
| --- | --- | --- |
| Old namespace gone (tracked) | `git grep -i vaimee` | only intentional references in this document; otherwise no matches |
| Main + test + JMH compile | `mvn -B -Pbenchmark -DskipTests test-compile` | BUILD SUCCESS — 23 main + 62 test/JMH sources, artifact `anomalib4j` |
| Quick test suite | `mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test` | Tests run: 98, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS; all classes under `io.github.antctrlwin.anomalib4j.*` |
| Whitespace errors | `git diff --check` / `git diff --cached --check` | exit 0 (only Git LF/CRLF autocrlf notices) |
| Logic unchanged | changed `+`/`-` lines in `src` and `docs`/`tools` not containing a namespace token | 0 |
| Benchmark results intact | `*.onnx`, `*.csv`, `*.npy`, `*.png` diff vs HEAD | no content changes; both ONNX blobs identical to HEAD |
| Personal paths | whole-repo search for drive/home paths and the user token | none remaining in tracked files |
| Old source dirs removed | filesystem check | `src/{main,test,jmh}/java/org` absent |

The long real-data tests `BottleTrainingTest` and `BottleRealEvaluationTest`
are excluded per the documented quick-suite convention; the refactor touches
only package/import/path lines, and the whole project (main + test + JMH)
compiles and the quick suite passes under the new namespace.

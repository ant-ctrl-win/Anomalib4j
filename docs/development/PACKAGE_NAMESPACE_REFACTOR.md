# Package / Maven Namespace Refactor

Date: 2026-10-04
Status: COMPLETE (uncommitted)
Scope: mechanical namespace and Maven-coordinate rename only.

## 1. Motivation

The repository had accidentally inherited the `org.vaimee.vsa` / `vaimee`
namespace when it was first created. Anomalib4j is not a VAIMEE project, so the
Java package root and the Maven coordinates were renamed. No algorithm, metric,
benchmark result, ONNX model or scientific conclusion was changed.

## 2. Old -> New identifiers

| Kind | Old | New |
| --- | --- | --- |
| Java package root | `org.vaimee.vsa.anomalib` | `io.github.antctrlwin.anomalib4j` |
| Source path root | `src/{main,test,jmh}/java/org/vaimee/vsa/anomalib/` | `src/{main,test,jmh}/java/io/github/antctrlwin/anomalib4j/` |
| Maven groupId | `org.vaimee.vsa` | `io.github.antctrlwin` |
| Maven artifactId | `vsa-anomalib-core` | `anomalib4j` |
| Maven version | `0.1.0-SNAPSHOT` | `0.1.0-SNAPSHOT` (unchanged) |

## 3. Files and classes affected

Source trees were moved with `git mv` (history preserving), preserving the
sub-package layout. Old `org/vaimee` directories were removed.

| Module | Old path | New path | Java files |
| --- | --- | --- | ---: |
| main | `src/main/java/org/vaimee/vsa/anomalib` | `src/main/java/io/github/antctrlwin/anomalib4j` | 23 |
| test | `src/test/java/org/vaimee/vsa/anomalib` | `src/test/java/io/github/antctrlwin/anomalib4j` | 54 |
| jmh | `src/jmh/java/org/vaimee/vsa/anomalib` | `src/jmh/java/io/github/antctrlwin/anomalib4j` | 8 |
| **Total** | | | **85** |

Sub-packages (new root), main: `adjoint` (2), `calibration` (1), `inference` (4),
`memory` (4), `model` (3), `onnx` (2), `projection` (3), `training` (1),
`validation` (3). Test: `adjoint` (4), `calibration` (1), `evaluation` (37),
`inference` (2), `memory` (2), `model` (1), `onnx` (2), `projection` (2),
`training` (2), `validation` (1). JMH: `evaluation` (2), `onnx` (6).

Every `package` and `import` statement was rewritten to the new root. Actual
class names were not changed (only their package).

Non-Java text updated in this pass:

- `pom.xml` — `groupId` / `artifactId` (Maven coordinates).
- `tools/comparison/common.py`, `tools/prerun/workspace_identity.py`,
  `tools/prerun/local_memory_ablation.py`,
  `tools/prerun/positional_global_variance_diagnostic.py` — fully-qualified
  class names and source paths used in Java launch / identity checks.
- 24 tracked documentation files with package, path or launch-command
  references (PROJECT_STATE, TECHNICAL_OVERVIEW, REPOSITORY_CLEANUP_RESULT,
  benchmark reports, development/archive reports, and the two post-hoc
  diagnostics).
- Untracked local files also cleaned: `CHAT_HANDOFF.md`,
  `.idea/workspace.xml` (run-configuration FQCNs), and the gitignored
  `session-ses_f13e.md` transcript. Note: the `<change>` local-VCS-history
  entries inside `.idea/workspace.xml` still record the old paths (see §5).

## 4. Occurrences of "vaimee" that are NOT the accidental namespace

These were **reported and left unchanged**, as they are not the project's
package/Maven namespace:

- `.idea/jarRepositories.xml` (untracked IDE config, lines 12 and 17):
  `https://maven.pkg.github.com/vaimee/SEPA` and
  `https://maven.pkg.github.com/vaimee/sjenar` — external Maven package
  repositories belonging to the VAIMEE organisation. These are third-party
  infrastructure URLs, not the local package namespace.

## 5. Residual (gitignored, non-source) occurrence

- `.idea/workspace.xml` (untracked IDE local state, ignored via `.idea/`):
  its `<change beforePath=".../org/vaimee/vsa/anomalib/..." afterPath=".../io/github/antctrlwin/anomalib4j/...">`
  entries (85 lines) are the IDE's own local-VCS *history record of this very
  rename* — the `beforePath` is intentionally the pre-refactor path. Run
  configurations and other FQCNs in the same file were updated. The `<change>`
  history is deliberately left untouched: it is local, untracked, ignored IDE
  state whose purpose is to remember the move, not build/source material.
- `session-ses_f13e.md` (gitignored private transcript, ignored via
  `session-*.md`): retains `vaimee` only inside an embedded **historical**
  ASCII directory tree of the old layout (quoted from an old
  `PROJECT_STRUCTURE.md`) and a few `vaimee/` tree fragments. It is deliberately
  left untouched: it is a private, ignored historical transcript, not
  build/source material, and rewriting it would falsify history. The tracked
  repository contains no reference to the old namespace.

## 6. Validation performed

| Check | Tool / command | Result |
| --- | --- | --- |
| Tracked old namespace gone | `git grep -il vaimee` | no matches (exit 1) |
| Main + test + JMH compile | `mvn -B -Pbenchmark -DskipTests test-compile` | BUILD SUCCESS (23 main, 62 test-jmh sources) |
| Full quick test suite | `mvn -B -Pbenchmark "-Dtest=*Test,!BottleTrainingTest,!BottleRealEvaluationTest" test` | Tests run: 98, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS; every test class under `io.github.antctrlwin.anomalib4j.*` |
| Whitespace errors | `git diff --check` and `git diff --cached --check` | exit 0 (only Git LF/CRLF autocrlf notices) |
| Logic unchanged | diff scan of changed lines | `src/main` 128 changed +/- lines, 0 non-package/import; whole `src` 478 changed +/- lines, 0 non-package/import |
| Frozen benchmark intact | `git status --porcelain -- docs/benchmark/BENCHMARK_FINAL_RESULT.md` | empty (untouched) |
| Metrics unchanged | benchmark result documents/CSV not modified | unchanged |
| ONNX models | not touched | unchanged; `mobilenetv4_spatial_14x14.onnx` SHA-256 `48da88a8da5f1f1f1ce2308d19128c0f302498aa1889882263620cad21bdd26e` |
| Old source dirs removed | filesystem check | `src/{main,test,jmh}/java/org` absent |

The long real-data tests `BottleTrainingTest` and `BottleRealEvaluationTest`
were excluded per the documented quick-suite convention; the refactor touches
only package/import lines, and the whole project (main + test + JMH) compiles
and the full quick suite passes under the new namespace.

## 7. Reproducibility note

To reach a clean repository-wide search for `vaimee`, gitignored `__pycache__`
directories that carried the old fully-qualified strings were deleted (they are
regenerable caches). No generated `target/` outputs were added to version
control. No commit was performed.

# Salary Editor Unification Implementation Plan

> **For agentic workers:** Execute each task with test-first development and verify its failing/passing test evidence. Work directly in the user-authorized current `main` workspace.

**Goal:** Align salary entry and editing with the ledger editor’s visual language, income-type controls, and in-app keypad, then release 0.5.10 to GitHub and Gitee.

**Architecture:** Extract a reusable Compose numeric keypad while keeping ledger and salary validation separate. Salary draft state tracks cash nature, selected income type, and active amount field; the existing Room and workbook contracts remain unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Gradle Android, Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-11-11-salary-editor-unification-design.md`

## Global Constraints

- Keep `main` in the current workspace; do not create another checkout or branch.
- Obtain no signing credentials into Git; keep package ID and signing certificate unchanged.
- Do not change Room, backup, or XLSX data formats.
- Publish tag `v0.5.10` and release title `财记 0.5.10` on both hosts.

## Review Focus

- Incomplete expressions and signed tax values must not become zero silently; cover evaluator boundary tests.
- Switching cash nature must clear only the income type; cover retained draft fields in Compose tests.
- Historical inactive types must remain editable without being offered for new selection; cover edit tests.
- The shared keypad must preserve ledger save, date, and amount behavior; run existing ledger instrumentation tests.
- Expanded deductions and long income labels must remain reachable above the dock; test layout with narrow width and larger font.

---

### Task 1: Salary amount expression and draft state

**Files:** `app/src/main/java/app/caiji/android/ui/SalaryScreens.kt`, `app/src/test/java/app/caiji/android/ui/SalaryInputTest.kt`

- [ ] Add tests for zero, positive and negative tax amounts, one addition/subtraction, incomplete input, precision and overflow.
- [ ] Verify the tests fail against the currently missing salary keypad evaluator.
- [ ] Implement a salary-only evaluator that returns integer fen and distinguishes blank-as-zero from malformed nonblank input.
- [ ] Verify the focused unit suite passes.

### Task 2: Shared numeric keypad

**Files:** `app/src/main/java/app/caiji/android/ui/EntryEditorScreen.kt`, new `app/src/main/java/app/caiji/android/ui/AmountKeypad.kt`, `app/src/androidTest/java/app/caiji/android/ui/EntryEditorScreenTest.kt`

- [ ] Characterize the shared keypad’s key sequence, save disabling, date picker and existing tags.
- [ ] Extract the 4×4 keypad into a reusable composable with date, active field label, expression, save-enabled state and key callback inputs.
- [ ] Keep ledger amount parsing and save behavior unchanged; retain `entry-key-*` tags.
- [ ] Run ledger editor instrumentation tests.

### Task 3: Salary editor layout and interactions

**Files:** `app/src/main/java/app/caiji/android/ui/SalaryScreens.kt`, `app/src/androidTest/java/app/caiji/android/ui/SalaryScreensTest.kt`

- [ ] Add failing UI cases for default cash/unselected, nature switching/draft retention, custom keypad, eight fields, deduction expansion, disabled historical type, and create/edit save.
- [ ] Replace salary text inputs with the approved segmented type selector, 4-column filtered type grid, active field state, shared keypad, and fixed calculation strip.
- [ ] Retain legacy inactive edit relationship; filter switch candidates to enabled types and clear selection on nature switch.
- [ ] Run focused salary instrumentation tests on API 26/34 plus narrow-width/large-font layout cases.

### Task 4: Work Item, Feature Docs and version

**Files:** `docs/work-items/WORK-20261111-001.md`, `docs/features/salary-records.md`, `docs/features/ledger-entries.md`, `docs/features/index.md`, `AGENTS.md`, `app/build.gradle.kts`

- [ ] Record approved scope, decisions, test evidence, risks and release/user-regression handoff.
- [ ] Update both existing feature records and index with verified current behavior; validate structural and fresh code basis.
- [ ] Add the Gitee mirror/release rule to project instructions and configure the named Gitee remote.
- [ ] Set app version to 0.5.10/code25.

### Task 5: Full verification and release

**Files:** release APK, SHA-256 sidecar, Work Item evidence.

- [ ] Run JVM tests, lint, full API 26/API 34 instrumentation suites and review the resulting diff.
- [ ] Build and verify signed APK package/version/certificate/zipalign/hash and same-signature upgrade data retention.
- [ ] Generate commit and Release notes with `dms-commit-msg`; commit and push `main` plus `v0.5.10` tag to both remotes without force.
- [ ] Create matching Releases on both hosts, attach APK and sidecar, and verify both uploaded artifacts.
- [ ] Mark user regression pending until the user installs and confirms the listed flows.

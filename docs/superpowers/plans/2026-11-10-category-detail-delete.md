# 分类明细删除与无分隔线实施计划

> **For agentic workers:** Execute inline in the current workspace and existing `main` branch. Use TDD; do not alter UI until the approved mockup is followed.

**Goal:** Remove per-entry separators from category details and add the same swipe-to-confirm delete interaction used by salary rows.

**Architecture:** Extract the existing ledger swipe-delete surface/gesture into an internal reusable row container used by `EntryRow` and category details. Keep category row content in `FinancialDetailRow`. Route confirmed deletion through the existing LedgerViewModel repository transaction, then explicitly reload the current report period and category.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Android instrumentation.

**Spec:** User-approved category-detail delete mockup, prior screen consistency work, and WORK-20261110-002.

## Global Constraints

- No database, backup, public API, or amount-calculation changes.
- Use the existing 100dp delete action, 48dp height, danger-soft fill, 12dp corners, delete glyph and 40% swipe threshold.
- Only one category row may be revealed at a time; tapping a revealed row closes it; clicking a closed row edits.
- Confirmation cancel retains the entry; failures retain it and show an error; deletion success reloads the active year/month/type/category while staying in category details.

## Review Focus

- Deleting the last item keeps the category name and reaches the existing empty state.
- Delete failure leaves the row visible and permits retry.
- Period and category remain selected after delete; updated summary/rankings appear on return.
- Narrow, large-font and long-amount rows remain readable while revealing the delete action.

---

### Task 1: Category detail delete interaction

**Files:** `LedgerScreens.kt`, `BillReportsScreen.kt`, `MainActivity.kt`; tests `BillReportsScreenTest.kt`, `EverydayLedgerFlowTest.kt`.

- [x] Add failing screen tests for left/right swipe, one-row reveal, close-on-tap, cancel, successful and failed delete callbacks.
- [x] Run targeted tests and confirm expected failures.
- [x] Extract the ledger swipe surface/gesture and use it for category detail rows; remove only category detail separators.
- [x] Connect delete confirmation to LedgerViewModel and reload the same report filter after success.
- [x] Run category flow tests and full suites on API26/API34 (178 tests each).

### Task 2: Full verification, docs and release

- [x] Run JVM, lint and API26/API34 instrumented suites; check narrow, large-font and long-amount states.
- [x] Update `ledger-entries` Feature Doc and index; validate structural and fresh.
- [x] Build and verify 0.5.9/versionCode 24 APK, signature, alignment, checksum and same-signature upgrade.
- [x] Generate commit/Release text with `dms-commit-msg`, commit/push `main` and publish `v0.5.9` with APK and checksum sidecar.

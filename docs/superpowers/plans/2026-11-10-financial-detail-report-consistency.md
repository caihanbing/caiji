# 财记工资与账单明细及报告样式统一实施计划

> **For agentic workers:** Execute inline in the current workspace; project instructions prohibit a new branch/workspace. Follow TDD and verify before reporting completion.

**Goal:** Align related salary and ledger screens with the user-approved eight-screen mockup.

**Architecture:** Retain existing report cards, tables, ranking and ledger row components. Add optional ranking amount sign/color behavior and render salary income-type details as the existing salary entry rows in one continuous, date-descending list.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android instrumented tests, Room data untouched.

**Spec:** User-approved mockup and requirements in WORK-20261110-001.

## Global Constraints

- Preserve appId `app.caiji.android`, minSdk 26, targetSdk 34, database schemas and backup formats.
- Preserve report filtering, click-to-edit, and salary edit/delete return paths.
- Detail rows use the category icon, existing `categoryEmoji` fallback, 40dp icon well, 12dp corners, 11dp gap, 18dp horizontal/6dp vertical padding, 14sp title, 12sp subtitle, 16sp semibold amount.
- Salary amount dates use `M月d日 · 现金/非现金`; detail is not grouped by month.
- Year report ranking amounts include `+` for salary income and `−` for ledger expense, 16sp semibold, salary green and ledger ink.

## Review Focus

- Equal and very large report totals: preserve signed prefix and tabular amount rendering.
- A salary detail spanning multiple months: render one descending list without month summary headers.
- Disabled or missing category IDs: use the existing “其他” icon fallback.
- Small widths and large font scale: retain right-aligned readable values and scrollable lists.
- Income and expense report pages: use their respective sign and palette semantics.

---

### Task 1: Ledger label and report ranking amounts

**Files:** `LedgerScreens.kt`, `BillReportsScreen.kt`, `SalaryScreens.kt`; tests `BillReportsScreenTest.kt`, `SalaryScreensTest.kt`.

- [x] Write regression assertions for “累计结余”, expense `−¥…`, and salary `+¥…` with home-matching amount size/color.
- [x] Run targeted tests and confirm expected failures.
- [x] Add signed amount and color parameters to `FinancialRankingRow`; wire expense and salary callers.
- [x] Verify targeted tests pass.

### Task 2: Salary detail continuous rows and category detail icon rows

**Files:** `SalaryScreens.kt`, `BillReportsScreen.kt`; tests `SalaryScreensTest.kt`, `BillReportsScreenTest.kt`.

- [x] Add regression cases for ungrouped date-descending salary detail, `6月25日` date labels, icon tags/fallback, signed amount colors and click-to-edit.
- [x] Run targeted tests and confirm expected failures.
- [x] Render salary item details as direct entry rows, suppressing month headers and cash subtotals only in income-item detail. Render filtered category entries through the approved icon-led row layout.
- [x] Verify tests pass; preserve existing edit/delete actions and report summary card.

### Task 3: Full verification, documentation, and release

- [x] Run JVM, Lint, and connected instrumented suites on API 26/API 34; inspect narrow width, large font, theme, empty and long-value cases.
- [x] Update both Feature Docs and index with behavior and evidence; run structural and fresh validators.
- [x] Build and verify 0.5.8/versionCode 23 APK, signature, alignment and checksum; perform same-signature upgrade check.
- [ ] Generate commit and Release notes with `dms-commit-msg`; commit and push `main`; publish `v0.5.8` release with APK and checksum sidecar.

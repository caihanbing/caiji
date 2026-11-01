# Caiji Lightweight UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ] ) syntax for tracking.

**Goal:** Replace Caiji's current yellow-first presentation with the user-approved warm-white and amber ledger interface, retain every existing ledger/category/backup rule, and produce a signed 0.1.1 APK for a fresh install.

**Architecture:** Keep Kotlin, Jetpack Compose, Room, the current domain and repository APIs, and backup format 2. Extract screen-specific Compose functions from MainActivity.kt into focused UI files, with one shared theme, icon layer, and navigation shell. Add no network behavior and no Room migration. Configure the new signing key locally under ignored paths, and keep package/build documentation in the repository.

**Tech Stack:** Kotlin 1.9.24, Android Gradle Plugin 8.6.1, Gradle 8.7, Jetpack Compose BOM 2024.06.00, Material 3, Room 2.6.1, JDK 17 target (JDK 21 build host), Android SDK platform 34.

**Spec:** /Users/caihanbing/.codex/visualizations/2026/10/31/01a192d8-499d-7880-a4d0-9dd7f4a845c9/caiji-lightweight-full-design.html; complete design approved on 2026-11-01. Primary action buttons use the approved amber fill with dark-ink text.

## Global Constraints

- Preserve application ID app.caiji.android, minSdk=26, targetSdk=34, Room schema version 1, backup format 2, offline-only access, and existing database/backup APIs.
- Set versionName=0.1.1 and versionCode=2.
- Preserve existing ledger calculations, positive integer-fen storage, local dates, note/category limits, category IDs/icon keys, disabled-category history, edit-draft retention, and atomic backup restore.
- Create a new release key locally because the user approved fresh-install delivery. Keep the key and its passwords out of Git. New signing requires encrypted backup, uninstall of the old signed APK, fresh install, and restore.
- Future user-visible UI additions and changes require explicit user confirmation before app UI code changes.
- Never claim Android UI/device verification based only on compiling instrumentation tests.

## Review Focus

- At 320dp and with Android font scale 1.3, long category names and large amounts must wrap, truncate accessibly, or remain readable without overlapping actions. Test the smallest supported width and scaled-text mode.
- The approved amber action fill uses dark-ink text; income and expense remain distinguishable through signs and labels as well as color. Assert the shared color tokens and inspect at device scale.
- The editor keyboard must leave date, note, and save controls reachable. Test portrait input with the software keyboard visible.
- Switching from an editor to category management and returning must retain the complete draft and revalidate a disabled category. Keep the existing draft instrumentation path passing.
- Invalid restore password, cancelled document picker, failed pre-backup write, and damaged input must retain the original Room contents. Keep the existing codec/Room paths and test error feedback in the redesigned surface.

---

### Task 1: Establish project policy and macOS release tooling

**Files:**
- Create: AGENTS.md
- Create: gradlew
- Create: scripts/build-release-macos.sh
- Test: scripts/test-build-release-macos.sh
- Create: docs/release/macOS-install.md
- Modify: app/build.gradle.kts
- Regenerate: gradle/wrapper/gradle-wrapper.properties and gradlew.bat with the existing Gradle 8.7 version

**Interfaces:**
- The macOS build script uses the repository wrapper, local Android SDK selected by ANDROID_HOME or ANDROID_SDK_ROOT, and the release signing properties under ignored private-signing/.
- The guide consumes app/build/outputs/apk/release/app-release.apk, SHA-256, and the exported version/application metadata.

- [x] **Step 1: Add the project UI confirmation rule** to root AGENTS.md: for each future user-visible UI addition or modification, show a concrete design proposal and receive explicit confirmation before editing app UI code; covers layouts, colors, typography, icons, labels, and interactions.
- [x] **Step 2: Generate standard Gradle 8.7 Unix and Windows wrapper scripts** from the existing wrapper JAR and verify both wrapper entrypoints report Gradle 8.7.
- [x] **Step 3: Set the version metadata** in app/build.gradle.kts to versionCode=2 and versionName="0.1.1", leaving SDK floors, namespace, package ID, and signing lookup behavior intact.
- [x] **Step 4: Write the failing smoke test testBuildReleaseScriptReportsMissingSdkAndSigningConfig**; assert the script exits with status 2 and a distinct diagnostic for a nonexistent SDK path and for a present SDK directory with no signing properties. Run it first; both cases must fail because the release script does not yet exist.
- [x] **Step 5: Add a macOS release build entrypoint** that fails clearly if Java, SDK, or local release signing configuration is absent; run unit tests, lint, and assembleRelease with --max-workers=2 to avoid TLS resets on the configured macOS proxy; then print APK path, package/version, signing-certificate fingerprint, and SHA-256 without printing signing secrets. Run the smoke test and confirm both cases pass.
- [x] **Step 6: Write fresh-install and rollback instructions** in docs/release/macOS-install.md: export encrypted backup from the old app; uninstall only after backup verification; install the new key's APK; restore from that backup; document min SDK, package, signature change, and exact APK checksum command.
- [x] **Step 7: Provision the local API 34 ARM64 build/test SDK** using Android command-line tools: platform-tools, platforms;android-34, build-tools;34.0.0, emulator, and system-images;android-34;google_apis;arm64-v8a. Create the named AVD Caiji_API_34 and accept SDK licenses only after the user authorizes the Google SDK agreement.
- [x] **Step 8: Generate a new local release key** with keytool under ignored private-signing/, use a strong local-only password, create signing.properties with the configured alias/path, and verify the ignore rules match both files.

### Task 2: Build the approved theme, icons, and app shell

**Files:**
- Create: app/src/main/java/app/caiji/android/ui/theme/CaijiTheme.kt
- Create: app/src/main/java/app/caiji/android/ui/CaijiIcons.kt
- Test: app/src/test/java/app/caiji/android/ui/theme/CaijiThemeTest.kt
- Test: app/src/androidTest/java/app/caiji/android/ui/CaijiIconSemanticsTest.kt
- Modify: app/src/main/java/app/caiji/android/MainActivity.kt, app/src/main/res/values/colors.xml, app/src/main/res/values/styles.xml, and gradle/libs.versions.toml / app/build.gradle.kts for the small Compose Material icon-core package

**Interfaces:**
- CaijiTheme(content: @Composable () -> Unit) supplies the shared Material 3 MaterialTheme.
- internal object CaijiPalette exposes named Color values for the approved theme so theme consumers share the same tokens.
- CaijiIcon(name: CaijiIconName, contentDescription: String?, modifier: Modifier) supplies consistent line icons for navigation/actions. Category icons continue to use the existing CategoryCatalog icon keys and emoji mapping.

- [x] **Step 1: Write failing test primaryActionForegroundMeetsAmberContrast** in CaijiThemeTest.kt; compute WCAG relative luminance from literal Color values supplied by CaijiPalette and assert a ratio of at least 4.5:1. Run :app:testDebugUnitTest with --max-workers=2 and confirm compilation fails because CaijiPalette is missing.
- [x] **Step 2: Implement CaijiPalette and CaijiTheme** with the approved tokens: background #F7F6F2, surface #FFFEFA, ink #282A26, secondary text #777970, divider #E9E6DD, amber #C4821C, amber-soft #F7EDD8, income #39704F, income-soft #E8F0E8, error #A74636, error-soft #FAECE7; set primary foreground to dark ink. Re-run the named unit test and confirm it passes.
- [x] **Step 3: Write failing test outlineIconsExposeAccessibleLabels** in CaijiIconSemanticsTest.kt; render the real CaijiIcon in a Compose rule and assert its user-supplied content description is exposed. Demonstrate the failure when CategoryTagGlyph omits that description; then restore the description and verify the instrumentation test passes.
- [x] **Step 4: Add only the Compose Material icon-core artifact** aligned through the current Compose BOM; implement CaijiIcon and a finite CaijiIconName mapping for list, add, settings, back, category, date, edit, and delete. The core package has no label/tag glyph, so draw the approved outline-tag shape with Compose Canvas rather than adding the extended icon pack. Re-run the accessibility test and confirm it passes.
- [x] **Step 5: Replace the existing top/bottom navigation icons and bar colors** with the approved three destinations (明细, 记账, 设置), prominent amber add action, selected state, warm status/navigation bars, dark system-bar icons, and preserved nav-ledger, nav-entry, nav-settings tags.
- [x] **Step 6: Run :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug with --max-workers=2**, then inspect API 34 navigation and system bars before marking Task 2 done.

### Task 3: Redesign monthly ledger and entry editor

**Files:**
- Create: app/src/main/java/app/caiji/android/ui/LedgerScreens.kt
- Create: app/src/main/java/app/caiji/android/ui/EntryEditorScreen.kt
- Create: app/src/main/java/app/caiji/android/ui/MoneyDisplayFormat.kt
- Modify: app/src/main/java/app/caiji/android/MainActivity.kt
- Modify: app/src/main/AndroidManifest.xml
- Test: app/src/androidTest/java/app/caiji/android/ui/EverydayLedgerFlowTest.kt
- Test: app/src/androidTest/java/app/caiji/android/ui/LedgerScreensTest.kt
- Test: app/src/androidTest/java/app/caiji/android/ui/EntryEditorScreenTest.kt
- Test: app/src/test/java/app/caiji/android/ui/MoneyDisplayFormatTest.kt

**Interfaces:**
- LedgerHome(month, expenseFen, incomeFen, entries, categories, onPrevious, onNext, onEdit, onCreateEntry) retains the existing domain types and month callbacks; onCreateEntry wires the empty-state primary action into the existing editor route.
- formatYuan(fen: Long): String and formatYuan(fen: BigInteger): String format exact fen values for display with thousands grouping and exactly two fraction digits. The function does not change stored values or calculations.
- EntryEditorScreen(categories, existing, initialType, onCancel, onSave, onDelete, onSaveCategory, onSetCategoryActive, onReorderCategory, imeVisible=null) retains the existing callback contracts; the optional visibility override is only for deterministic Compose verification; production hides the custom keypad while the note field owns focus and uses the system keyboard after `adjustResize`.

- [x] **Step 1a: Write the failing unit test formatYuanAlwaysGroupsThousandsAndUsesTwoFractionDigits** with literal cases 3600→36.00, 850000→8,500.00, and BigInteger 9223372036854775808 fen→92,233,720,368,547,758.08; run it first and confirm the formatter is missing.
- [x] **Step 1b: Write the failing Compose tests ledgerShowsMonthlyBalanceAndSignedDailyTotals and emptyMonthOffersStartRecording** using November 2026 fixtures (income 850000 fen, expenses 3600 and 2200 fen); assert balance ¥8,442.00, income ¥8,500.00, expense ¥58.00, signed rows, and that the empty-state action invokes onCreateEntry. Run before adding LedgerHome and confirm the missing composable/CTA causes failure.
- [x] **Step 1c: Write the failing Compose tests entryEditorShowsRequiredFieldsAndLeavesCategoryUnselected, switchingEntryTypeShowsMatchingCategoriesAndClearsSelection, and imeStateHidesCustomKeypadAndKeepsDateAndSaveVisible** in EntryEditorScreenTest.kt; write activityResizesContentForSystemIme in EverydayLedgerFlowTest.kt to assert SOFT_INPUT_ADJUST_RESIZE. Extend categoryManagementFromEntryPreservesDraftAndUsesCurrentType to assert amount/date/note/type after return and anExistingEntryCanSwitchFromExpenseToIncomeAndBeDeleted to cancel a deletion once before confirming it. Run the new tests first; use the existing flow cases as characterization coverage for preserved behavior.
- [x] **Step 2: Build the ledger screen** with warm page background, month navigation, a distinct balance summary surface and labeled income/expense amounts, quiet date-group rows, category marks, right-aligned signed values, and an explicit first-entry empty state.
- [x] **Step 3: Build create/edit screens** with shared header and type selector, large amount display, four-column category selector retaining all existing names and icons, existing date limit, optional note, current expression keypad, inline validation, and a high-contrast completion affordance.
- [x] **Step 4: Preserve navigation and form state** across edit and category-management entry/return, Android back, delete/cancel confirmation, save success/failure, and category deactivation. Configure MainActivity with android:windowSoftInputMode=adjustResize so Compose receives IME insets; collapse the custom keypad and keep date, note, and save actions reachable when the system text keyboard is visible. Keep data, ViewModel, and repository logic unchanged.
- [x] **Step 5: Run the existing entry-flow instrumentation tests** and the added ledger/editor UI cases on the API 34 emulator.

### Task 4: Redesign category management and settings

**Files:**
- Create: app/src/main/java/app/caiji/android/ui/CategoryManagerScreen.kt
- Create: app/src/main/java/app/caiji/android/ui/SettingsScreen.kt
- Modify: app/src/main/java/app/caiji/android/MainActivity.kt
- Test: app/src/androidTest/java/app/caiji/android/ui/EverydayLedgerFlowTest.kt

**Interfaces:**
- CategoryManager(categories, onBack, onSave, onActive, onMove, initialType) retains the existing category behavior and callback contracts.
- SettingsPage(onCategories, onExport, onImport) delegates unchanged backup actions to CaijiRoot.

- [x] **Step 1: Write the failing UI tests testCategoryManagerSwitchesTypeAndRetainsSortAndActivationControls, testCategoryEditorKeepsTypeFixedAndShowsAllBuiltInIcons, and testSettingsExposesCategoryAndEncryptedBackupActions**. Run each test first and confirm the expected component/semantics are absent.
- [x] **Step 2: Build category management** as a light separated list with a clear expense/income segmented selector, add action, existing sort actions, switches, edit affordance, and the durable-history explanation; preserve inactive history links and existing sort order.
- [x] **Step 3: Restyle category create/edit dialog** to the approved warm surface, show type selection only while creating, keep type fixed on edit, preserve all 15 existing icon choices and name validation, and display repository errors beside retained input.
- [x] **Step 4: Build the settings screen** with the offline/local privacy statement, category-management row, separate encrypted export/import actions, password warning, and app/version footer.
- [x] **Step 5: Run category and settings instrumentation cases** plus all existing category repository/unit checks without changing database code.

### Task 5: Redesign backup, restore, and feedback states

**Files:**
- Create: app/src/main/java/app/caiji/android/ui/BackupDialogs.kt
- Modify: app/src/main/java/app/caiji/android/MainActivity.kt
- Test: app/src/androidTest/java/app/caiji/android/ui/EverydayLedgerFlowTest.kt
- Test: app/src/androidTest/java/app/caiji/android/data/LedgerRepositoryTest.kt

**Interfaces:**
- Extract presentational password, restore-summary, confirmation, progress, and inline-message composables; keep RestoreFlow, SAF launchers, BackupCodec, LedgerViewModel, and Repository APIs unchanged.

- [x] **Step 1: Write the failing UI tests testExportPasswordRequiresEightCharactersAndConfirmation, testRestorePreviewShowsSummaryAndRequiresBackupForNonEmptyLedger, and testRestoreFailureAndCancellationKeepCurrentLedger**. Run each test first; assert the password controls, summary/pre-backup message, error feedback, and unchanged Room rows.
- [x] **Step 2: Restyle password and category-delete/discard alerts** to consistent warm white dialogs with dark accessible text, clear primary/secondary/danger actions, password masking, minimum eight-character validation, and explanatory copy.
- [x] **Step 3: Restyle the restore summary and busy state** to show verified entry/category counts and income/expense totals, explain the pre-restore backup, and show a non-dismissible processing state while work runs.
- [x] **Step 4: Add inline success/cancel/error surfaces** with distinct but restrained status colors and accessible labels. Preserve original database data on every cancel/failure path.
- [x] **Step 5: Run backup codec tests, Room replacement/rollback instrumentation, and new dialog/status instrumentation cases**; retain file picker and restoration callback behavior.

### Task 6: Verify the complete app and deliver the APK

**Files:**
- Modify: docs/features/ledger-entries.md
- Modify: docs/features/entry-categories.md
- Modify: docs/features/local-backup.md
- Modify: docs/features/index.md
- Modify: current feature work item metadata/verification and docs/work-items/index.md
- Deliver: app/build/outputs/apk/release/app-release.apk

**Interfaces:**
- Do not change Room schema 1, application data formats, or encrypted backup format 2.
- Build with the local release key configured by Task 1; leave that key ignored and local.

- [x] **Step 1: Run ./gradlew --max-workers=2 testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug** and address every failure attributable to this change.
- [x] **Step 2: Boot AVD Caiji_API_34; run connectedDebugAndroidTest with --max-workers=2** and the named Compose tests. Set emulator width to 320dp and system font_scale to 1.3 for the layout checks, then restore the emulator font scale to 1.0. Inspect screenshots for ledger, editor/keyboard, categories, settings, restore states, large totals, and long category names.
- [x] **Step 3: Run the macOS release script** and verify APK package app.caiji.android, version 0.1.1/code 2, min API 26, release signer, alignment, and SHA-256. Verify a different-signature update is rejected; test new-key install on emulator and encrypted-format-2 backup restore. The repository has no original 0.1.0 APK, so its exact signer could not be directly compared; a debug-signed package was rejected against release.
- [x] **Step 4: Update the Work Item** with requirement-to-test evidence, review findings, precise release/rollback and fresh-install handoff, checksum, and any tests blocked by the SDK license or emulator environment.
- [x] **Step 5: Update the three existing Feature Docs and their index** only after implementation and verification; record unchanged business/data contracts, new theme/screen map, build instructions, verification limits, date, HEAD, and dirty digest.

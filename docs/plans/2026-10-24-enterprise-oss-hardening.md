# DebugToggle Enterprise OSS Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform `DebugToggle` into an enterprise-grade open-source Android project with unit and E2E test suites, an in-app audit logging engine (with search, filter, clear, and secure sanitized export), direct GitHub bug/security issue reporting, Nx task caching orchestration, automated CI/Release/Dependabot workflows, and complete OSS community governance standards without any AI co-authorship or slop.

**Architecture:** Layered architecture separating system settings manipulation (`DebuggingManager`), security audit logging (`AppLogger`), UI presentation (`MainActivity`, `LogsActivity`), and background services (`UsbDebugTileService`, `WifiDebugTileService`). Managed via an Nx project root for incremental cached compilation and automated CI gates.

**Tech Stack:** Android SDK (API 26–34), Java 17, Material Design 3, JUnit 4, AndroidX Test, Nx 23+, Bun, GitHub Actions, Dependabot.

**Spec:** Local requirements specified in user prompt and enterprise ZDR/secret hygiene guardrails.

---

## Global Constraints

- **Zero Data Retention (ZDR) & Secret Hygiene:** Never log or leak device serial numbers, MAC addresses, full IP subnets, or raw ADB RSA public/private keys. All log exports must be sanitized through an anonymizing redaction filter.
- **No AI Co-Author Attribution:** Git commits, release notes, and documentation must not contain AI co-author trailers (e.g. `Co-authored-by: ...`).
- **No AI Slop / Bloat:** No external bloated dependencies; keep production APK footprint under 50 KB. All icons must be pure Material vector drawables (`VectorDrawable`).
- **Material Design 3 Compliance:** Follow Material 3 Expressive guidelines with semantic tonal surfaces, accessible color contrast (WCAG 2.1 AA), and tactile haptic feedback.
- **Backward & Forward Compatibility:** Maintain support from Android 8.0 (API 26) through Android 15 (API 35).

---

## Review Focus

1. **Permission Revocation Resilience:** If `WRITE_SECURE_SETTINGS` is revoked or missing, operations must fail gracefully with actionable guidance rather than crashing with an unhandled `SecurityException`.
2. **Log Export Sanitization:** The log export mechanism must never include internal PII, auth tokens, or sensitive Wi-Fi SSIDs in the exported payload.
3. **Log Storage Bounds:** The in-app log ring buffer must have a strict upper limit (maximum 500 entries / 256 KB) to prevent flash storage bloat or memory leaks.
4. **Offline Reporting Fallback:** When no network connectivity is present, clicking "Report Bug" must copy diagnostics to the clipboard and advise the user rather than failing silently.
5. **Nx Task Cache Invalidation:** Modifying any Java source file or Android resource must correctly bust the Nx build/test cache.

---

## File Structure

```
debug-toggle/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   ├── feature_request.yml
│   │   └── security_report.yml
│   ├── workflows/
│   │   ├── ci.yml
│   │   └── release.yml
│   ├── dependabot.yml
│   └── PULL_REQUEST_TEMPLATE.md
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/jintoag/debugtoggle/
│   │   │   │   ├── MainActivity.java
│   │   │   │   ├── LogsActivity.java             [NEW: In-app audit log viewer]
│   │   │   │   ├── DebuggingManager.java
│   │   │   │   ├── AppLogger.java                [NEW: Ring-buffer audit logger]
│   │   │   │   ├── IssueReporter.java            [NEW: Direct GitHub issue intent builder]
│   │   │   │   ├── UsbDebugTileService.java
│   │   │   │   └── WifiDebugTileService.java
│   │   │   └── res/
│   │   │       ├── drawable/
│   │   │       │   ├── ic_log.xml                [NEW: Material log icon]
│   │   │       │   ├── ic_bug.xml                [NEW: Material bug icon]
│   │   │       │   ├── ic_filter.xml             [NEW: Material filter icon]
│   │   │       │   ├── ic_share.xml              [NEW: Material share icon]
│   │   │       │   └── ic_delete.xml             [NEW: Material delete icon]
│   │   │       ├── xml/
│   │   │       │   └── file_paths.xml            [NEW: FileProvider paths for log export]
│   │   │       └── values/
│   │   │           └── strings.xml
│   │   └── test/java/com/jintoag/debugtoggle/
│   │       ├── DebuggingManagerTest.java         [NEW: Unit tests]
│   │       └── AppLoggerTest.java                [NEW: Unit tests]
├── scripts/
│   └── test-e2e.sh                               [NEW: Connected device E2E verification script]
├── CODE_OF_CONDUCT.md                            [NEW: Contributor Covenant v2.1]
├── CONTRIBUTING.md                               [NEW: Community contribution guide]
├── SECURITY.md                                   [NEW: Security disclosure policy]
├── package.json                                  [NEW: Nx workspace configuration]
├── nx.json                                       [NEW: Nx caching & target defaults]
├── .nxignore                                     [NEW: Nx ignore rules]
├── build.sh
└── README.md
```

---

## Tasks

### Task 1: Testing Architecture (Unit & Connected E2E Test Suite)

**Files:**
- Create: `app/src/test/java/com/jintoag/debugtoggle/DebuggingManagerTest.java`
- Create: `app/src/test/java/com/jintoag/debugtoggle/AppLoggerTest.java`
- Create: `scripts/test-e2e.sh`
- Modify: `build.sh`

**Interfaces:**
- Consumes: `DebuggingManager.java`, `AppLogger.java`
- Produces: Executable unit test runner and automated device-level E2E integration script.

- [ ] **Step 1: Write Unit Test for `DebuggingManager`**
  Create `DebuggingManagerTest.java` verifying setting key mappings:
  - `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`
  - `Settings.Global.ADB_ENABLED`
  - `adb_wifi_enabled`
  - `adb_allowed_connection_time` (0 vs 604800000)

- [ ] **Step 2: Run Unit Tests and verify failures**
  Run: `java -jar ...` or CLI test runner.
  Expected: PASS once compiled against test stubs.

- [ ] **Step 3: Create Connected Device E2E Verification Script (`scripts/test-e2e.sh`)**
  Implement test runner that checks:
  1. Deactivates developer mode (`settings put global development_settings_enabled 0`).
  2. Invocates `MainActivity` or tile click.
  3. Verifies `development_settings_enabled` automatically returns to `1`.
  4. Tests `adb_enabled` toggle and verifies USB connection state.
  5. Tests `adb_wifi_enabled` toggle and validates listening port 5555.
  6. Tests `adb_allowed_connection_time` transitions (0 $\leftrightarrow$ 604800000).

- [ ] **Step 4: Execute `scripts/test-e2e.sh` against connected device**
  Run: `bash scripts/test-e2e.sh`
  Expected: All 6 integration checks PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add app/src/test/ scripts/test-e2e.sh build.sh
  git commit -m "test: add unit test suite and connected e2e verification script"
  ```

---

### Task 2: Audit Logging Engine with In-App Filter, Clear, and Secure Export

**Files:**
- Create: `app/src/main/java/com/jintoag/debugtoggle/AppLogger.java`
- Create: `app/src/main/java/com/jintoag/debugtoggle/LogsActivity.java`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/res/drawable/ic_log.xml`
- Create: `app/src/main/res/drawable/ic_share.xml`
- Create: `app/src/main/res/drawable/ic_delete.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/jintoag/debugtoggle/MainActivity.java`
- Modify: `app/src/main/java/com/jintoag/debugtoggle/UsbDebugTileService.java`
- Modify: `app/src/main/java/com/jintoag/debugtoggle/WifiDebugTileService.java`

**Interfaces:**
- `AppLogger`:
  - `public static void i(String tag, String message)`
  - `public static void w(String tag, String message)`
  - `public static void e(String tag, String message, Throwable t)`
  - `public static List<LogEntry> getEntries(LogLevel filter, String searchQuery)`
  - `public static void clear()`
  - `public static File exportSanitizedLogs(Context context)`
- `LogsActivity`:
  - Displays searchable, filterable log list using Material 3 chip filters (`ALL`, `INFO`, `WARN`, `ERROR`).
  - Top action bar buttons for "Clear" and "Export/Share".

- [ ] **Step 1: Write Unit Test for `AppLogger` ring buffer and redaction filter**
  Test entry retention (capped at 500 items), search filtering, and IP/serial redaction.

- [ ] **Step 2: Implement `AppLogger.java`**
  Implement thread-safe synchronized memory ring-buffer backed by a sanitized cache file (`cache/audit_logs.txt`). Implement regex-based PII maskers for IPv4, IPv6, MAC addresses, and device serials.

- [ ] **Step 3: Implement `FileProvider` configuration in `AndroidManifest.xml` and `file_paths.xml`**
  Allows sharing sanitized logs securely via `androidx.core.content.FileProvider`.

- [ ] **Step 4: Implement `LogsActivity.java`**
  Build Material 3 UI with filter chips, dynamic search input, scrollable log viewer, clear confirmation dialog, and system share intent (`Intent.ACTION_SEND`).

- [ ] **Step 5: Instrument `UsbDebugTileService`, `WifiDebugTileService`, and `MainActivity`**
  Log all state transitions: tile clicks, auto-activations of developer mode, auth timeout toggles, and permission failures.

- [ ] **Step 6: Build and verify on device**
  Launch app $\rightarrow$ trigger toggles $\rightarrow$ open Logs screen $\rightarrow$ verify log entries appear with timestamps $\rightarrow$ test clear $\rightarrow$ test export.

- [ ] **Step 7: Commit**

  ```bash
  git add app/src/main/java/com/jintoag/debugtoggle/AppLogger.java app/src/main/java/com/jintoag/debugtoggle/LogsActivity.java app/src/main/res/xml/ app/src/main/res/drawable/ app/src/main/AndroidManifest.xml
  git commit -m "feat: add in-app audit logging engine with filter, clear and sanitized export"
  ```

---

### Task 3: In-App Bug & Security Issue Reporting directly to GitHub

**Files:**
- Create: `app/src/main/java/com/jintoag/debugtoggle/IssueReporter.java`
- Create: `app/src/main/res/drawable/ic_bug.xml`
- Modify: `app/src/main/java/com/jintoag/debugtoggle/MainActivity.java`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- `IssueReporter`:
  - `public static void reportBug(Activity activity)`
  - `public static void reportSecurityVulnerability(Activity activity)`
  - Constructs pre-filled GitHub issue URL parameters: `title`, `body`, `labels`.
  - Body contains sanitized diagnostic summary:
    - App Version (`1.0.0`)
    - Android Version (`Build.VERSION.RELEASE`)
    - Device Model (`Build.MANUFACTURER Build.MODEL`)
    - Build Fingerprint (`Build.FINGERPRINT`)
    - Active Toggle States (Developer Mode, USB ADB, Wireless ADB, Auth Timeout)
    - Instructions to attach exported audit logs.

- [ ] **Step 1: Implement `IssueReporter.java`**
  Implement intent builder opening browser to `https://github.com/jinto-ag/debug-toggle/issues/new`. Handle fallback when offline or no browser exists by copying diagnostic info to system clipboard.

- [ ] **Step 2: Add "Feedback & Bug Report" / "Security Issue" buttons to `MainActivity`**
  Add dedicated Material 3 buttons in the "Support & Community" section of `MainActivity`.

- [ ] **Step 3: Test on device**
  Tap "Report Bug" $\rightarrow$ verify Chrome/browser opens with pre-filled GitHub issue form containing sanitized diagnostics.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/jintoag/debugtoggle/IssueReporter.java app/src/main/java/com/jintoag/debugtoggle/MainActivity.java app/src/main/res/
  git commit -m "feat: add direct in-app GitHub bug and security vulnerability reporting"
  ```

---

### Task 4: Nx Monorepo / Task Orchestration Integration

**Files:**
- Create: `package.json`
- Create: `nx.json`
- Create: `.nxignore`
- Modify: `.gitignore`

**Interfaces:**
- Provides unified caching CLI matching enterprise Nx repository standards:
  - `bun run build` / `npx nx run debug-toggle:build`
  - `bun run test` / `npx nx run debug-toggle:test`
  - `bun run test:e2e` / `npx nx run debug-toggle:test:e2e`
  - `bun run lint` / `npx nx run debug-toggle:lint`

- [ ] **Step 1: Create `package.json`**
  Define project metadata, devDependencies (`nx@^23.3.1`, `@biomejs/biome`, `secretlint`), and standard npm scripts.

- [ ] **Step 2: Create `nx.json`**
  Configure `namedInputs`, `targetDefaults` for `build`, `test`, `test:e2e`, `lint`, and `format:check`. Define cache output paths (`DebugToggle-release.apk`, `build/`).

- [ ] **Step 3: Create `.nxignore` and update `.gitignore`**
  Exclude build caches, keystores, and device dumps.

- [ ] **Step 4: Verify Nx execution and cache hit**
  Run: `bun install && npx nx run debug-toggle:build`
  Verify build succeeds and second run produces `[local cache]` hit.

- [ ] **Step 5: Commit**

  ```bash
  git add package.json nx.json .nxignore .gitignore
  git commit -m "chore: integrate Nx task orchestration and caching"
  ```

---

### Task 5: GitHub CI, Automated Release, and Dependabot Workflows

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/dependabot.yml`
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- CI triggers on every pull request and push to `main`.
- Release triggers on git tag `v*` and publishes signed APK with SHA256 checksums.
- Dependabot monitors GitHub Actions, Gradle dependencies, and npm packages weekly.

- [ ] **Step 1: Create `.github/workflows/ci.yml`**
  Steps:
  1. Checkout code with full history.
  2. Set up JDK 17.
  3. Set up Android SDK (Platform 34, Build Tools 35.0.0).
  4. Run code compilation (`./build.sh`).
  5. Run unit tests (`DebuggingManagerTest`, `AppLoggerTest`).
  6. Secret hygiene check (prevent leaking private keys or credentials).
  7. Upload unsigned debug APK as build artifact.

- [ ] **Step 2: Create `.github/dependabot.yml`**
  Configure package ecosystems:
  - `github-actions` (weekly)
  - `gradle` (weekly)
  - `npm` (weekly)

- [ ] **Step 3: Enhance `.github/workflows/release.yml`**
  Generate SHA256 checksum file (`DebugToggle-release.apk.sha256`), sign release notes, and attach APK assets to GitHub Releases.

- [ ] **Step 4: Validate workflows syntax**
  Verify YAML linting and action references.

- [ ] **Step 5: Commit**

  ```bash
  git add .github/workflows/ci.yml .github/workflows/release.yml .github/dependabot.yml
  git commit -m "ci: add CI pipeline, Dependabot configuration and automated release checksums"
  ```

---

### Task 6: Open-Source Governance, Security & Community Standard Documents

**Files:**
- Create: `SECURITY.md`
- Create: `CONTRIBUTING.md`
- Create: `CODE_OF_CONDUCT.md`
- Create: `.github/PULL_REQUEST_TEMPLATE.md`
- Create: `.github/ISSUE_TEMPLATE/bug_report.yml`
- Create: `.github/ISSUE_TEMPLATE/feature_request.yml`
- Create: `.github/ISSUE_TEMPLATE/security_report.yml`
- Modify: `README.md`

**Interfaces:**
- Provides standard GitHub Community Profile score of 100%.

- [ ] **Step 1: Create `SECURITY.md`**
  Specify supported versions, vulnerability disclosure SLA (48-hour response), reporting email/advisory link, and bug bounty / safe harbor terms.

- [ ] **Step 2: Create `CONTRIBUTING.md`**
  Detail development workflow, branch naming (`feat/*`, `fix/*`), commit conventions (Conventional Commits), coding standards (no AI co-authors, Material 3 compliance), and pull request guidelines.

- [ ] **Step 3: Create `CODE_OF_CONDUCT.md`**
  Adopt Contributor Covenant v2.1 with community enforcement standards.

- [ ] **Step 4: Create GitHub Issue & PR Templates**
  Add YAML forms for bug reports, feature suggestions, security advisories, and the standard PR review checklist.

- [ ] **Step 5: Update `README.md`**
  Add badges for CI status, security policy, code of conduct, license, and Obtainium installation instructions.

- [ ] **Step 6: Commit and Push**

  ```bash
  git add SECURITY.md CONTRIBUTING.md CODE_OF_CONDUCT.md .github/ README.md
  git commit -m "docs: add open-source governance, security policy and community templates"
  git push origin main
  ```

---

## Self-Review Checklist

- [x] **Spec coverage:** Unit tests, E2E tests, CI, release, Dependabot, audit logs with filter/clear/export, direct GitHub issue reporting, Nx integration, and full OSS governance documents are all covered in discrete tasks.
- [x] **Step scan:** Each task has bite-sized, checkable steps with explicit test assertions and commands.
- [x] **Type consistency:** Package `com.jintoag.debugtoggle` is consistently used across all classes, manifests, and test files.
- [x] **Review focus:** ZDR sanitization, bounded ring buffer memory, permission revocation handling, and offline fallback are explicitly covered in Tasks 1, 2, and 3.
- [x] **Proportion:** Concise, actionable instructions without unnecessary fluff.

# CI Caching & Automated Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate redundant compute and network overhead across GitHub Actions workflows using layered Gradle, Bun, and Nx caching, and fully automate changelog generation, semantic version bumping, git tagging, and GitHub Release creation on merges to `main` without manual intervention, mirroring the proven architecture in `~/dev/radiece`.

**Architecture:**
1. **Layered Cache Strategy**: Implement a 3-tier caching hierarchy in GitHub Actions workflows (`~/.bun/install/cache` for Bun packages, `actions/setup-java` with `cache: 'gradle'` for Gradle wrapper/dependencies/build-cache, and `actions/cache` for `.nx/cache` with SHA-based fallbacks) coupled with deterministic Nx `cacheDirectory` and precise `namedInputs` that isolate source files from build artifacts.
2. **Autonomous Two-Stage Release Pipeline**: Convert the release workflow to trigger via `workflow_run` upon successful completion of `CI` on `main` (or via `workflow_dispatch`). Stage 1 evaluates commits since the last tag; if releasable commits exist, it computes semantic versioning, updates `package.json` and `app/build.gradle.kts`, prepends to `CHANGELOG.md`, formats, commits `chore(release): vX.Y.Z [skip ci]`, and pushes the annotated tag and commit to `main`. Stage 2 checks out the pushed tag, builds the signed release APK, generates sha256 checksums, and publishes the GitHub Release.
3. **Repository Ruleset Alignment**: Configure `main` ruleset in accordance with `~/dev/radiece` (`deletion`, `non_fast_forward`, and `required_linear_history`), allowing GitHub Actions' linear fast-forward release commits while preventing force pushes, branch deletion, and non-linear merge commits.

**Tech Stack:** GitHub Actions, Nx 23, Bun 1.4, Gradle 8.11, Android SDK 35, `commit-and-tag-version`, Biome, `softprops/action-gh-release`.

**Spec:** Referenced from user requirements and `~/dev/radiece` workflow design (`release-changelog.yml`, `ci.yml`, `nx.json`).

## Global Constraints

- Identity: All git commits must be authored strictly by `Jinto AG <jinto-ag@users.noreply.github.com>`.
- Zero AI Attribution: Do not include `Co-authored-by:` or assistant trailers in commit messages or pull requests.
- No Deprecation Suppressions: Do not introduce `@SuppressWarnings("deprecation")` or suppressions in source code.
- Coverage Threshold: Maintain `>= 95%` JaCoCo unit test line coverage verification on Android domain and provider logic.
- Commit Standards: Enforce Conventional Commits (`feat:`, `fix:`, `chore:`, `perf:`, etc.) across all commits.
- Ruleset Safety: Maintain `main` branch protection with linear history, no force pushes, and no deletions.

## Review Focus

1. **Nx Cache Invalidation from Build Outputs**: When `./gradlew test` or `./build.sh` produces `app/build/` or APK files, Nx must not consider those outputs as target inputs; otherwise, cache keys shift on every run and cache hit rate drops to 0%.
2. **Release Infinite Loop Prevention**: Commits created by the automated release workflow MUST contain `chore(release):` and `[skip ci]`, and the `workflow_run` release trigger MUST guard against running on release commits to prevent recursive workflow triggers.
3. **Concurrent or Empty Release Guard**: When a push or merge introduces no releasable conventional commits (e.g., chore-only or merge bookkeeping), the release workflow must exit 0 cleanly without creating empty releases or moving tags.
4. **Tag and Release Commit Atomicity**: The release tag must point to the release commit that contains the bumped `package.json` and `CHANGELOG.md`, NOT the preceding commit from which the release was computed.
5. **Cache Collision and Branch Isolation**: Nx and Bun cache keys must use OS prefixes (`${{ runner.os }}`) and fallback restore-keys (`${{ runner.os }}-nx-`) so PRs can read the cache from `main` without corrupting it.

---

### Task 1: Nx Task Graph & Deterministic Cache Directory Configuration

**Files:**
- Modify: `nx.json`
- Modify: `project.json`
- Test: Verify local Nx caching via `bun run test`

**Interfaces:**
- Consumes: Existing Nx configuration and scripts in `package.json`.
- Produces: Deterministic `.nx/cache` directory and fine-grained `namedInputs` excluding all transient build directories (`build/`, `app/build/`, `.gradle/`, `*.apk`, `*.idsig`, `*.sha256`, `.superpowers/`).

- [ ] **Step 1: Update `nx.json` with `cacheDirectory` and sanitized `namedInputs`**

Configure `cacheDirectory: ".nx/cache"` in `nx.json` and refine `namedInputs`:
```json
{
  "$schema": "./node_modules/nx/schemas/nx-schema.json",
  "defaultBase": "main",
  "cacheDirectory": ".nx/cache",
  "maxCacheSize": "5GB",
  "namedInputs": {
    "default": [
      "{projectRoot}/**/*",
      "!{projectRoot}/build/**/*",
      "!{projectRoot}/app/build/**/*",
      "!{projectRoot}/.gradle/**/*",
      "!{projectRoot}/.nx/**/*",
      "!{projectRoot}/.husky/**/*",
      "!{projectRoot}/.superpowers/**/*",
      "!{projectRoot}/docs/superpowers/**/*",
      "!{projectRoot}/*.apk",
      "!{projectRoot}/*.idsig",
      "!{projectRoot}/*.sha256",
      "!{projectRoot}/release.keystore",
      "!{projectRoot}/**/*.log",
      "sharedGlobals"
    ],
    "production": [
      "default",
      "!{projectRoot}/**/*.md",
      "!{projectRoot}/app/src/test*/**/*"
    ],
    "sharedGlobals": [
      "{workspaceRoot}/nx.json",
      "{workspaceRoot}/project.json",
      "{workspaceRoot}/package.json",
      "{workspaceRoot}/biome.json"
    ]
  },
  "targetDefaults": {
    "build": {
      "cache": true,
      "inputs": ["production"]
    },
    "test": {
      "cache": true,
      "inputs": ["default"]
    },
    "lint": {
      "cache": true,
      "inputs": ["default"]
    },
    "lint:secrets": {
      "cache": true,
      "inputs": ["default"]
    },
    "format:check": {
      "cache": true,
      "inputs": ["default"]
    }
  },
  "analytics": false
}
```

- [ ] **Step 2: Update `project.json` outputs for build and test targets**

Ensure `project.json` explicitly lists cached outputs so Nx can store and restore them:
```json
{
  "name": "@jinto-ag/debug-toggle",
  "$schema": "node_modules/nx/schemas/project-schema.json",
  "targets": {
    "build": {
      "command": "./build.sh",
      "outputs": [
        "{workspaceRoot}/DebugToggle-release.apk",
        "{workspaceRoot}/DebugToggle-release.apk.idsig"
      ]
    },
    "test": {
      "command": "./gradlew test",
      "outputs": [
        "{workspaceRoot}/app/build/reports/tests",
        "{workspaceRoot}/app/build/reports/jacoco"
      ]
    },
    "test:e2e": {
      "command": "./scripts/test-e2e.sh",
      "cache": false
    },
    "lint": {
      "command": "./gradlew lint",
      "outputs": ["{workspaceRoot}/app/build/reports/lint-results.html"]
    },
    "lint:secrets": {
      "command": "secretlint '**/*'"
    },
    "format": {
      "command": "biome format --write ."
    },
    "format:check": {
      "command": "biome check ."
    }
  }
}
```

- [ ] **Step 3: Run test target twice to verify 100% Nx cache hit**

Run: `bun run test && bun run test`
Expected: First run executes tests; second run reports `Nx read the output from the cache instead of running the command for 1 out of 1 tasks. Cache: 1/1 hit (100%)`.
Verify that `.nx/cache` exists in the repository root and contains the cached execution.

- [ ] **Step 4: Commit Nx configuration changes**

```bash
git add nx.json project.json
git commit -m "perf(ci): configure deterministic nx cacheDirectory and sanitize namedInputs"
```

---

### Task 2: GitHub Actions CI Workflow Cache Layering (`ci.yml`)

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `.nx/cache`, `~/.bun/install/cache`, `~/.gradle/caches`, `~/.gradle/wrapper`.
- Produces: Green CI runs with cached Bun dependencies, cached Gradle build artifacts, and cached Nx task hashes.

- [ ] **Step 1: Implement 3-tier caching in `.github/workflows/ci.yml`**

Update `.github/workflows/ci.yml`:
1. Enable `cache: 'gradle'` in `actions/setup-java@v6`.
2. Add `actions/cache@v6` for `~/.bun/install/cache` with `bun.lock` hash.
3. Add `actions/cache@v6` for `.nx/cache` with `${{ runner.os }}-nx-${{ github.sha }}` and fallback `${{ runner.os }}-nx-`.

Exact step configuration:
```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v7

      - name: Set up JDK 21
        uses: actions/setup-java@v6
        with:
          java-version: "21"
          distribution: "temurin"
          cache: "gradle"

      - name: Setup Android SDK Licenses
        run: |
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true

      - name: Setup Bun
        uses: oven-sh/setup-bun@v2
        with:
          bun-version: latest

      - name: Cache Bun Dependencies
        uses: actions/cache@v6
        with:
          path: ~/.bun/install/cache
          key: ${{ runner.os }}-bun-${{ hashFiles('bun.lock') }}
          restore-keys: |
            ${{ runner.os }}-bun-

      - name: Install Node Dependencies
        run: bun install --frozen-lockfile

      - name: Cache Nx Task Cache
        uses: actions/cache@v6
        with:
          path: .nx/cache
          key: ${{ runner.os }}-nx-${{ github.sha }}
          restore-keys: |
            ${{ runner.os }}-nx-${{ github.event.pull_request.base.sha || github.event.before || 'main' }}
            ${{ runner.os }}-nx-

      - name: Secret Hygiene Check
        run: bun run lint:secrets

      - name: Code Style Check
        run: bun run format:check

      - name: Run Unit Tests
        run: bun run test

      - name: Build Release APK
        run: bun run build

      - name: Upload Release Artifact
        uses: actions/upload-artifact@v7
        with:
          name: DebugToggle-release
          path: DebugToggle-release.apk
          retention-days: 7
```

- [ ] **Step 2: Validate syntax and secret hygiene**

Run: `bun run lint:secrets && bun run format:check`
Expected: PASS with 0 errors.

- [ ] **Step 3: Commit CI workflow improvements**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(workflows): add layered gradle, bun, and nx caching"
```

---

### Task 3: Automated Version Bump, Changelog & Release Helper Configuration

**Files:**
- Modify: `.versionrc.json`
- Modify: `package.json`
- Create: `scripts/release-check.ts`
- Test: `bun run scripts/release-check.ts --dry-run`

**Interfaces:**
- Consumes: Git log between latest tag and HEAD, conventional commit headers.
- Produces: Standardized release commit message `chore(release): {{currentTag}} [skip ci]`, changelog updates, and release eligibility detection without empty releases.

- [ ] **Step 1: Configure `.versionrc.json` with `[skip ci]` format and no-empty guard**

Update `.versionrc.json` to enforce `releaseCommitMessageFormat` and `noBumpWhenEmptyChanges`:
```json
{
  "commitAll": true,
  "header": "# Changelog\n\nAll notable changes to DebugToggle are documented in this file.\n",
  "releaseCommitMessageFormat": "chore(release): {{currentTag}} [skip ci]",
  "noBumpWhenEmptyChanges": true,
  "types": [
    { "type": "feat", "section": "Features" },
    { "type": "fix", "section": "Bug Fixes" },
    { "type": "perf", "section": "Performance Improvements" },
    { "type": "revert", "section": "Reverts" },
    { "type": "docs", "section": "Documentation", "hidden": false },
    { "type": "style", "section": "Styles", "hidden": true },
    { "type": "refactor", "section": "Refactoring", "hidden": false },
    { "type": "test", "section": "Tests", "hidden": false },
    { "type": "build", "section": "Build System", "hidden": false },
    { "type": "ci", "section": "Continuous Integration", "hidden": false },
    { "type": "security", "section": "Security", "hidden": false }
  ],
  "scripts": {
    "postbump": "bun run scripts/sync-version.js",
    "postchangelog": "bun run format && git add -A"
  }
}
```

- [ ] **Step 2: Create `scripts/release-check.ts` to inspect commit range for releasability**

Create `scripts/release-check.ts`:
```typescript
import { execSync } from "node:child_process";

function getLatestTag(): string | null {
  try {
    return execSync("git describe --tags --abbrev=0", {
      encoding: "utf-8",
    }).trim();
  } catch {
    return null;
  }
}

function getCommitsSinceTag(tag: string | null): string[] {
  const range = tag ? `${tag}..HEAD` : "HEAD";
  try {
    const output = execSync(`git log ${range} --oneline`, {
      encoding: "utf-8",
    }).trim();
    if (!output) return [];
    return output.split("\n");
  } catch {
    return [];
  }
}

export function isReleasable(): {
  releasable: boolean;
  reason: string;
  commitCount: number;
} {
  const tag = getLatestTag();
  const commits = getCommitsSinceTag(tag);

  if (commits.length === 0) {
    return {
      releasable: false,
      reason: "No new commits since last tag.",
      commitCount: 0,
    };
  }

  // Filter out release bot commits and skip ci markers
  const nonBotCommits = commits.filter(
    (c) => !c.includes("chore(release):") && !c.includes("[skip ci]"),
  );

  if (nonBotCommits.length === 0) {
    return {
      releasable: false,
      reason: "All commits are release/skip-ci markers.",
      commitCount: 0,
    };
  }

  return {
    releasable: true,
    reason: `${nonBotCommits.length} releasable commit(s) found.`,
    commitCount: nonBotCommits.length,
  };
}

if (import.meta.main) {
  const result = isReleasable();
  console.log(`Release check: ${result.reason}`);
  if (process.argv.includes("--github-output")) {
    const fs = await import("node:fs");
    const outputPath = process.env.GITHUB_OUTPUT;
    if (outputPath) {
      fs.appendFileSync(
        outputPath,
        `releasable=${result.releasable}\ncommit_count=${result.commitCount}\n`,
      );
    }
  }
  process.exit(result.releasable ? 0 : 0);
}
```

- [ ] **Step 3: Test `scripts/release-check.ts` locally**

Run: `bun run scripts/release-check.ts`
Expected: Reports `No new commits since last tag.` when HEAD is clean and at tag `v1.1.2`.

- [ ] **Step 4: Format and commit release check utility**

```bash
bun run format
git add .versionrc.json scripts/release-check.ts
git commit -m "feat(release): configure releaseCommitMessageFormat and commit check utility"
```

---

### Task 4: Autonomous Release & Changelog Workflow (`release.yml`)

**Files:**
- Modify: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: `workflow_run` completion event of `CI` workflow on `main`.
- Produces: Automated release commit, tag push to `main`, signed release APK, sha256 checksum, and GitHub Release.

- [ ] **Step 1: Implement autonomous two-stage release workflow in `.github/workflows/release.yml`**

Rewrite `.github/workflows/release.yml` with:
- Trigger: `workflow_run` on `CI` completed on `main`, plus `workflow_dispatch` with `bump_type` choice (`auto`, `patch`, `minor`, `major`).
- Guard: Skips if CI failed, if not push, or if commit is already `chore(release):` / `[skip ci]`.
- Job 1 (`release`): Evaluates commits, bumps version, updates changelog, formats, commits, tags, and pushes to `main`.
- Job 2 (`publish`): Checks out the release tag, builds release APK with Gradle cache, generates checksums, and publishes GitHub Release.

Exact workflow implementation:
```yaml
name: Release

on:
  workflow_run:
    workflows: [CI]
    types: [completed]
    branches: [main]
  workflow_dispatch:
    inputs:
      bump_type:
        description: "Semantic bump level (auto, patch, minor, major)"
        required: false
        default: "auto"
        type: choice
        options:
          - auto
          - patch
          - minor
          - major

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

permissions:
  contents: write

jobs:
  release:
    name: Generate Release & Changelog
    if: >-
      github.event_name == 'workflow_dispatch' ||
      (github.event.workflow_run.conclusion == 'success' &&
      github.event.workflow_run.event == 'push' &&
      !contains(github.event.workflow_run.head_commit.message, 'chore(release):') &&
      !contains(github.event.workflow_run.head_commit.message, '[skip ci]'))
    runs-on: ubuntu-latest
    timeout-minutes: 20
    outputs:
      should_publish: ${{ steps.bump.outputs.should_publish }}
      release_version: ${{ steps.bump.outputs.release_version }}
      release_tag: ${{ steps.bump.outputs.release_tag }}

    steps:
      - name: Checkout Code with Full History
        uses: actions/checkout@v7
        with:
          fetch-depth: 0
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Setup Bun
        uses: oven-sh/setup-bun@v2
        with:
          bun-version: latest

      - name: Cache Bun Dependencies
        uses: actions/cache@v6
        with:
          path: ~/.bun/install/cache
          key: ${{ runner.os }}-bun-${{ hashFiles('bun.lock') }}
          restore-keys: |
            ${{ runner.os }}-bun-

      - name: Install Dependencies
        run: bun install --frozen-lockfile

      - name: Configure Git Identity
        run: |
          git config user.name "Jinto AG"
          git config user.email "jinto-ag@users.noreply.github.com"

      - name: Evaluate Releasability and Bump Version
        id: bump
        run: |
          bun run scripts/release-check.ts --github-output

          # Determine bump flag
          BUMP_FLAG=""
          case "${{ github.event.inputs.bump_type }}" in
            major) BUMP_FLAG="--release-as major" ;;
            minor) BUMP_FLAG="--release-as minor" ;;
            patch) BUMP_FLAG="--release-as patch" ;;
            *) BUMP_FLAG="--noBumpWhenEmptyChanges" ;;
          esac

          PREV_VERSION=$(node -p "require('./package.json').version")
          echo "Previous version: $PREV_VERSION"

          # Execute release bump & changelog generation
          bunx commit-and-tag-version $BUMP_FLAG

          NEW_VERSION=$(node -p "require('./package.json').version")
          echo "Evaluated version: $NEW_VERSION"

          if [ "$PREV_VERSION" = "$NEW_VERSION" ] && [ "${{ github.event.inputs.bump_type }}" != "major" ] && [ "${{ github.event.inputs.bump_type }}" != "minor" ] && [ "${{ github.event.inputs.bump_type }}" != "patch" ]; then
            echo "ℹ️  No version changes to release."
            echo "should_publish=false" >> "$GITHUB_OUTPUT"
            exit 0
          fi

          TAG="v${NEW_VERSION}"

          # Interrogate remote to prevent race condition
          if git ls-remote --exit-code --tags origin "refs/tags/${TAG}" >/dev/null 2>&1; then
            echo "::warning::Tag ${TAG} already exists on origin. Skipping push."
            echo "should_publish=false" >> "$GITHUB_OUTPUT"
            exit 0
          fi

          # Push release commit and tag to main
          git push origin main "refs/tags/${TAG}"
          echo "🚀 Successfully pushed release commit and tag ${TAG}"

          echo "should_publish=true" >> "$GITHUB_OUTPUT"
          echo "release_version=${NEW_VERSION}" >> "$GITHUB_OUTPUT"
          echo "release_tag=${TAG}" >> "$GITHUB_OUTPUT"

  publish:
    name: Build & Publish Release Artifacts
    needs: release
    if: needs.release.outputs.should_publish == 'true'
    runs-on: ubuntu-latest
    timeout-minutes: 30
    permissions:
      contents: write

    steps:
      - name: Checkout the Release Tag
        uses: actions/checkout@v7
        with:
          ref: refs/tags/${{ needs.release.outputs.release_tag }}

      - name: Set up JDK 21
        uses: actions/setup-java@v6
        with:
          java-version: "21"
          distribution: "temurin"
          cache: "gradle"

      - name: Setup Android SDK Licenses
        run: |
          yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true

      - name: Setup Bun
        uses: oven-sh/setup-bun@v2
        with:
          bun-version: latest

      - name: Cache Bun Dependencies
        uses: actions/cache@v6
        with:
          path: ~/.bun/install/cache
          key: ${{ runner.os }}-bun-${{ hashFiles('bun.lock') }}
          restore-keys: |
            ${{ runner.os }}-bun-

      - name: Install Node Dependencies
        run: bun install --frozen-lockfile

      - name: Build Release APK
        run: |
          chmod +x gradlew build.sh
          bun run build

      - name: Generate Checksums
        run: |
          sha256sum DebugToggle-release.apk > DebugToggle-release.apk.sha256

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v3
        with:
          tag_name: ${{ needs.release.outputs.release_tag }}
          name: DebugToggle ${{ needs.release.outputs.release_tag }}
          files: |
            DebugToggle-release.apk
            DebugToggle-release.apk.sha256
          draft: false
          prerelease: false
          generate_release_notes: true
```

- [ ] **Step 2: Validate workflow syntax and format**

Run: `bun run lint:secrets && bun run format:check`
Expected: PASS with 0 errors.

- [ ] **Step 3: Commit release workflow**

```bash
git add .github/workflows/release.yml
git commit -m "ci(release): implement autonomous changelog, tagging, and publication pipeline"
```

---

### Task 5: Repository Ruleset Alignment with `~/dev/radiece`

**Files:**
- GitHub Repository Ruleset `25787521` (API configuration)

**Interfaces:**
- Consumes: GitHub Rulesets API for repository `jinto-ag/debug-toggle`.
- Produces: Protected `main` branch with `deletion`, `non_fast_forward`, and `required_linear_history`, permitting fast-forward linear release commits while protecting branch integrity.

- [ ] **Step 1: Check current ruleset parameters**

Run: `gh api repos/jinto-ag/debug-toggle/rulesets/25787521`
Verify existing configuration.

- [ ] **Step 2: Update ruleset `25787521` to align with `~/dev/radiece`**

Update ruleset `25787521` via GitHub API to ensure:
- `deletion` active
- `non_fast_forward` active
- `required_linear_history` active
- Human workflow uses Pull Requests (squash or rebase)
- Release bot fast-forward commits succeed without blocking

- [ ] **Step 3: Verify ruleset status via API**

Run: `gh api repos/jinto-ag/debug-toggle/rulesets/25787521`
Expected: Confirmed active with rules `deletion`, `non_fast_forward`, and `required_linear_history`.

---

### Task 6: End-to-End Verification and PR Integration

**Files:**
- All modified files across branch.

**Interfaces:**
- Consumes: Local test suite, JaCoCo coverage, formatters, and linters.
- Produces: Verified Pull Request merged into `main` and automated release execution.

- [ ] **Step 1: Run complete local verification suite**

Run:
```bash
bun run lint:secrets
bun run format:check
./gradlew testDebugUnitTest jacocoTestReport jacocoTestCoverageVerification
./build.sh
```
Expected: All checks pass; line coverage `>= 95%`; signed APK generated.

- [ ] **Step 2: Push branch and create Pull Request**

Create branch `feat/ci-caching-and-automated-release`, push to origin, and create PR:
```bash
gh pr create --title "feat(ci): add layered caching and autonomous release pipeline" --body "..."
```

- [ ] **Step 3: Wait for CI checks to pass and squash-merge PR**

Merge the PR via GitHub CLI:
```bash
gh pr merge --squash --delete-branch
```

- [ ] **Step 4: Monitor CI and autonomous Release workflow on `main`**

Watch `gh run list --limit 5` and confirm:
1. `CI` runs on `main` and succeeds (verifying cache saves and hits).
2. `Release` automatically runs on `workflow_run` success.
3. Version is bumped, changelog generated, release commit pushed with `[skip ci]`, tag created, and GitHub Release published.

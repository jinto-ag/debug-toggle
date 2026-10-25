import { execSync } from "node:child_process";
import * as fs from "node:fs";

export function getLatestTag(): string | null {
  try {
    return execSync("git describe --tags --abbrev=0", {
      encoding: "utf-8",
    }).trim();
  } catch {
    return null;
  }
}

export function getCommitsSinceTag(tag: string | null): string[] {
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

export function getModifiedFilesSinceTag(tag: string | null): string[] {
  try {
    const cmd = tag
      ? `git diff --name-only ${tag}..HEAD`
      : "git ls-tree -r --name-only HEAD";
    const output = execSync(cmd, {
      encoding: "utf-8",
    }).trim();
    if (!output) return [];
    return output
      .split("\n")
      .map((f) => f.trim())
      .filter(Boolean);
  } catch {
    return [];
  }
}

export function isAppRelatedFile(filePath: string): boolean {
  if (!filePath) return false;
  // App module source, resources, Gradle, and ProGuard
  if (filePath.startsWith("app/")) return true;
  // Root Gradle and build files directly configuring, building, or packaging the Android app
  if (filePath === "build.gradle.kts") return true;
  if (filePath === "settings.gradle.kts") return true;
  if (filePath === "gradle.properties") return true;
  if (filePath.startsWith("gradle/")) return true;
  if (filePath === "build.sh") return true;
  return false;
}

export function hasAppChanges(modifiedFiles: string[]): boolean {
  return modifiedFiles.some(isAppRelatedFile);
}

export function filterReleasableCommits(commits: string[]): string[] {
  return commits.filter(
    (c) => !c.includes("chore(release):") && !c.includes("[skip ci]"),
  );
}

export function isReleasableFrom(
  commits: string[],
  modifiedFiles: string[],
): {
  releasable: boolean;
  reason: string;
  commitCount: number;
  appFilesChanged: number;
} {
  if (commits.length === 0) {
    return {
      releasable: false,
      reason: "No new commits since last tag.",
      commitCount: 0,
      appFilesChanged: 0,
    };
  }

  const nonBotCommits = filterReleasableCommits(commits);

  if (nonBotCommits.length === 0) {
    return {
      releasable: false,
      reason: "All commits are release/skip-ci markers.",
      commitCount: 0,
      appFilesChanged: 0,
    };
  }

  const appFiles = modifiedFiles.filter(isAppRelatedFile);

  if (appFiles.length === 0) {
    return {
      releasable: false,
      reason:
        "No app changes detected since last release (modified files are non-app/infrastructure).",
      commitCount: nonBotCommits.length,
      appFilesChanged: 0,
    };
  }

  return {
    releasable: true,
    reason: `${nonBotCommits.length} releasable commit(s) and ${appFiles.length} app file change(s) found.`,
    commitCount: nonBotCommits.length,
    appFilesChanged: appFiles.length,
  };
}

export function isReleasableFromCommits(commits: string[]): {
  releasable: boolean;
  reason: string;
  commitCount: number;
} {
  if (commits.length === 0) {
    return {
      releasable: false,
      reason: "No new commits since last tag.",
      commitCount: 0,
    };
  }

  const nonBotCommits = filterReleasableCommits(commits);
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

export function isReleasable(): {
  releasable: boolean;
  reason: string;
  commitCount: number;
  appFilesChanged: number;
} {
  const tag = getLatestTag();
  const commits = getCommitsSinceTag(tag);
  const modifiedFiles = getModifiedFilesSinceTag(tag);
  return isReleasableFrom(commits, modifiedFiles);
}

if (import.meta.main) {
  const result = isReleasable();
  console.log(`Release check: ${result.reason}`);

  if (process.argv.includes("--github-output")) {
    const outputPath = process.env.GITHUB_OUTPUT;
    if (outputPath) {
      fs.appendFileSync(
        outputPath,
        `releasable=${result.releasable}\ncommit_count=${result.commitCount}\napp_files_changed=${result.appFilesChanged}\n`,
      );
    }
  }

  process.exit(0);
}

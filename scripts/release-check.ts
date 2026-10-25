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

export function filterReleasableCommits(commits: string[]): string[] {
  return commits.filter(
    (c) => !c.includes("chore(release):") && !c.includes("[skip ci]"),
  );
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
} {
  const tag = getLatestTag();
  const commits = getCommitsSinceTag(tag);
  return isReleasableFromCommits(commits);
}

if (import.meta.main) {
  const result = isReleasable();
  console.log(`Release check: ${result.reason}`);

  if (process.argv.includes("--github-output")) {
    const outputPath = process.env.GITHUB_OUTPUT;
    if (outputPath) {
      fs.appendFileSync(
        outputPath,
        `releasable=${result.releasable}\ncommit_count=${result.commitCount}\n`,
      );
    }
  }

  process.exit(0);
}

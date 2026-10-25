import { describe, expect, it } from "bun:test";
import {
  filterReleasableCommits,
  hasAppChanges,
  isAppRelatedFile,
  isReleasableFrom,
  isReleasableFromCommits,
} from "./release-check";

describe("release-check logic", () => {
  it("filters out release bot and skip-ci commits", () => {
    const commits = [
      "chore(release): v1.1.2 [skip ci]",
      "fix(ci): format changelog [skip ci]",
      "chore(release): 1.1.1",
    ];
    const releasable = filterReleasableCommits(commits);
    expect(releasable).toHaveLength(0);
  });

  it("identifies releasable conventional commits", () => {
    const commits = [
      "feat(app): add new toggle option",
      "fix(settings): fix root fallback",
      "chore(release): v1.1.0 [skip ci]",
    ];
    const releasable = filterReleasableCommits(commits);
    expect(releasable).toHaveLength(2);
    expect(releasable).toContain("feat(app): add new toggle option");
    expect(releasable).toContain("fix(settings): fix root fallback");
  });

  it("evaluates empty commit range as not releasable", () => {
    const result = isReleasableFromCommits([]);
    expect(result.releasable).toBe(false);
    expect(result.commitCount).toBe(0);
  });

  it("evaluates only bot commits as not releasable", () => {
    const result = isReleasableFromCommits([
      "chore(release): v1.1.2 [skip ci]",
    ]);
    expect(result.releasable).toBe(false);
    expect(result.commitCount).toBe(0);
  });

  it("correctly identifies app-related files", () => {
    expect(isAppRelatedFile("app/src/main/java/MainActivity.kt")).toBe(true);
    expect(isAppRelatedFile("app/src/main/res/values/strings.xml")).toBe(true);
    expect(isAppRelatedFile("app/build.gradle.kts")).toBe(true);
    expect(isAppRelatedFile("build.gradle.kts")).toBe(true);
    expect(isAppRelatedFile("settings.gradle.kts")).toBe(true);
    expect(isAppRelatedFile("gradle.properties")).toBe(true);
    expect(isAppRelatedFile("build.sh")).toBe(true);
    expect(isAppRelatedFile("gradle/wrapper/gradle-wrapper.properties")).toBe(
      true,
    );

    // Non-app files
    expect(isAppRelatedFile(".github/workflows/ci.yml")).toBe(false);
    expect(isAppRelatedFile(".github/workflows/release.yml")).toBe(false);
    expect(isAppRelatedFile("docs/README.md")).toBe(false);
    expect(isAppRelatedFile("README.md")).toBe(false);
    expect(isAppRelatedFile("package.json")).toBe(false);
    expect(isAppRelatedFile("nx.json")).toBe(false);
    expect(isAppRelatedFile("project.json")).toBe(false);
    expect(isAppRelatedFile("scripts/test-e2e.sh")).toBe(false);
    expect(isAppRelatedFile("scripts/release-check.ts")).toBe(false);
  });

  it("detects whether a file list has app changes", () => {
    const ciOnly = [
      ".github/workflows/ci.yml",
      "nx.json",
      "scripts/release-check.ts",
    ];
    expect(hasAppChanges(ciOnly)).toBe(false);

    const withApp = [
      ".github/workflows/ci.yml",
      "app/src/main/java/MainActivity.kt",
    ];
    expect(hasAppChanges(withApp)).toBe(true);

    const withGradle = ["build.gradle.kts", "README.md"];
    expect(hasAppChanges(withGradle)).toBe(true);
  });

  it("evaluates non-app changes as not releasable even with feat/fix commit messages", () => {
    const commits = [
      "feat(ci): add layered caching and release pipeline",
      "fix(ci): format changelog",
    ];
    const modifiedFiles = [
      ".github/workflows/ci.yml",
      ".github/workflows/release.yml",
      "nx.json",
      "project.json",
    ];

    const result = isReleasableFrom(commits, modifiedFiles);
    expect(result.releasable).toBe(false);
    expect(result.commitCount).toBe(2);
    expect(result.appFilesChanged).toBe(0);
    expect(result.reason).toContain("No app changes detected");
  });

  it("evaluates app changes with valid conventional commits as releasable", () => {
    const commits = [
      "fix(app): fix permission dialog",
      "chore(release): v1.1.2 [skip ci]",
    ];
    const modifiedFiles = [
      "app/src/main/java/MainActivity.kt",
      "app/src/main/res/values/strings.xml",
    ];

    const result = isReleasableFrom(commits, modifiedFiles);
    expect(result.releasable).toBe(true);
    expect(result.commitCount).toBe(1);
    expect(result.appFilesChanged).toBe(2);
  });
});

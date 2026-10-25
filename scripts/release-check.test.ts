import { describe, expect, it } from "bun:test";
import {
  filterReleasableCommits,
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

  it("evaluates non-bot commits as releasable", () => {
    const result = isReleasableFromCommits([
      "fix(app): fix permission dialog",
      "chore(release): v1.1.2 [skip ci]",
    ]);
    expect(result.releasable).toBe(true);
    expect(result.commitCount).toBe(1);
  });
});

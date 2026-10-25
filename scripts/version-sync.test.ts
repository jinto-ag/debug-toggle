import { describe, expect, it } from "bun:test";
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..");

describe("Version Synchronization Verification", () => {
  it("ensures package.json version matches app/build.gradle.kts versionName and versionCode", () => {
    const pkgPath = path.join(rootDir, "package.json");
    const gradlePath = path.join(rootDir, "app/build.gradle.kts");

    expect(fs.existsSync(pkgPath)).toBe(true);
    expect(fs.existsSync(gradlePath)).toBe(true);

    const pkg = JSON.parse(fs.readFileSync(pkgPath, "utf-8"));
    const pkgVersion: string = pkg.version;
    expect(pkgVersion).toBeDefined();

    const parts = pkgVersion.split(".").map(Number);
    const expectedMajor = Number.isFinite(parts[0]) ? parts[0] : 1;
    const expectedMinor = Number.isFinite(parts[1]) ? parts[1] : 0;
    const expectedPatch = Number.isFinite(parts[2]) ? parts[2] : 0;
    const expectedVersionCode =
      expectedMajor * 10000 + expectedMinor * 100 + expectedPatch;

    const gradleContent = fs.readFileSync(gradlePath, "utf-8");
    const versionNameMatch = gradleContent.match(/versionName\s*=\s*"([^"]+)"/);
    const versionCodeMatch = gradleContent.match(/versionCode\s*=\s*(\d+)/);

    expect(versionNameMatch).not.toBeNull();
    expect(versionCodeMatch).not.toBeNull();

    const actualVersionName = versionNameMatch![1];
    const actualVersionCode = Number(versionCodeMatch![1]);

    expect(actualVersionName).toBe(pkgVersion);
    expect(actualVersionCode).toBe(expectedVersionCode);
  });
});

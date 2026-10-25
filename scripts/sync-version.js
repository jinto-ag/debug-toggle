import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..");

const pkg = JSON.parse(
  fs.readFileSync(path.join(rootDir, "package.json"), "utf8"),
);
const newVersion = pkg.version;

// Parse semver parts: major.minor.patch
const parts = newVersion.split(".").map(Number);
const major = parts[0] || 1;
const minor = parts[1] || 0;
const patch = parts[2] || 0;
// Semantic versionCode: major * 10000 + minor * 100 + patch
const newVersionCode = major * 10000 + minor * 100 + patch;

console.log(`Syncing version: ${newVersion} (versionCode: ${newVersionCode})`);

// 1. Update app/build.gradle.kts
const gradlePath = path.join(rootDir, "app/build.gradle.kts");
if (fs.existsSync(gradlePath)) {
  let content = fs.readFileSync(gradlePath, "utf8");
  content = content.replace(
    /versionCode = \d+/,
    `versionCode = ${newVersionCode}`,
  );
  content = content.replace(
    /versionName = "[^"]+"/,
    `versionName = "${newVersion}"`,
  );
  fs.writeFileSync(gradlePath, content, "utf8");
  console.log(`Updated ${gradlePath}`);
}

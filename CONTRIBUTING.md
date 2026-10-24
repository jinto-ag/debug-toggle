# Contributing to DebugToggle

Contributions to DebugToggle are welcome.

## Development Setup

Requirements:
- Android SDK (API 34+, Build-Tools 35.0.0)
- JDK 17 or JDK 21
- [Bun](https://bun.sh) (v1.2+)

```bash
git clone https://github.com/jinto-ag/debug-toggle.git
cd debug-toggle
bun install
```

## Running Tasks

All development workflows are managed via Nx:

```bash
bun run build         # Build release APK (< 50 KB)
bun run test          # Run JVM unit tests
bun run test:e2e      # Run E2E tests on connected device or headless emulator
bun run lint:secrets  # Scan for accidental credentials
bun run format:check  # Check formatting via Biome
bun run format        # Auto-format files
```

## Pull Request Guidelines

1. Create a descriptive branch: `feat/feature-name` or `fix/issue-description`.
2. Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` for new capabilities
   - `fix:` for bug fixes
   - `test:` for test additions
   - `docs:` for documentation updates
   - `chore:` for maintenance
3. Do not include AI co-author attributions (`Co-authored-by:`) in commit messages.
4. Verify that `bun run test` and `bun run lint:secrets` pass before submitting.

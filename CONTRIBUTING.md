# Contributing to V3SP3R

Thanks for your interest in contributing! Vesper is an open-source project and we welcome contributions of all kinds — bug fixes, new features, documentation, and more.

## Getting Started

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/V3SP3R.git
   cd V3SP3R
   ```
3. **Create a branch** for your work:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. **Open in Android Studio** and let Gradle sync
5. **Build and test** your changes

### Requirements

- Android Studio (latest stable recommended)
- JDK 17+
- Android SDK with API 26+ (Android 8.0)
- A Flipper Zero device (for testing hardware features)

## Development Guidelines

### Code Style

- Follow standard [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions focused — one function, one responsibility
- Use Jetpack Compose best practices for UI code

### Architecture

Vesper follows a layered architecture:

- **UI Layer** — Jetpack Compose screens + ViewModels (in `ui/`)
- **Domain Layer** — Business logic, command execution, risk assessment (in `domain/`)
- **Data Layer** — Persistence, API clients, BLE communication (in `data/`, `ai/`, `ble/`)

When adding features, place code in the appropriate layer. If unsure, look at how existing features are structured.

### Security

Security is a core concern for Vesper. Please:

- **Never** commit API keys, secrets, or credentials
- **Always** validate and sanitize external input (LLM responses, BLE data, user input)
- **Respect** the risk classification system — new actions must have appropriate risk levels
- **Test** edge cases, especially around JSON parsing and BLE communication
- Run inputs through the existing `InputValidator` and `InputSanitizer` utilities

### Commit Messages

Write clear, descriptive commit messages:

```
Add SubGHz frequency validation to signal editor

Validates that user-entered frequencies fall within supported SubGHz
bands before attempting transmission. Shows an inline error for
out-of-range values.
```

- Use the imperative mood ("Add", not "Added" or "Adds")
- First line: concise summary (50 chars or less ideal, 72 max)
- Body: explain *what* and *why*, not *how*

## What to Contribute

### Areas That Need Help

- **iOS version** — SwiftUI port of the Android app
- **Signal format parsers** — Support for new RF/IR protocols
- **Payload templates** — BadUSB scripts, SubGHz signals, IR remotes, NFC tags
- **UI/UX improvements** — Animations, accessibility, responsive layouts
- **Translations / i18n** — Localization for non-English languages
- **Test coverage** — Unit tests, integration tests, UI tests
- **Documentation** — Guides, tutorials, API docs
- **Knowledge corpus** — New topic docs and cross-links under `knowledge/` (see below)

### Knowledge corpus contributions

The `knowledge/` directory is a **knowledge-focused corpus** served
over MCP by `mcp-server/`. It's browsable end-to-end from
[`knowledge/MANIFEST.md`](knowledge/MANIFEST.md). Each subdirectory
under `knowledge/` is a **topic**; each `.md` file inside is a
**name** under that topic. If you're adding or editing a page, follow
these rules — the MCP server relies on them:

- **Directory = topic; filename stem = name.** `list_topics` returns
  `{"subghz": ["README", "protocols", "sub-format"], ...}`;
  `read_doc("subghz", "protocols")` reads
  `knowledge/subghz/protocols.md`. Renaming a file breaks callers.
- **Kebab-case filenames, `.md` extension, no numeric prefixes.**
  Filenames are addressable.
- **One idea per file, ~150-400 lines.** Long omnibus files are
  hostile to `read_doc` — clients read files whole.
- **Follow the doc template** described in
  [`plan-knowledge-expand.md`](plan-knowledge-expand.md) §4: `What it
  is`, `How it works`, `Capabilities and limits`, `Common tasks`,
  `Gotchas`, `Legal & safety notes`, `See also`, and an Attribution
  footer. Index / reference docs can deviate — say so at the top.
- **Cross-link liberally.** Every topic doc ends with a `See also:`
  section of at least two related pages. `search_docs` is
  substring-only, so discoverability rides on explicit references.
- **Cite sources.** Frequencies, chip part numbers, protocol names —
  attribute in the footer with a retrieval date (`2025-Q3`) so the
  claim can be re-verified.
- **Capability ≠ legality.** Any doc that touches emit / transmit /
  spoof / clone gets a Legal & safety notes block linking
  [`knowledge/legal/README.md`](knowledge/legal/README.md).
- **Update [`knowledge/MANIFEST.md`](knowledge/MANIFEST.md).** New
  topics belong under the appropriate section.

If your topic is a methodology playbook that already exists under
`app/src/main/assets/skills/<name>/SKILL.md`, edit the source there —
**do not** hand-edit `knowledge/skills/<name>.md`. Then run:

```bash
python mcp-server/scripts/sync_skills.py
```

CI (`.github/workflows/mcp-python.yml`) runs
`sync_skills.py --check` before lint and fails on drift.

### Good First Issues

Look for issues labeled [`good first issue`](../../labels/good%20first%20issue) — these are scoped, well-documented tasks ideal for new contributors.

## Submitting Changes

1. **Ensure your code builds** without errors or warnings
2. **Test** your changes on a real device if possible
3. **Push** your branch to your fork
4. **Open a Pull Request** against the `main` branch
5. **Fill out** the PR template completely
6. **Respond** to review feedback promptly

### Pull Request Guidelines

- Keep PRs focused — one feature or fix per PR
- Include a clear description of what changed and why
- Reference related issues (e.g., "Closes #42")
- Add screenshots or recordings for UI changes
- Ensure no secrets or credentials are included

## Reporting Bugs

Use the [Bug Report](../../issues/new?template=bug_report.md) issue template. Include:

- Steps to reproduce
- Expected vs actual behavior
- Device info (Android version, Flipper firmware version)
- Logs or screenshots if available

## Requesting Features

Use the [Feature Request](../../issues/new?template=feature_request.md) issue template. Describe:

- The problem you're trying to solve
- Your proposed solution
- Any alternatives you've considered

## Code of Conduct

Be respectful. We're all here to build something useful. Harassment, trolling, and unconstructive behavior won't be tolerated.

## License

By contributing, you agree that your contributions will be licensed under the [GPL-3.0 License](LICENSE).

---

Questions? Open a [Discussion](../../discussions) or reach out in an issue. We're happy to help you get started.

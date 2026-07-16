<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# VariantX Changelog

## [Unreleased]

## [0.3.0] - 2026-07-16

### Added

- Gradle online/offline mode toggle in the dialog's action row

### Changed

- Dialog now seeds from the variant Android Studio actually has selected, instead of a stale persisted selection
- Build / Install no longer triggers a redundant Gradle sync when the target variant is already selected

## [0.2.0]

### Added

- Fix duplicates in the variant selector dialog

## [0.1.0]

### Added

- "Build" action: assembles the selected variant without launching the app
- Dialog keyboard shortcuts: press `R` / `B` / `S` to trigger Run / Build / Sync directly
- Fix bug where UI wouldn't reflect immediately on pin a variant

## [0.0.1]

### Added

- Keyboard shortcut `Cmd+Shift+X` / `Ctrl+Shift+X` to open variant selector dialog
- Automatic detection of Android app modules
- Flavor dimension and product flavor matrix with segmented controls
- Build type selection with segmented control
- Live variant name preview
- "Set" action to apply variant to all modules
- "Run" action to apply variant and launch the app
- Pin/unpin favorite variant combinations for quick recall
- Per-project state persistence (last used selection + favorites)

[Unreleased]: https://github.com/AxonDragonScale/VariantX/compare/0.3.0...HEAD
[0.3.0]: https://github.com/AxonDragonScale/VariantX/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/AxonDragonScale/VariantX/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/AxonDragonScale/VariantX/compare/0.0.1...0.1.0
[0.0.1]: https://github.com/AxonDragonScale/VariantX/commits/0.0.1

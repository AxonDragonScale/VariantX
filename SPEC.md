# VariantX — Plugin Specification

## 1. Overview

**VariantX** is an IntelliJ Platform plugin (targeting Android Studio) that provides a fast, keyboard-shortcut-driven UI for selecting and applying Android build variant combinations (product flavors + build type) across all modules in a project. It replaces the friction of navigating the Build Variants tool window by offering a single popup dialog invoked via `Cmd+Shift+X` (macOS) / `Ctrl+Shift+X` (Windows/Linux).

### 1.1 Problem Statement

Switching build variants in Android Studio today requires:
1. Opening the Build Variants tool window
2. Scrolling through a table of all modules
3. Clicking each module's variant dropdown individually
4. Waiting for Gradle sync after each change

For projects with multiple flavor dimensions (e.g., `environment` × `tier` × `api`), this is tedious and error-prone. VariantX collapses this into a single dialog with structured dimension/flavor pickers and one-click apply.

### 1.2 Target Users

- Android developers working on multi-module, multi-flavor projects
- Teams with complex build matrix configurations (2+ flavor dimensions)

---

## 2. Features

| #  | Feature                   | Description                                                                                                     |
|----|---------------------------|-----------------------------------------------------------------------------------------------------------------|
| F1 | Keyboard Shortcut         | `Cmd+Shift+X` (macOS) / `Ctrl+Shift+X` (Win/Linux) opens the dialog                                           |
| F2 | Module Detection          | Detects Android **app** modules (excludes library modules). If multiple app modules exist, shows a module picker |
| F3 | Flavor/Dimension Matrix   | Reads `flavorDimensions` and `productFlavors` from the Gradle Android model and displays a dimension → flavors mapping |
| F4 | Flavor Selection          | User picks one flavor per dimension via **segmented control** (toggle button group)                             |
| F5 | Build Type Selection      | User picks a build type (debug, release, or custom build types) via **segmented control**                       |
| F6 | "Set" Action              | Applies the selected variant combination to all modules using `BuildVariantUpdater`                             |
| F7 | "Build" Action            | Sets the variant (same as F6), then triggers a Gradle `assemble` task for the selected app module (no install/run) |
| F8 | "Run" Action              | Sets the variant (same as F6), then triggers a Gradle `install` + run for the selected app module               |
| F9 | Remember Last Used        | Persists the last selection per project using `PersistentStateComponent`; pre-populates on next dialog open      |
| F10 | Favorites / Pinning      | User can pin frequently used variant combinations for instant recall; pinned combos shown at the top of the dialog |

---

## 3. UI / UX Design

### 3.1 Dialog Layout

The dialog uses Kotlin UI DSL v2 and extends `DialogWrapper`. Flavor and build type selections use **segmented controls** (toggle button groups) instead of dropdowns — this gives a full at-a-glance view of all options and requires a single click to switch. Layout:

```
┌──────────────────────── VariantX ─────────────────────────┐
│                                                            │
│  ★ Favorites ──────────────────────────────────────────── │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ ▸ stagingFreeDebug    [ Set ] [ 🔨 Build ] [ ▶ Run ] │ │
│  │ ▸ productionPremiumRelease [ Set ] [ 🔨 Build ] [ ▶ Run ] │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  Module:       [ app ▾ ]                                   │  ← Dropdown, hidden if only 1
│                                                            │
│  ── Flavors ─────────────────────────────────────────────  │
│  environment:  [ staging | production | dev ]              │  ← Segmented control
│  tier:         [  free   |  premium  ]                     │  ← Segmented control
│  api:          [ minApi21 | minApi26 ]                     │  ← Segmented control
│                                                            │
│  ── Build Type ──────────────────────────────────────────  │
│               [  debug  |  release  ]                      │  ← Segmented control
│                                                            │
│  Variant:      stagingFreeMinApi21Debug                    │  ← Live preview
│                                                            │
│  [ ☆ Pin ]              [ Set ]  [ 🔨 Build ]  [ ▶ Run ]  │  ← Pin on left, no Cancel
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**Footer layout notes:**
- `[ ☆ Pin ]` is placed on the **far left** via `DialogWrapper.createLeftSideActions()`, visually separated from the action buttons
- `[ Set ]`, `[ 🔨 Build ]`, `[ ▶ Run ]` are on the **right** via `DialogWrapper.createActions()`
- Icons are placed **to the left of the text** on Build and Run buttons (using `Action.SMALL_ICON`)
- There is **no Cancel button**; the dialog is dismissed by pressing `Escape`

### 3.2 Segmented Controls

Each flavor dimension and the build type selector use IntelliJ UI DSL v2's native **`segmentedButton`** component (`com.intellij.ui.dsl.builder.Row.segmentedButton`). This provides automatic IntelliJ look-and-feel consistency, correct light/dark theme colors, focus ring rendering, and built-in keyboard navigation — with zero custom painting.

**Implementation pattern:**

```kotlin
panel {
    row {
        segmentedButton(flavors) { text = it }
            .bind(selectedFlavorProperty)
    }
}.apply { isOpaque = false; border = JBUI.Borders.empty() }
```

Each `SegmentedControl` wrapper creates a fresh `PropertyGraph` and `ObservableMutableProperty<String>` per rebuild, binds it to the native `SegmentedButton<String>`, and notifies the dialog via an `afterChange` listener.

**Behavior:**
- Exactly one segment is selected per row at all times
- Selected segment renders with the platform's standard selected-button style (automatically respects IntelliJ theme — no hardcoded colors)
- Clicking a segment immediately updates the variant preview
- If a dimension has many flavors (>5), the row wraps naturally based on available width
- Arrow keys (←/→) navigate between segments in the focused row (built-in)

### 3.3 Favorites / Pinning

The **Favorites** section appears at the top of the dialog when there are pinned variant combinations.

**Behavior:**
- Each favorite is a row showing the composed variant name (e.g., `stagingFreeDebug`) with inline **Set**, **🔨 Build**, and **▶ Run** buttons (icons left of text)
- Clicking a favorite's **Set** applies it immediately and closes the dialog
- Clicking a favorite's **🔨 Build** applies the variant and assembles without running
- Clicking a favorite's **▶ Run** applies and runs immediately
- The **☆ Pin** button in the dialog footer saves the current segmented control selection as a new favorite
- If the current selection is already pinned, the button shows **★ Unpin** and removes it on click
- Favorites are persisted per-project alongside other state
- Each favorite stores: module name, flavor selections (dimension → flavor map), and build type
- Favorites can be reordered via drag-and-drop (stretch goal) or are shown in most-recently-pinned-first order
- Right-clicking a favorite shows a context menu with "Remove" option
- Maximum of 10 favorites per project (to keep the dialog compact)

### 3.4 Behavioral Rules

| Condition                                | Behavior                                                                                |
|------------------------------------------|-----------------------------------------------------------------------------------------|
| No flavor dimensions exist               | Flavors section is hidden entirely; variant = build type only (e.g., `debug`)           |
| Single app module                        | Module dropdown row is hidden; the only app module is auto-selected                     |
| Single flavor in a dimension             | Segmented control still shown (single button, selected), for structural clarity         |
| Module selection changes                 | Segmented controls rebuild for the new module's dimensions/flavors/build types          |
| Any segment selection changes            | Variant preview label updates immediately; Pin/Unpin state re-evaluated                 |
| Composed variant name is invalid         | "Set", "Build", and "Run" buttons are disabled; variant label shows "⚠ Invalid combination" |
| Gradle sync in progress                  | Warning label shown; "Set", "Build", and "Run" buttons disabled                             |
| Persisted state is stale                 | Falls back to defaults (first flavor per dimension, "debug" build type)                 |
| Favorite references stale flavors        | Favorite is shown with a warning icon; Set/Build/Run for that favorite are disabled     |
| No favorites exist                       | Favorites section is hidden entirely                                                    |

### 3.5 Keyboard Interaction

- `Enter` activates the "Run" button (primary action)
- `Alt+S` / `Option+S` activates "Set" (mnemonic)
- `Alt+B` / `Option+B` activates "Build" (mnemonic)
- `Alt+P` / `Option+P` toggles Pin/Unpin for current selection
- `Escape` closes the dialog (no Cancel button; handled by `DialogWrapper.doCancelAction()`)
- `←` / `→` navigates between segments in the focused segmented control row
- `↑` / `↓` moves focus between dimension rows

### 3.6 Notifications

| Event                                   | Notification Type | Message                                                |
|-----------------------------------------|-------------------|--------------------------------------------------------|
| No Android modules found                | Warning balloon   | "No Android app modules found. Is the project synced?" |
| Variant successfully set                | Info balloon      | "Build variant set to `stagingFreeDebug`"              |
| Gradle sync in progress on dialog open  | Warning balloon   | "Gradle sync is in progress. Please wait."             |
| Build triggered                         | Info balloon      | "Assembling `app` with variant `stagingFreeDebug`"     |
| Run triggered                           | Info balloon      | "Running `app` with variant `stagingFreeDebug`"        |

---

## 4. Technical Architecture

### 4.1 Plugin Dependencies

| Dependency                    | Type    | Purpose                                                        |
|-------------------------------|---------|----------------------------------------------------------------|
| `com.intellij.modules.platform` | Bundled | Core platform APIs (already declared)                          |
| `org.jetbrains.android`       | Bundled | Android facet, Gradle Android model, BuildVariantUpdater       |
| `com.intellij.gradle`         | Bundled | Gradle project model, sync state awareness                    |

### 4.2 Key IntelliJ / Android Studio APIs

| Purpose                        | API / Class                                                                              | Package                                                    |
|--------------------------------|------------------------------------------------------------------------------------------|------------------------------------------------------------|
| Find Android modules           | `ModuleManager.getInstance(project).modules` + `AndroidFacet.getInstance(module)`       | `com.intellij.openapi.module` / `org.jetbrains.android.facet` |
| Module display name & path     | `name` = last segment of the derived Gradle path (e.g. `"login"` from `":feature:login"`); `gradlePath` derived from `ExternalSystemApiUtil.getExternalProjectPath(module)` relative to project root | `com.intellij.openapi.externalSystem.util` |
| Check if app module            | `AndroidModel.getProjectType()` == `IdeAndroidProjectType.PROJECT_TYPE_APP`              | `com.android.tools.idea.model`                             |
| Read flavors & dimensions      | `GradleAndroidModel.get(module)?.androidProject?.multiVariantData`                       | `com.android.tools.idea.gradle.project.model`              |
| Read build types               | `multiVariantData.buildTypes`                                                            | same                                                       |
| Read available variants        | `GradleAndroidModel.get(module)?.allVariantNames`                                        | same                                                       |
| Read current variant           | `GradleAndroidModel.get(module)?.selectedVariantName`                                    | same                                                       |
| Set build variant              | `BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, moduleName, variantName)` | `com.android.tools.idea.gradle.variant.view`  |
| Trigger Build (assemble)       | `ExternalSystemUtil.runTask(...)` with task `:module:assemble{Variant}`                  | `com.intellij.openapi.externalSystem.util`                 |
| Trigger Run                    | `ProgramRunnerUtil.executeConfiguration(settings, executor)`                             | `com.intellij.execution`                                   |
| Run configurations             | `RunManager.getInstance(project)` + `AndroidRunConfigurationType`                        | `com.intellij.execution` / `com.android.tools.idea.run`    |
| Custom dialog                  | Extend `DialogWrapper`, override `createCenterPanel()`, `createActions()`, `createLeftSideActions()` | `com.intellij.openapi.ui`                        |
| UI building                    | Kotlin UI DSL v2: `panel { row { segmentedButton(items) { text = it }.bind(property) } }` | `com.intellij.ui.dsl.builder`                             |
| Action + shortcut              | `AnAction` subclass registered in `plugin.xml` `<actions>` with `<keyboard-shortcut>`    | `com.intellij.openapi.actionSystem`                        |
| Persistent state               | `PersistentStateComponent<T>` with `@State`, `@Storage` annotations                     | `com.intellij.openapi.components`                          |
| Notifications                  | `NotificationGroupManager.getInstance().getNotificationGroup("VariantX")`                | `com.intellij.notification`                                |
| Gradle sync check              | `GradleSyncState.getInstance(project).isSyncInProgress`                                  | `com.android.tools.idea.gradle.project.sync`               |

### 4.3 Data Models

```kotlin
/**
 * Represents a detected Android module with its variant configuration.
 */
data class AndroidModuleInfo(
    val name: String,                               // display name = last segment of Gradle path (e.g. "login" from ":feature:login")
    val gradlePath: String,                         // full Gradle project path, e.g. ":app" or ":feature:login"
    val isAppModule: Boolean,
    val flavorDimensions: List<String>,                 // ordered as declared in build.gradle
    val flavorsPerDimension: Map<String, List<String>>, // dimension name → list of flavor names
    val buildTypes: List<String>,                       // e.g., ["debug", "release"]
    val availableVariants: Set<String>,                 // all valid variant name strings
    val currentVariant: String?                         // currently selected variant, if any
)

/**
 * Represents the user's selection in the VariantX dialog.
 */
data class VariantSelection(
    var selectedModuleGradlePath: String = "",
    var flavorSelections: MutableMap<String, String> = mutableMapOf(), // dimension → chosen flavor
    var selectedBuildType: String = "debug"
) {
    /**
     * Composes the variant name by concatenating flavors (in dimension order)
     * and the build type in camelCase.
     *
     * Example: dimensions=["environment","tier"], flavors={"environment":"staging","tier":"free"},
     *          buildType="debug" → "stagingFreeDebug"
     */
    fun composeVariantName(dimensionOrder: List<String>): String {
        val flavorPart = dimensionOrder.mapIndexed { index, dim ->
            val flavor = flavorSelections[dim] ?: ""
            if (index == 0) flavor.lowercase()
            else flavor.replaceFirstChar { it.uppercase() }
        }.joinToString("")

        val buildTypePart = selectedBuildType.replaceFirstChar { it.uppercase() }
        return if (flavorPart.isEmpty()) selectedBuildType
               else "$flavorPart$buildTypePart"
    }
}

/**
 * Represents a pinned/favorite variant combination.
 */
data class FavoriteVariant(
    val moduleGradlePath: String,
    val flavorSelections: Map<String, String>,          // dimension → chosen flavor (immutable snapshot)
    val buildType: String,
    val variantName: String,                            // pre-composed variant name for display
    val pinnedAt: Long = System.currentTimeMillis()     // timestamp for ordering (most recent first)
)
```

### 4.4 State Persistence

Persisted per-project in `.idea/variantx.xml`:

```xml
<component name="VariantXState">
  <option name="lastModule" value=":app" />
  <option name="lastFlavors">
    <map>
      <entry key="environment" value="staging" />
      <entry key="tier" value="free" />
    </map>
  </option>
  <option name="lastBuildType" value="debug" />
  <option name="favorites">
    <list>
      <FavoriteVariant>
        <option name="moduleGradlePath" value=":app" />
        <option name="flavorSelections">
          <map>
            <entry key="environment" value="staging" />
            <entry key="tier" value="free" />
          </map>
        </option>
        <option name="buildType" value="debug" />
        <option name="variantName" value="stagingFreeDebug" />
        <option name="pinnedAt" value="1710000000000" />
      </FavoriteVariant>
    </list>
  </option>
</component>
```

State is saved on every "Set", "Run", or "Pin" action. On dialog open, the saved state is loaded and validated against the current module info. If any saved dimension/flavor no longer exists, that selection falls back to the first available flavor for that dimension. Stale favorites are shown with a warning icon.

### 4.5 Package / File Structure

```
src/main/kotlin/com/github/axondragonscale/variantx/
├── VariantXBundle.kt                      — String resource bundle (renamed from MyBundle)
├── action/
│   └── ShowVariantXAction.kt              — AnAction triggered by Cmd+Shift+X
├── model/
│   ├── AndroidModuleInfo.kt               — Data class for module variant info
│   ├── FavoriteVariant.kt                 — Data class for pinned variant combinations
│   └── VariantSelection.kt               — Data class for user selection + variant name composition
├── service/
│   ├── ModuleDetectionService.kt          — Finds Android app/library modules, reads variant model
│   ├── VariantApplierService.kt           — Sets variant via BuildVariantUpdater on all modules
│   └── AppRunnerService.kt                — Triggers assemble (Build) and install + run (Run) for a given module
├── state/
│   └── VariantXStateService.kt            — PersistentStateComponent for last selection + favorites
└── ui/
    ├── VariantXDialog.kt                  — DialogWrapper with full UI (Kotlin UI DSL v2)
    ├── SegmentedControl.kt                — Reusable segmented button group component
    └── FavoritesPanel.kt                  — Favorites list panel with inline Set/Run actions
```

### 4.6 plugin.xml Registrations

```xml
<idea-plugin>
    <id>com.github.axondragonscale.variantx</id>
    <name>VariantX</name>
    <vendor>axondragonscale</vendor>

    <depends>com.intellij.modules.platform</depends>
    <depends>org.jetbrains.android</depends>
    <depends>com.intellij.gradle</depends>

    <resource-bundle>messages.VariantXBundle</resource-bundle>

    <extensions defaultExtensionNs="com.intellij">
        <notificationGroup id="VariantX" displayType="BALLOON"/>
        <projectService serviceImplementation="com.github.axondragonscale.variantx.state.VariantXStateService"/>
    </extensions>

    <actions>
        <action id="VariantX.Show"
                class="com.github.axondragonscale.variantx.action.ShowVariantXAction"
                text="VariantX: Select Variant"
                description="Open VariantX variant selector dialog">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift X"/>
            <keyboard-shortcut keymap="Mac OS X" first-keystroke="meta shift X"/>
            <keyboard-shortcut keymap="Mac OS X 10.5+" first-keystroke="meta shift X"/>
        </action>
    </actions>
</idea-plugin>
```

---

## 5. Edge Cases & Error Handling

| #  | Edge Case                                     | Handling                                                                                         |
|----|-----------------------------------------------|--------------------------------------------------------------------------------------------------|
| E1 | Project has no Android modules                | Show warning notification; dialog does not open                                                  |
| E2 | Project not synced (GradleAndroidModel is null)| Show notification: "Project not synced. Please sync first."                                      |
| E3 | Gradle sync in progress                       | Dialog opens but shows warning; Set/Build/Run buttons disabled; re-enabled when sync finishes          |
| E4 | No flavor dimensions (simple project)         | Flavors section hidden; variant = build type only                                                |
| E5 | Variant combination filtered by `variantFilter`| Composed name not in `availableVariants` → buttons disabled, warning shown                       |
| E6 | Persisted state references deleted dimension  | Fall back to first flavor of remaining dimensions                                                |
| E7 | Persisted state references deleted module      | Fall back to first available app module                                                          |
| E8 | No run configuration for selected module       | `AppRunnerService` creates a default `AndroidRunConfiguration` automatically                     |
| E9 | No connected device for "Run"                  | Delegated to standard Android run pipeline (device chooser dialog appears)                       |
| E10| BuildVariantUpdater triggers a Gradle sync     | "Run" action waits for sync completion before triggering execution via `GradleSyncState` listener|
| E11| Multiple app modules selected for "Run"        | "Run" and "Build" only act on the single selected module; "Set" applies variant to all modules   |
| E12| Favorite references stale/deleted flavors      | Favorite row shown with ⚠ warning icon; its Set/Build/Run buttons are disabled                  |
| E13| Max favorites (10) reached                     | "Pin" button disabled; tooltip explains limit; user must unpin one first                         |
| E14| Duplicate favorite pinned                      | Pin button shows "★ Unpin" if current selection matches an existing favorite; no duplicates      |

---

## 6. Non-Functional Requirements

| Requirement       | Detail                                                                                 |
|-------------------|----------------------------------------------------------------------------------------|
| Compatibility     | Android Studio 2025.1+ (Narwhal, build 251+), IntelliJ IDEA with Android plugin   |
| Performance       | Dialog should open in < 200ms; module/variant data should be cached per sync cycle     |
| Memory            | Minimal footprint; no background processes when dialog is not open                     |
| Stability         | All Android-plugin API calls wrapped in try/catch; graceful degradation on API changes |
| UI consistency    | Uses IntelliJ UI DSL v2, standard `DialogWrapper`, platform look-and-feel              |

---

## 7. Future Enhancements (Out of Scope for v1)

- **Toolbar widget**: Status bar widget showing current variant, clickable to open dialog
- **Build Type in Run**: Support "Debug" (attach debugger) in addition to "Run"
- **ABI / Device filters**: Support filtering variants by connected device ABI
- **Bulk operations**: Set different variants per module in one dialog
- **Favorite reordering**: Drag-and-drop reordering of pinned favorites
- **Export/Import favorites**: Share favorite variant combinations across team members

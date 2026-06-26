# Offline evaluation (baseline) for `LogDetector`

This repo supports a simple **Android instrumentation** baseline runner that executes `LogDetector.detectAnalysis(...)`, writes a CSV timing/count summary, and is safe to skip if fixtures are absent.

## Where

- Runner: `app/src/androidTest/java/com/algerianwood/app/eval/LogDetectorBaselineTest.kt`

## Fixtures (assets)

Preferred layout:

```
app/src/androidTest/assets/evaluation_images/sample_001.jpg
```

Optional manifest (overrides asset directory listing):

```
app/src/androidTest/assets/evaluation_manifest.txt
```

`evaluation_manifest.txt` is newline-separated filenames only, for example:

```
pile_001.jpg
pile_002.jpg
```

## How to run

From Android Studio: run instrumentation test class `LogDetectorBaselineTest.recordBaseline_optionalFixturesPresent`.

Or Gradle (typical):

```bash
.\gradlew :app:connectedDebugAndroidTest
```

## Outputs

Writes:

```
<instrumentation_external_cache>/arwood_eval/baseline_run_<timestamp>.csv
```

If external cache is unavailable, it falls back to app cache dir.

Also writes an artifacts folder next to it:

```
<instrumentation_external_cache>/arwood_eval/artifacts_<timestamp>/
  <base>_input.png
  <base>_boxes.png
  <base>_mask.png
```

## Prototype UX/auth updates (local-only)

- Tiled inference overlap is now increased to a 30% shared band for 4/9/16 modes, and tiled mask merge is seam-tolerant (majority-based) to reduce hard-cut artifacts.
- In results view, when tiled mode is used (4/9/16), segment borders are drawn on the original image view.
- Login is now local account based (no default `admin/admin` user). Accounts can be created from the login page and are stored on-device.
- Analyses can be saved locally per user (keyed by username + timestamp id) with name and description, then reopened later from `Saved Analyses`.
- Saved analyses restore full snapshot views (original/mask/YOLO) and computed metrics without recomputation.

# Threshold tuning checklist (offline)

Tune using the baseline runner artifacts:
- CSV: `<externalCache>/arwood_eval/baseline_run_*.csv`
- PNGs: `<externalCache>/arwood_eval/artifacts_*/*_{input,boxes,mask}.png`

## Knobs wired to the app

Edit:
`app/src/main/assets/models/log_detector_metadata.json`

Fields under `postprocessDefaults`:
- `confidenceThreshold`
- `iouThreshold`
- `maskThreshold`
- `maskThresholdRelaxed`

## Suggested coarse grid

Confidence:
`0.25, 0.35, 0.45, 0.55`

IoU:
`0.35, 0.45, 0.55`

Mask:
`0.30, 0.40, 0.50`
Relaxed:
`0.25, 0.30, 0.35`

## What “good” looks like

On your locked evaluation set:
- fewer roof/background false positives (mask blobs not on log ends)
- stable counts under mild lighting JPEG noise
- mask edges less “block merged” vs neighbors (still depends heavily on dataset)

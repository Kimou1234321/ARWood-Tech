# Colab training recipe (offline Android target)

This app expects a **YOLOv8 segmentation** TFLite compatible with the interpreter setup in `LogDetector`:
- input: `[1, 640, 640, 3]` float32 RGB `/255` **with letterboxing in the app**
- outputs: `[1, 37, 8400]` + `[1, 160, 160, 32]`

## 1) Build a dataset

- Label **log end cross-sections** as instance segmentation masks (polygons), not only boxes.
- Include hard negatives: roofs, vehicles, soil, shadows.

Export from Roboflow as **YOLOv8 Segmentation** (Ultralytics-compatible).

## 2) Train baseline (fast)

Recommended first model for phone performance:

```bash
pip install -U ultralytics
yolo segment train model=yolov8n-seg.pt data=data.yaml imgsz=640 epochs=120 batch=16 patience=30
yolo segment val model=runs/segment/train/weights/best.pt data=data.yaml imgsz=640
```

Upgrade only if needed:

```bash
yolo segment train model=yolov8s-seg.pt data=data.yaml imgsz=640 epochs=120 batch=8 patience=30
```

## 3) Export to TFLite (Android)

```bash
yolo export model=runs/segment/train/weights/best.pt format=tflite imgsz=640 half=True
```

Copy the produced `.tflite` into:
```
app/src/main/assets/models/log_detector.tflite
```

## 4) Update app metadata

Edit:
```
app/src/main/assets/models/log_detector_metadata.json
```

Record:
- dataset id / date
- thresholds you validated
- training command + export command
- any known limitations

## 5) Evaluate on device

Use Android instrumentation baseline:
- `docs/android_evaluation.md`

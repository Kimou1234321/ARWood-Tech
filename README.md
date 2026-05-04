# 🌲 Algerian Wood

**Wood Volume Measurement System — Android Prototype**

An Android app that estimates the total volume of stacked wood logs from a single photograph using image segmentation and scale calibration.

---

## Features

| Feature | Status |
|---|---|
| Static login (admin/admin) | ✅ |
| Import picture from gallery | ✅ |
| Take picture with camera | ✅ |
| Two-point scale calibration | ✅ |
| AI log detection (prototype) | ✅ |
| Volume calculation per log | ✅ |
| Results overlay with stats | ✅ |
| Dark forest theme (green/brown) | ✅ |

---

## Setup Instructions

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17** (bundled with Android Studio)
- **Android SDK 34** (install via SDK Manager)

### Opening the Project

1. Open Android Studio
2. Select **"Open"** → navigate to this folder (`ProjectBGF`)
3. Wait for Gradle sync to complete (first sync downloads dependencies)
4. If prompted about Gradle wrapper, click **"OK"** to regenerate `gradlew`/`gradlew.bat`

### Running on Emulator

1. Open **Device Manager** → Create a new Virtual Device
2. Choose **Pixel 6** (or similar) → **API 34** system image
3. Click ▶ **Run** in the toolbar
4. Login with `admin` / `admin`

### Running on Physical Device

1. Enable **Developer Options** → **USB Debugging** on your phone
2. Connect via USB
3. Click ▶ **Run** — Android Studio will detect your device

---

## Architecture

```
com.algerianwood.app
├── data/
│   └── SessionData.kt          # Shared state between screens
├── navigation/
│   └── AppNavGraph.kt           # Compose Navigation graph
├── processing/
│   ├── LogDetector.kt           # AI/CV log detection engine
│   └── VolumeCalculator.kt      # Area & volume math
├── ui/
│   ├── theme/                   # Material3 dark forest theme
│   ├── login/                   # Login screen
│   ├── home/                    # Home (Import / Camera)
│   ├── camera/                  # CameraX integration
│   ├── calibration/             # Scale calibration + point picking
│   └── results/                 # Detection results + stats
└── MainActivity.kt              # Single-activity entry point
```

### Detection Pipeline

The `LogDetector` uses a pure-Android implementation (no external CV library):

1. **Downscale** image to 400px max for performance
2. **Grayscale** conversion
3. **Gaussian blur** (5×5 kernel) to reduce noise
4. **Otsu's threshold** to separate light log faces from dark gaps
5. **Morphological opening** to clean up small artifacts
6. **Connected component labeling** via BFS flood fill
7. **Filter** by area, aspect ratio, and fill ratio (circularity)
8. **Map** detected circles back to original image coordinates

### Volume Calculation

```
For each detected circle:
  radiusCm  = radiusPixels ÷ pixelPerCm
  areaCm²   = π × radiusCm²
  volumeCm³ = areaCm² × logLengthCm
  volumeM³  = volumeCm³ ÷ 1,000,000
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Navigation | Compose Navigation |
| Camera | CameraX |
| Build | Gradle KTS, AGP 8.2.2 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

---

## Future Improvements

- [ ] **OpenCV integration** — Hough Circle Transform for better detection
- [ ] **TensorFlow Lite model** — Custom-trained segmentation model
- [ ] **Panoramic capture** — Multi-frame stitching for wide log stacks
- [ ] **Measurement history** — Room database for saving past analyses
- [ ] **Export results** — PDF/CSV export of volume reports
- [ ] **Real authentication** — Firebase Auth or backend API
- [ ] **Adjustable detection** — Sensitivity sliders for threshold tuning

---

## License

Prototype — Internal use only.

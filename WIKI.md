# @pushpendersingh/react-native-scanner - Project Wiki

## Overview

This is a React Native library for scanning QR codes and barcodes. It's built with the **New Architecture** (Turbo Modules + Fabric) and supports both iOS and Android platforms.

**Version:** 3.0.0  
**Minimum React Native:** 0.80+  
**Languages:** TypeScript, Kotlin (Android), Swift/Objective-C++ (iOS)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        JavaScript Layer                             │
├─────────────────────────────────────────────────────────────────────┤
│  src/index.tsx          - BarcodeScanner class (main API)           │
│  src/CameraView.tsx     - React component for camera preview        │
│  src/NativeReactNativeScanner.ts - TurboModule spec (codegen)       │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │   React Native Bridge  │
                    │   (Turbo Modules)      │
                    └───────────┬───────────┘
                                │
        ┌───────────────────────┴───────────────────────┐
        │                                               │
┌───────┴───────┐                               ┌───────┴───────┐
│    Android    │                               │      iOS      │
├───────────────┤                               ├───────────────┤
│ ReactNative-  │                               │ ReactNative-  │
│ ScannerModule │                               │ Scanner.mm    │
│    (.kt)      │                               │   (Obj-C++)   │
├───────────────┤                               ├───────────────┤
│ CameraManager │                               │ CameraManager │
│    (.kt)      │                               │   (.swift)    │
├───────────────┤                               ├───────────────┤
│   CameraX +   │                               │ AVFoundation +│
│    ML Kit     │                               │    Vision     │
└───────────────┘                               └───────────────┘
```

---

## Project Structure

```
react-native-scanner/
├── src/                           # TypeScript source code
│   ├── index.tsx                  # Main exports & BarcodeScanner class
│   ├── CameraView.tsx             # Camera preview component
│   ├── NativeReactNativeScanner.ts# TurboModule interface spec
│   ├── ReactNativeScannerViewNativeComponent.ts
│   └── __tests__/                 # Jest unit tests
│
├── android/                       # Android native code
│   └── src/main/java/.../
│       ├── ReactNativeScannerModule.kt      # Turbo Module impl
│       ├── ReactNativeScannerViewManager.kt # Fabric View Manager
│       ├── ReactNativeScannerView.kt        # Native View
│       ├── ReactNativeScannerPackage.kt     # Package registration
│       └── CameraManager.kt                 # Camera & ML Kit logic
│
├── ios/                           # iOS native code
│   ├── ReactNativeScanner.mm      # Turbo Module impl (Obj-C++)
│   ├── ReactNativeScanner.h       # Header file
│   ├── ReactNativeScanner-Bridging-Header.h # Swift-ObjC bridging
│   ├── CameraViewManager.mm       # Fabric View Manager (Obj-C++)
│   ├── CameraManager.swift        # Camera & Vision logic
│   └── CameraView.swift           # Native UIView wrapper
│
├── example/                       # Example React Native app
│   └── src/App.tsx                # Demo implementation
│
├── lib/                           # Compiled output (generated)
└── package.json                   # Package configuration
```

---

## Key Components

### 1. BarcodeScanner Class (`src/index.tsx`)

The main API exposed to JavaScript. It's a static class that wraps native module calls.

**Methods:**
| Method | Description |
|--------|-------------|
| `startScanning(callback)` | Starts camera scanning, calls callback on detection |
| `stopScanning()` | Stops the scanning process |
| `enableFlashlight()` | Turns on camera torch |
| `disableFlashlight()` | Turns off camera torch |
| `releaseCamera()` | Releases all camera resources |
| `hasCameraPermission()` | Checks if camera permission granted |
| `requestCameraPermission()` | Requests camera permission from user |
| `scanImage(imageUri)` | Scans a static image for barcodes |

### 2. CameraView Component (`src/CameraView.tsx`)

A React component that renders the native camera preview. It's a Fabric Native Component.

```tsx
<CameraView style={{ flex: 1 }} />
```

### 3. TurboModule Spec (`src/NativeReactNativeScanner.ts`)

Defines the interface between JavaScript and native code using React Native's Codegen system.

```typescript
export interface Spec extends TurboModule {
  startScanning(): Promise<void>;
  stopScanning(): Promise<void>;
  // ... other methods
  readonly onBarcodeScanned: CodegenTypes.EventEmitter<BarcodeScannedEvent>;
}
```

---

## Native Implementation Details

### Android (Kotlin)

**Key files:**
- `ReactNativeScannerModule.kt` - Implements TurboModule spec
- `CameraManager.kt` - Core camera and barcode scanning logic

**Technologies used:**
- **CameraX 1.5.0** - Modern Android camera API with lifecycle awareness
- **ML Kit Barcode Scanning 17.3.0** - Google's ML-powered barcode detection

**Thread Safety:**
- Uses `@Volatile` for visibility of shared state
- Uses `AtomicBoolean` and `AtomicReference` for lock-free atomic operations
- Uses `ReentrantLock` with `withLock` for synchronized camera binding
- Ensures safe concurrent access to camera state

### iOS (Swift + Objective-C++)

**Key files:**
- `ReactNativeScanner.mm` - Objective-C++ bridge to TurboModule
- `CameraManager.swift` - Core camera and barcode scanning logic
- `CameraView.swift` - Native UIView for camera preview

**Technologies used:**
- **AVFoundation** - Native camera framework
- **Vision Framework** - Apple's barcode detection (primary)
- **CIDetector** - Core Image QR code detection (fallback for image scanning)

**Thread Safety:**
- Uses Swift Actors (`CameraSessionActor`, `CallbackActor`) for isolated state management
- Actors provide automatic serialization of concurrent access
- Uses `DispatchQueue` for session operations on dedicated background queue
- Ensures thread-safe operations across async/await boundaries

---

## Data Flow

### Scanning Flow

```
1. User calls BarcodeScanner.startScanning(callback)
       │
       ▼
2. JS sets up event listener for 'onBarcodeScanned'
       │
       ▼
3. Native module starts camera preview
       │
       ▼
4. CameraManager processes camera frames
       │
       ▼
5. ML Kit (Android) / Vision (iOS) detects barcode
       │
       ▼
6. Native emits 'onBarcodeScanned' event with result
       │
       ▼
7. JS callback receives BarcodeResult[]
```

### Image Scanning Flow

```
1. User calls BarcodeScanner.scanImage(imageUri)
       │
       ▼
2. Native loads image from URI
       │
       ▼
3. ML Kit / Vision processes static image
       │
       ▼
4. Promise resolves with BarcodeResult[]
```

---

## Types

### BarcodeResult

```typescript
interface BarcodeResult {
  data: string;      // Decoded barcode content
  type: BarcodeType; // Format type (QR_CODE, EAN_13, etc.)
  bounds?: {         // Optional bounding box
    width: number;
    height: number;
    origin: {
      topLeft: { x: number; y: number };
      bottomLeft: { x: number; y: number };
      bottomRight: { x: number; y: number };
      topRight: { x: number; y: number };
    };
  };
}
```

### BarcodeType

Supported barcode formats:

| Category | Formats | Platform Notes |
|----------|---------|----------------|
| **2D Codes** | QR_CODE, DATA_MATRIX, AZTEC, PDF417 | Both platforms |
| **1D Product** | EAN_13, EAN_8, UPC_E | Both platforms |
| **1D Product** | UPC_A | Android only |
| **1D Industrial** | CODE_128, CODE_39, CODE_93, CODABAR, ITF | Both platforms |
| **Other** | UNKNOWN | Fallback type |

**Note:** iOS uses Vision framework's `.itf14` symbology which maps to "ITF" type.

---

## Build System

### Codegen Configuration (`package.json`)

```json
{
  "codegenConfig": {
    "name": "ReactNativeScannerSpec",
    "type": "modules",
    "jsSrcsDir": "src",
    "android": {
      "javaPackageName": "com.pushpendersingh.reactnativescanner"
    }
  }
}
```

### Build Output

Uses `react-native-builder-bob` to compile:
- ESM modules to `lib/module/`
- TypeScript declarations to `lib/typescript/`

---

## Testing

**Framework:** Jest with React Native preset

**Run tests:**
```bash
yarn test
```

**Test coverage:**
- BarcodeScanner method calls
- Event listener management
- Permission handling
- Multiple barcode type support

---

## Development Scripts

| Script | Description |
|--------|-------------|
| `yarn test` | Run Jest tests |
| `yarn typecheck` | TypeScript type checking |
| `yarn lint` | ESLint code linting |
| `yarn prepare` | Build library with bob |
| `yarn clean` | Clean build artifacts |
| `yarn example` | Run example app commands |

---

## Dependencies

### Runtime
- No external JS dependencies (peer deps: `react`, `react-native`)

### Development
- TypeScript 5.9+
- ESLint 9.x
- Jest 29.x
- react-native-builder-bob
- release-it (for publishing)

### Native (Android)
- CameraX 1.5.0
- ML Kit Barcode Scanning 17.3.0

### Native (iOS)
- AVFoundation (system framework)
- Vision (system framework)

---

## Lifecycle Management

The library handles camera lifecycle automatically:

1. **Module Creation** - CameraManager initialized
2. **Start Scanning** - Camera session starts, preview begins
3. **Stop Scanning** - Scanning paused, camera may stay active
4. **Release Camera** - Full cleanup of camera resources
5. **Module Invalidation** - Automatic cleanup on unmount

**Best Practice:**
```tsx
useEffect(() => {
  BarcodeScanner.startScanning(handleBarcode);
  
  return () => {
    BarcodeScanner.stopScanning();
    BarcodeScanner.releaseCamera();
  };
}, []);
```

---

## Error Handling

Native errors are propagated to JavaScript with error codes:

| Error Code | Description |
|------------|-------------|
| `PERMISSION_DENIED` | Camera permission not granted |
| `START_SCANNING_ERROR` | Failed to start camera |
| `STOP_SCANNING_ERROR` | Failed to stop scanning |
| `FLASHLIGHT_ERROR` | Flashlight toggle failed |
| `RELEASE_CAMERA_ERROR` | Camera cleanup failed |
| `SCAN_IMAGE_ERROR` | Image scanning failed |

---

## Platform-Specific Notes

### iOS
- Requires physical device (simulator has no camera)
- Info.plist permission required: `NSCameraUsageDescription`
- Minimum iOS version determined by Vision framework availability

**Note:** If using `scanImage()` with gallery images, you'll need a third-party image picker library (e.g., `react-native-image-picker`) which may require additional permissions like `NSPhotoLibraryUsageDescription`. See that library's documentation for details.

### Android
- Requires Google Play Services for ML Kit
- Manifest permission required: `CAMERA`
- API 23+ for runtime permissions

**Note:** If using `scanImage()` with gallery images, you'll need a third-party image picker library (e.g., `react-native-image-picker`) which may require additional permissions like `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`. See that library's documentation for details.

---

## Further Reading

- [README.md](./README.md) - Installation and usage guide
- [CONTRIBUTING.md](./CONTRIBUTING.md) - Contribution guidelines
- [example/src/App.tsx](./example/src/App.tsx) - Full example implementation

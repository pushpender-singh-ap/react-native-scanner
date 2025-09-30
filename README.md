# @pushpendersingh/react-native-scanner

<div align="center">

![React Native](https://img.shields.io/badge/React%20Native-v0.81+-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-lightgrey.svg)

**A powerful, easy-to-use QR code & Barcode Scanner for React Native with New Architecture support**

</div>

---

## 📖 About

A QR code & Barcode Scanner for React Native Projects.

For React Native developers that need to scan barcodes and QR codes in their apps, this package is a useful resource. It supports React Native's new Fabric Native architecture and was created in Kotlin (Android) and Swift (iOS) with Objective-C++ bridges.

With this package, users can quickly and easily scan barcodes and QR codes with their device's camera. Using this package, several types of codes can be scanned, and it is simple to use and integrate into your existing projects.

If you want to provide your React Native app the ability to read barcodes and QR codes, you should definitely give this package some thought.

The `@pushpendersingh/react-native-scanner` package also includes a flashlight feature that can be turned on and off. This can be useful when scanning QR codes & barcodes in low light conditions.

---

## ✨ Features

- 📱 **Cross-platform** - Works on both iOS and Android
- 🚀 **New Architecture Ready** - Built with Turbo Modules & Fabric
- 📷 **Camera Preview** - Real-time camera feed with preview
- 🔍 **Multiple Formats** - Supports 13+ barcode formats (QR, EAN, Code128, etc.)
- ⚡ **High Performance** - Optimized with CameraX (Android) & AVFoundation (iOS)
- 🎯 **Easy Integration** - Simple API with event-based scanning
- 💡 **Flash Support** - Toggle flashlight on/off
- 🔄 **Lifecycle Management** - Automatic camera resource handling
- 🎨 **Customizable** - Flexible styling options

---

## 📦 Installation

```bash
npm install @pushpendersingh/react-native-scanner
```

or

```bash
yarn add @pushpendersingh/react-native-scanner
```

### iOS Setup

1. Install CocoaPods dependencies:

```bash
cd ios && pod install && cd ..
```

2. Add camera permission to `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>We need camera access to scan barcodes</string>
```

### Android Setup

Add camera permission to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
```

---

## 🎯 Usage

### Basic Example

```tsx
import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { BarcodeScanner, CameraView } from '@pushpendersingh/react-native-scanner';

export default function App() {
  const [scanning, setScanning] = useState(false);
  const [result, setResult] = useState('');

  const startScanning = async () => {
    try {
      setScanning(true);
      await BarcodeScanner.startScanning((barcode) => {
        console.log('Barcode detected:', barcode);
        setResult(`${barcode.type}: ${barcode.data}`);
        stopScanning();
      });
    } catch (error) {
      console.error('Failed to start scanning:', error);
    }
  };

  const stopScanning = async () => {
    try {
      await BarcodeScanner.stopScanning();
      setScanning(false);
    } catch (error) {
      console.error('Failed to stop scanning:', error);
    }
  };

  return (
    <View style={styles.container}>
      <CameraView style={styles.camera} />
      
      <View style={styles.controls}>
        <TouchableOpacity 
          style={styles.button} 
          onPress={scanning ? stopScanning : startScanning}
        >
          <Text style={styles.buttonText}>
            {scanning ? 'Stop Scanning' : 'Start Scanning'}
          </Text>
        </TouchableOpacity>
        
        {result && (
          <View style={styles.resultContainer}>
            <Text style={styles.resultText}>{result}</Text>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  camera: {
    flex: 1,
  },
  controls: {
    position: 'absolute',
    bottom: 50,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  button: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 8,
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  resultContainer: {
    marginTop: 16,
    backgroundColor: 'rgba(0,0,0,0.7)',
    padding: 16,
    borderRadius: 8,
  },
  resultText: {
    color: 'white',
    fontSize: 14,
  },
});
```

---

## 📚 API Reference

### `BarcodeScanner`

#### `startScanning(callback)`

Starts the barcode scanning process.

```typescript
BarcodeScanner.startScanning((barcode: BarcodeResult) => {
  console.log('Type:', barcode.type);
  console.log('Data:', barcode.data);
  console.log('Raw:', barcode.raw);
});
```

**Parameters:**
- `callback: (barcode: BarcodeResult) => void` - Called when a barcode is detected

**Returns:** `Promise<void>`

---

#### `stopScanning()`

Stops the barcode scanning process.

```typescript
await BarcodeScanner.stopScanning();
```

**Returns:** `Promise<void>`

---

#### `releaseCamera()`

Releases the camera resources completely.

```typescript
await BarcodeScanner.releaseCamera();
```

**Returns:** `Promise<void>`

---

#### `setFlash(enabled)`

Toggles the camera flash on/off.

```typescript
await BarcodeScanner.setFlash(true);  // Turn on
await BarcodeScanner.setFlash(false); // Turn off
```

**Parameters:**
- `enabled: boolean` - Enable or disable flash

**Returns:** `Promise<void>`

---

### `CameraView`

React component that renders the camera preview.

```typescript
<CameraView style={{ flex: 1 }} />
```

**Props:**
- `style?: ViewStyle` - Style for the camera view container

---

### Types

#### `BarcodeResult`

```typescript
interface BarcodeResult {
  type: string;      // Barcode format (e.g., 'QR_CODE', 'EAN_13')
  data: string;      // Decoded barcode data
  raw: string;       // Raw barcode value
}
```

---

## 🎨 Advanced Usage

### Flashlight Control

```tsx
import { BarcodeScanner } from '@pushpendersingh/react-native-scanner';

const [flashEnabled, setFlashEnabled] = useState(false);

const toggleFlash = async () => {
  const newState = !flashEnabled;
  await BarcodeScanner.setFlash(newState);
  setFlashEnabled(newState);
};
```

### Lifecycle Management

```tsx
import { useEffect } from 'react';
import { BarcodeScanner } from '@pushpendersingh/react-native-scanner';

useEffect(() => {
  // Start scanning on mount
  BarcodeScanner.startScanning(handleBarcode);

  // Cleanup on unmount
  return () => {
    BarcodeScanner.stopScanning();
    BarcodeScanner.releaseCamera();
  };
}, []);
```

### Permission Handling

> **⚠️ Important:** We **strongly recommend** using [`react-native-permissions`](https://github.com/zoontek/react-native-permissions) for handling camera permissions in production apps. This provides better UX, more control, and unified API across platforms.

#### Recommended: Using react-native-permissions

```bash
npm install react-native-permissions
# or
yarn add react-native-permissions
```

```tsx
import { request, PERMISSIONS, RESULTS } from 'react-native-permissions';
import { Platform } from 'react-native';

const requestCameraPermission = async () => {
  const result = await request(
    Platform.OS === 'ios' 
      ? PERMISSIONS.IOS.CAMERA 
      : PERMISSIONS.ANDROID.CAMERA
  );
  
  switch (result) {
    case RESULTS.GRANTED:
      return true;
    case RESULTS.DENIED:
      console.log('Permission denied');
      return false;
    case RESULTS.BLOCKED:
      console.log('Permission blocked - open settings');
      return false;
    default:
      return false;
  }
};
```

#### Using React Native's PermissionsAndroid (Android only)

```tsx
import { PermissionsAndroid, Platform } from 'react-native';

const requestCameraPermission = async () => {
  if (Platform.OS === 'android') {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CAMERA
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }
  return true; // iOS handles via Info.plist
};
```

---

## 📋 Supported Barcode Formats

This library supports a wide range of barcode formats across different categories:

| 1D Product            | 1D Industrial | 2D             |
|:----------------------|:--------------|:---------------|
| UPC-A                 | Code 39       | QR Code        |
| UPC-E                 | Code 93       | Data Matrix    |
| EAN-8                 | Code 128      | Aztec          |
| EAN-13                | Codabar       | PDF 417        |
|                       | ITF           |                |

**Format Details:**

- **1D Product Codes**: Commonly used in retail (UPC-A, UPC-E, EAN-8, EAN-13)
- **1D Industrial Codes**: Used in logistics and inventory (Code 39, Code 93, Code 128, Codabar, ITF)
- **2D Codes**: High-density codes for storing more data (QR Code, Data Matrix, Aztec, PDF 417)

**Total Supported Formats**: 13 barcode types

---

## 🛠️ Technical Details

### Android
- **CameraX 1.5.0** - Modern camera API with lifecycle awareness
- **ML Kit Barcode Scanning 17.3.0** - Google's ML-powered barcode detection
- **Kotlin** - Native implementation

### iOS
- **AVFoundation** - Native camera framework
- **Vision Framework** - Apple's barcode detection
- **Swift 5.0** - Native implementation

### React Native
- **New Architecture** - Turbo Modules + Fabric support
- **React Native 0.81+** - Minimum version requirement

---

## 🔧 Troubleshooting

### Camera Preview Not Showing

**iOS:**
- Check camera permission in `Info.plist`
- Ensure you're running on a physical device (simulator doesn't have camera)

**Android:**
- Check camera permission in `AndroidManifest.xml`
- Verify Google Play Services is installed

### Barcode Not Scanning

- Ensure good lighting conditions
- Hold barcode steady and at proper distance
- Check that barcode format is supported
- Verify barcode is not damaged or distorted

### IMEI/IMEI2 Scanning

**✅ Fixed:** Previously, the scanner could detect EID and MEID but had issues scanning IMEI and IMEI2 numbers. This has been resolved in the current version.

**How it works:**
- IMEI and IMEI2 are typically encoded as **CODE_128** or **CODE_39** barcodes
- The scanner now properly detects and decodes these formats
- Both IMEI (15 digits) and IMEI2 (dual SIM devices) are fully supported

**Tips for scanning IMEI:**
- Ensure the IMEI barcode is clean and undamaged
- Use good lighting (enable flashlight if needed)
- Hold device steady at 10-15cm distance from the barcode
- IMEI barcodes are usually found on device packaging or SIM trays

### Build Issues

**iOS:**
```bash
cd ios && pod deintegrate && pod install && cd ..
```

**Android:**
```bash
cd android && ./gradlew clean && cd ..
```

---

## 📖 Example App

Check out the [example app](./example) for a complete working implementation.

**Run the example:**

```bash
# Install dependencies
cd example && yarn

# iOS
cd example && npx pod-install && yarn ios

# Android
cd example && yarn android
```

---

## 🚀 Roadmap & Future Improvements

We're constantly working to improve this library. Here are some planned enhancements:

### Planned Features

- [ ] **Enhanced Permission Handling** - Implement proper native permission callback mechanism for `requestCameraPermission()` method with promise resolution based on user response
- [ ] **Barcode Generation** - Add ability to generate barcodes/QR codes
- [ ] **Image Analysis** - Support scanning barcodes from gallery images
- [ ] **Advanced Camera Controls** - Zoom, focus, and exposure controls

### Known Limitations

- **Permission Handling**: The built-in `requestCameraPermission()` currently triggers the system dialog but doesn't wait for user response. We recommend using `react-native-permissions` for production apps. A proper implementation with permission callbacks is planned for a future release.

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Workflow

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Run tests: `yarn test`
5. Commit: `git commit -m 'Add new feature'`
6. Push: `git push origin feature/my-feature`
7. Open a Pull Request

Please read our [Code of Conduct](CODE_OF_CONDUCT.md) before contributing.

---

## 📄 License

MIT © [Pushpender Singh](https://github.com/pushpender-singh-ap)

---

## 🙏 Acknowledgments

- Built with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
- Uses [CameraX](https://developer.android.com/training/camerax) on Android
- Uses [AVFoundation](https://developer.apple.com/av-foundation/) on iOS
- Barcode detection powered by [ML Kit](https://developers.google.com/ml-kit/vision/barcode-scanning) (Android) and [Vision](https://developer.apple.com/documentation/vision) (iOS)

---

## 📞 Support

- 🐛 [Report a bug](https://github.com/pushpender-singh-ap/react-native-scanner/issues)
- 💡 [Request a feature](https://github.com/pushpender-singh-ap/react-native-scanner/issues)

---

<div align="center">

**If you find this package helpful, please give it a ⭐️ on [GitHub](https://github.com/pushpender-singh-ap/react-native-scanner)!**

</div>

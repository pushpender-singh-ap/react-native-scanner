# @pushpendersingh/react-native-scanner

<div align="center">

![React Native](https://img.shields.io/badge/React%20Native-v0.80+-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-lightgrey.svg)
![Version](https://img.shields.io/badge/version-2.0.0-brightgreen.svg)

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
- 🔒 **Thread-Safe** - Modern concurrency patterns (Swift Actors & Kotlin synchronization)
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
      await BarcodeScanner.startScanning((barcodes) => {
        console.log('Barcodes detected:', barcodes);
        if (barcodes.length > 0) {
          const barcode = barcodes[0];
          setResult(`${barcode.type}: ${barcode.data}`);
        }
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
BarcodeScanner.startScanning((barcodes: BarcodeResult[]) => {
  barcodes.forEach((barcode) => {
    console.log('Type:', barcode.type);
    console.log('Data:', barcode.data);
    console.log('Raw:', barcode.raw);
  });
});
```

**Parameters:**
- `callback: (barcodes: BarcodeResult[]) => void` - Called when barcodes are detected

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

#### `enableFlashlight()` / `disableFlashlight()`

Control the camera flashlight (torch) explicitly.

```typescript
// Turn flashlight on
await BarcodeScanner.enableFlashlight();

// Turn flashlight off
await BarcodeScanner.disableFlashlight();
```

**Returns:** `Promise<void>`

---

#### `hasCameraPermission()`

Checks if camera permission is currently granted.

```typescript
const hasPermission = await BarcodeScanner.hasCameraPermission();
console.log('Camera permission granted:', hasPermission);
```

**Returns:** `Promise<boolean>` - `true` if permission granted, `false` otherwise

---

#### `requestCameraPermission()`

Requests camera permission from the user with native promise resolution.

```typescript
const granted = await BarcodeScanner.requestCameraPermission();
if (granted) {
  console.log('Permission granted!');
  // Start scanning
} else {
  console.log('Permission denied');
  // Show error or guide user to settings
}
```

**Returns:** `Promise<boolean>` - `true` if user grants permission, `false` if denied

**Platform Support:**
- ✅ **iOS**: Fully supported with native callback
- ✅ **Android**: Fully supported with native callback (API 23+)

**Note:** This method shows the native system permission dialog and waits for the user's response, then resolves the promise based on their choice.

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
  try {
    if (flashEnabled) {
      await BarcodeScanner.disableFlashlight();
      setFlashEnabled(false);
    } else {
      await BarcodeScanner.enableFlashlight();
      setFlashEnabled(true);
    }
  } catch (err) {
    console.error('Failed to toggle flashlight', err);
  }
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

The library now provides **built-in native camera permission methods** that work seamlessly on both iOS and Android with proper promise resolution based on user response.

#### ✅ Using Built-in Permission Methods (Recommended)

The library includes native methods that handle camera permissions with proper callbacks:

```tsx
import React, { useEffect, useState } from 'react';
import { View, Text, Button, Alert } from 'react-native';
import { BarcodeScanner, CameraView } from '@pushpendersingh/react-native-scanner';

export default function App() {
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [scanning, setScanning] = useState(false);

  useEffect(() => {
    checkPermission();
  }, []);

  const checkPermission = async () => {
    const granted = await BarcodeScanner.hasCameraPermission();
    setHasPermission(granted);
  };

  const requestPermission = async () => {
    const granted = await BarcodeScanner.requestCameraPermission();
    setHasPermission(granted);
    
    if (granted) {
      Alert.alert('Success', 'Camera permission granted!');
    } else {
      Alert.alert(
        'Permission Denied', 
        'Camera permission is required to scan barcodes'
      );
    }
  };

  const startScanning = async () => {
    // Check permission before scanning
    const granted = await BarcodeScanner.hasCameraPermission();
    
    if (!granted) {
      Alert.alert(
        'Permission Required',
        'Please grant camera permission to scan barcodes',
        [{ text: 'Grant Permission', onPress: requestPermission }]
      );
      return;
    }
    await BarcodeScanner.startScanning((barcodes) => {
      console.log('Scanned:', barcodes);
      BarcodeScanner.stopScanning();
      setScanning(false);
    });
  };

  if (hasPermission === null) {
    return <Text>Checking camera permission...</Text>;
  }

  if (hasPermission === false) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <Text style={{ marginBottom: 20 }}>Camera permission not granted</Text>
        <Button title="Grant Permission" onPress={requestPermission} />
      </View>
    );
  }

  return (
    <View style={{ flex: 1 }}>
      <CameraView style={{ flex: 1 }} />
      <Button 
        title={scanning ? 'Stop Scanning' : 'Start Scanning'} 
        onPress={startScanning} 
      />
    </View>
  );
}
```

**Key Features:**
- ✅ **Cross-platform**: Works on both iOS (API 10+) and Android (API 23+)
- ✅ **Promise-based**: Returns `true` when granted, `false` when denied
- ✅ **Native callbacks**: Waits for actual user response from system dialog
- ✅ **No dependencies**: No need for additional permission libraries

#### Alternative: Using react-native-permissions

For more advanced permission handling (checking settings, handling blocked state, etc.), you can use [`react-native-permissions`](https://github.com/zoontek/react-native-permissions):

```bash
npm install react-native-permissions
# or
yarn add react-native-permissions
```

```tsx
import { request, check, PERMISSIONS, RESULTS } from 'react-native-permissions';
import { Platform, Linking } from 'react-native';

const checkCameraPermission = async () => {
  const permission = Platform.select({
    ios: PERMISSIONS.IOS.CAMERA,
    android: PERMISSIONS.ANDROID.CAMERA,
  });

  const result = await check(permission);
  
  switch (result) {
    case RESULTS.GRANTED:
      return 'granted';
    case RESULTS.DENIED:
      return 'denied';
    case RESULTS.BLOCKED:
      return 'blocked';
    default:
      return 'unavailable';
  }
};

const requestCameraPermission = async () => {
  const permission = Platform.select({
    ios: PERMISSIONS.IOS.CAMERA,
    android: PERMISSIONS.ANDROID.CAMERA,
  });

  const result = await request(permission);
  
  if (result === RESULTS.BLOCKED) {
    // User has blocked permission, guide them to settings
    Alert.alert(
      'Permission Blocked',
      'Please enable camera permission in settings',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Open Settings', onPress: () => Linking.openSettings() },
      ]
    );
    return false;
  }
  
  return result === RESULTS.GRANTED;
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
- **Kotlin** - Native implementation with thread-safe synchronization
- **Thread Safety** - Uses `@Volatile` and `@Synchronized` for concurrent access protection

### iOS

- **AVFoundation** - Native camera framework
- **Vision Framework** - Apple's barcode detection
- **Swift 5.0+** - Native implementation with modern concurrency
- **Thread Safety** - Uses Swift Actors for isolated state management and thread-safe operations

### React Native

- **New Architecture** - Turbo Modules + Fabric support
- **React Native 0.80+** - Minimum version requirement
- **Codegen** - Automatic native interface generation

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

- [ ] **Barcode Generation** - Add ability to generate barcodes/QR codes
- [ ] **Image Analysis** - Support scanning barcodes from gallery images
- [ ] **Advanced Camera Controls** - Zoom, focus, and exposure controls

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

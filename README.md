# @pushpendersingh/react-native-scanner

⚠️ Note: This package is currently not compatible with React Native 0.80 due to recent architectural changes.
If you're using this library, please stick to React Native 0.79 or below for now.
An update to support React Native 0.80 is planned, but there's no confirmed timeline for release.
🤝 If you'd like to contribute, please feel free to open a pull request — your support is always welcome!
Thank you for your understanding.

A QR code & Barcode Scanner for React Native Projects.

For React Native developers that need to scan barcodes and QR codes in their apps, this package is a useful resource. It supports React Native's new Fabric Native architecture and was created in Kotlin and Objective-C.

With this package, users can quickly and easily scan barcodes and QR codes with their device's camera. Using this package, several types of codes can be scanned, and it is simple to use.

If you want to provide your React Native app the ability to read barcodes and QR codes, you should definitely give this package some thought.

The `@pushpendersingh/react-native-scanner` package also includes a flashlight feature that can be turned on and off. This can be useful when scanning QR codes & barcodes in low light conditions.

## Getting started

### Requirements

#### IOS

Open your project's `Info.plist` and add the following lines inside the outermost `<dict>` tag:

```xml
<key>NSCameraUsageDescription</key>
<string>Your message to user when the camera is accessed for the first time</string>
```

Open your project's `Podfile` and add enable the new architecture:

```
:fabric_enabled => true,
```

Run below command to enable the new architecture in IOS folder

```
bundle install && RCT_NEW_ARCH_ENABLED=1 bundle exec pod install
```

### Android

Open your project's `AndroidManifest.xml` and add the following lines inside the `<manifest>` tag:

```xml
<uses-permission android:name="android.permission.CAMERA" />

<uses-feature android:name="android.hardware.camera.any" />
```

Open your project's `gradle.properties` and add enable the new architecture:

```
newArchEnabled=true
```

### To install and start using @pushpendersingh/react-native-scanner

```sh
npm install @pushpendersingh/react-native-scanner
```

### Supported Formats

| 1D product            | 1D industrial | 2D             |
|:----------------------|:--------------|:---------------|
| UPC-A                 | Code 39       | QR Code        |
| UPC-E                 | Code 93       | Data Matrix    |
| EAN-8                 | Code 128      | Aztec          |
| EAN-13                | Codabar       | PDF 417        |
|                       | ITF           |                |

## Usage

To use @pushpendersingh/react-native-scanner, `import` the `@pushpendersingh/react-native-scanner` module and use the `<ReactNativeScannerView />` tag. More usage examples can be seen under the `examples/` folder.

<details>
  <summary>Basic usage</summary>

Here is an example of basic usage:

```js
import React, {useEffect, useRef, useState} from 'react';
import {
  Alert,
  Platform,
  Text,
  SafeAreaView,
  Button,
  View,
  StyleSheet,
} from 'react-native';

import {
  request,
  PERMISSIONS,
  openSettings,
  RESULTS,
} from 'react-native-permissions'; // For camera permission
import {
  Commands,
  ReactNativeScannerView,
} from '@pushpendersingh/react-native-scanner';

export default function App() {
  const scannerRef = useRef(null);
  const [isCameraPermissionGranted, setIsCameraPermissionGranted] =
    useState(false);
  const [isActive, setIsActive] = useState(true);
  const [scannedData, setScannedData] = useState(null);

  useEffect(() => {
    checkCameraPermission();
  }, []);

  const handleBarcodeScanned = event => {
    const {data, bounds, type} = event?.nativeEvent;
    setScannedData({data, bounds, type});
    console.log('Barcode / QR Code scanned:', data, bounds, type);
  };

  const enableFlashlight = () => {
    if (scannerRef?.current) {
      Commands.enableFlashlight(scannerRef.current);
    }
  };

  const disableFlashlight = () => {
    if (scannerRef?.current) {
      Commands.disableFlashlight(scannerRef.current);
    }
  };

  // Pause the camera after barcode / QR code is scanned
  const stopScanning = () => {
    if (scannerRef?.current) {
      Commands.stopScanning(scannerRef?.current);
      console.log('Scanning paused');
    }
  };

  // Resume the camera after barcode / QR code is scanned
  const resumeScanning = () => {
    if (scannerRef?.current) {
      Commands.resumeScanning(scannerRef?.current);
      console.log('Scanning resumed');
    }
  };

  const releaseCamera = () => {
    if (scannerRef?.current) {
      Commands.releaseCamera(scannerRef?.current);
    }
  }

  const startScanning = () => {
    if (scannerRef?.current) {
      Commands.startCamera(scannerRef?.current);
    }
  }

  const checkCameraPermission = async () => {
    request(
      Platform.OS === 'ios'
        ? PERMISSIONS.IOS.CAMERA
        : PERMISSIONS.ANDROID.CAMERA,
    ).then(async (result: any) => {
      switch (result) {
        case RESULTS.UNAVAILABLE:
          // console.log('This feature is not available (on this device / in this context)');
          break;
        case RESULTS.DENIED:
          Alert.alert(
            'Permission Denied',
            'You need to grant camera permission first',
          );
          openSettings();
          break;
        case RESULTS.GRANTED:
          setIsCameraPermissionGranted(true);
          break;
        case RESULTS.BLOCKED:
          Alert.alert(
            'Permission Blocked',
            'You need to grant camera permission first',
          );
          openSettings();
          break;
      }
    });
  };

  if (isCameraPermissionGranted) {
    return (
      <SafeAreaView style={styles.container}>
        {isActive && (
          <ReactNativeScannerView
            ref={scannerRef}
            style={styles.scanner}
            onQrScanned={handleBarcodeScanned}
            pauseAfterCapture={true} // Pause the scanner after barcode / QR code is scanned
            isActive={isActive} // Start / stop the scanner using this prop
            showBox={true} // Show the box around the barcode / QR code
          />
        )}

        <View style={styles.controls}>
          <Button
            title="Stop Scanning"
            onPress={() => {
              stopScanning();
              setIsActive(false);
            }}
          />
          <Button
            title="Resume Scanning"
            onPress={() => {
              resumeScanning();
              setIsActive(true);
            }}
          />
          <Button
            title="Flash Off"
            onPress={() => {
              disableFlashlight();
            }}
          />
          <Button
            title="Flash On"
            onPress={() => {
              enableFlashlight();
            }}
          />
          <Button
            title="Release Camera"
            onPress={() => {
              releaseCamera();
            }}
          />
          <Button
            title="Start Camera"
            onPress={() => {
              startScanning();
            }}
          />
        </View>

        {scannedData && (
          <View style={styles.result}>
            <Text style={styles.resultText}>
              Scanned Data: {scannedData?.data}
            </Text>
            <Text style={styles.resultText}>Type: {scannedData?.type}</Text>
          </View>
        )}
      </SafeAreaView>
    );
  } else {
    return (
      <Text style={styles.TextStyle}>
        You need to grant camera permission first
      </Text>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  box: {
    position: 'absolute',
    borderWidth: 2,
    borderColor: 'green',
    zIndex: 10,
  },
  scanner: {
    flex: 1,
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginVertical: 10,
    flexWrap: 'wrap',
    gap: 8,
    marginHorizontal: 10,
  },
  result: {
    marginTop: 16,
    padding: 16,
    backgroundColor: '#f9f9f9',
    borderRadius: 8,
  },
  resultText: {
    fontSize: 16,
    marginVertical: 4,
  },
  TextStyle: {
    fontSize: 30,
    color: 'red',
  },
});
```

</details>

## Props

#### `onQrScanned` (required)

propType: `function.isRequired`
default: `(e) => (console.log('QR code scanned!', e))`

In the event that a QR code or barcode is detected in the camera's view, this specified method will be called.

#### `pauseAfterCapture` (required)
propType: `boolean`
default: `false`

If set to `true`, the scanner will pause after capturing a QR code or barcode.

#### `showBox` (optional)
propType: `boolean`
default: `false`

If set to `true`, a green box will be displayed around the QR code or barcode that is detected.

#### `isActive` (required)
propType: `boolean`
default: `true`

If set to `false`, the scanner will be disabled. This can be useful when you want to pause the scanner.

## Native Commands

The `@pushpendersingh/react-native-scanner` package also includes a few native commands that can be used to control the camera and flashlight.

### Commands

#### `enableFlashlight`

This command is used to turn on the flashlight.
```js
if(cameraRef.current) {
  Commands.enableFlashlight(cameraRef.current);
}
```

#### `disableFlashlight`

This command is used to turn off the flashlight.
```js
if(cameraRef.current) {
  Commands.disableFlashlight(cameraRef.current);
}
```

#### `releaseCamera`

This command is used to release the camera.

```js
if(cameraRef?.current) {
  Commands?.releaseCamera(cameraRef?.current);
}
```

#### `startCamera`

This command is used to start the camera.

```js
if(cameraRef?.current) {
  Commands.startCamera(cameraRef?.current);
}
```

#### `stopScanning`

This command is used to stop the scanning.

```js
if(cameraRef.current) {
  Commands.stopScanning(cameraRef.current);
}
```

### `resumeScanning`

This command is used to resume the scanning.

```js
if(cameraRef.current) {
  Commands.resumeScanning(cameraRef.current);
}
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

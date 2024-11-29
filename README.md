# @pushpendersingh/react-native-scanner

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
    console.log('Barcode scanned:', data, bounds, type);
  };

  // Pause the camera after barcode / QR code is scanned
  const pauseScanning = () => {
    if (scannerRef?.current) {
      Commands.pauseScanning(scannerRef?.current);
      console.log('Camera preview paused');
    }
  };

  // Resume the camera after barcode / QR code is scanned
  const resumeScanning = () => {
    if (scannerRef?.current) {
      Commands.resumeScanning(scannerRef?.current);
      console.log('Camera preview resumed');
    }
  };

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
      <SafeAreaView style={{flex: 1}}>
        {isActive && (
          <ReactNativeScannerView
            ref={scannerRef}
            style={styles.scanner}
            onQrScanned={handleBarcodeScanned}
            pauseAfterCapture={false} // Pause the scanner after barcode / QR code is scanned
            isActive={isActive} // Start / stop the scanner using this prop
          />
        )}

        <View style={styles.controls}>
          <Button title="Pause Scanning" onPress={pauseScanning} />
          <Button title="Resume Scanning" onPress={resumeScanning} />
          <Button title="Stop Scanner" onPress={() => setIsActive(false)} />
          <Button title="Restart Scanner" onPress={() => setIsActive(true)} />
        </View>

        {scannedData && (
          <View style={styles.result}>
            <Text style={styles.resultText}>
              Scanned Data: {scannedData?.data}
            </Text>
            <Text style={styles.resultText}>Type: {scannedData?.type}</Text>
            <Text style={styles.resultText}>
              Bounds: {JSON.stringify(scannedData?.bounds)}
            </Text>
          </View>
        )}
      </SafeAreaView>
    );
  } else {
    return (
      <Text style={{fontSize: 30, color: 'red'}}>
        You need to grant camera permission first
      </Text>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: '#fff',
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
});
```

</details>

## Flashlight Feature

<details>
  <summary>Flashlight Feature</summary>

  To use the flashlight feature, add the following code to your project:

```jsx
import React, {useEffect, useRef, useState} from 'react';
import {
  Alert,
  Platform,
  useWindowDimensions,
  Text,
  SafeAreaView,
  TouchableOpacity,
} from 'react-native';

import {
  request,
  PERMISSIONS,
  openSettings,
  RESULTS,
} from 'react-native-permissions';
import {
  ReactNativeScannerView,
  Commands,
} from '@pushpendersingh/react-native-scanner';

export default function App() {
  const {height, width} = useWindowDimensions();
  const [isCameraPermissionGranted, setIsCameraPermissionGranted] =
    useState(false);
  const cameraRef = useRef(null);

  useEffect(() => {
    checkCameraPermission();

    return () => {
      if(cameraRef.current) {
        Commands.releaseCamera(cameraRef.current);
      }
    };
  }, []);

  const enableFlashlight = () => {
    Commands.enableFlashlight(cameraRef.current);
  };

  const disableFlashlight = () => {
    Commands.disableFlashlight(cameraRef.current);
  };

  const checkCameraPermission = async () => {
    request(
      Platform.OS === 'ios'
        ? PERMISSIONS.IOS.CAMERA
        : PERMISSIONS.ANDROID.CAMERA,
    ).then(async (result: any) => {
      switch (result) {
        case RESULTS.UNAVAILABLE:
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
      <SafeAreaView style={{flex: 1}}>
        <ReactNativeScannerView
          ref={ref => (cameraRef.current = ref)}
          style={{height, width}}
          onQrScanned={(value: any) => {
            console.log(value.nativeEvent);
          }}
        />

        <TouchableOpacity
          style={{
            position: 'absolute',
            bottom: 20,
            left: 20,
            padding: 10,
            backgroundColor: 'blue',
            borderRadius: 10,
          }}
          onPress={enableFlashlight}>
          <Text>Turn ON</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={{
            position: 'absolute',
            bottom: 20,
            right: 20,
            padding: 10,
            backgroundColor: 'blue',
            borderRadius: 10,
          }}
          onPress={disableFlashlight}>
          <Text>Turn OFF</Text>
        </TouchableOpacity>
      </SafeAreaView>
    );
  } else {
    return (
      <Text style={{fontSize: 30, color: 'red'}}>
        You need to grant camera permission first
      </Text>
    );
  }
}
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
if(cameraRef.current) {
  Commands.releaseCamera(cameraRef.current);
}
```

### `pauseScanning`

This command is used to pause the scanning.

```js
if(cameraRef.current) {
  Commands.pauseScanning(cameraRef.current);
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

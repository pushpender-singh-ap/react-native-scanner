# @pushpendersingh/react-native-scanner

A QR code & Barcode Scanner for React Native Projects.

For React Native developers that need to scan barcodes and QR codes in their apps, this package is a useful resource. It supports React Native's new Fabric Native architecture and was created in Kotlin and Objective-C.

With this package, users can quickly and easily scan barcodes and QR codes with their device's camera. Using this package, several types of codes can be scanned, and it is simple to use.

If you want to provide your React Native app the ability to read barcodes and QR codes, you should definitely give this package some thought.

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

Here is an example of basic usage:

```js
import React, { useEffect, useState } from 'react';
import {
  Alert,
  Platform,
  useWindowDimensions,
  Text,
  SafeAreaView
} from 'react-native';

import { request, PERMISSIONS, openSettings, RESULTS } from 'react-native-permissions';
import { ReactNativeScannerView } from "@pushpendersingh/react-native-scanner";

export default function App() {

  const { height, width } = useWindowDimensions();
  const [isCameraPermissionGranted, setIsCameraPermissionGranted] = useState(false);

  useEffect(() => {
    checkCameraPermission();
  }, []);

  const checkCameraPermission = async () => {
    request(Platform.OS === 'ios' ? PERMISSIONS.IOS.CAMERA : PERMISSIONS.ANDROID.CAMERA)
      .then(async (result: any) => {
        switch (result) {
          case RESULTS.UNAVAILABLE:
            // console.log('This feature is not available (on this device / in this context)');
            break;
          case RESULTS.DENIED:
            Alert.alert("Permission Denied", "You need to grant camera permission first");
            openSettings();
            break;
          case RESULTS.GRANTED:
            setIsCameraPermissionGranted(true);
            break;
          case RESULTS.BLOCKED:
            Alert.alert("Permission Blocked", "You need to grant camera permission first");
            openSettings();
            break;
        }
      })
  };

  if (isCameraPermissionGranted) {
    return (
      <SafeAreaView style={{ flex: 1 }}>
        <ReactNativeScannerView
          style={{ height, width }}
          onQrScanned={(value: any) => {
            console.log(value.nativeEvent);
          }}
        />
      </SafeAreaView>
    );
  } else {
    return (
      <Text style={{ fontSize: 30, color: 'red' }}>
        You need to grant camera permission first
      </Text>
    );
  }
}
```

## Props

#### `onQrScanned` (required)

propType: `func.isRequired`
default: `(e) => (console.log('QR code scanned!', e))`

In the event that a QR code or barcode is detected in the camera's view, this specified method will be called.

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

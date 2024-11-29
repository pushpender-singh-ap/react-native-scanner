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

  // Pause the camera after barcode / QR code is scanned
  const pauseScanning = () => {
    if (scannerRef?.current) {
      Commands.pauseScanning(scannerRef?.current);
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

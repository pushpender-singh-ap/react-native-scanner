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
            pauseAfterCapture={false} // Pause the scanner after barcode / QR code is scanned
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

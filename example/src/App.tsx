import { useEffect, useState } from 'react';
import {
  Alert,
  Platform,
  Text,
  Button,
  View,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';

import BarcodeScanner, {
  type BarcodeResult,
  CameraView,
} from '@pushpendersingh/react-native-scanner';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function App() {
  const [isCameraPermissionGranted, setIsCameraPermissionGranted] =
    useState(false);
  const [isScanning, setIsScanning] = useState(false);
  const [scannedData, setScannedData] = useState<BarcodeResult | null>(null);
  const [flashEnabled, setFlashEnabled] = useState(false);
  const [scanHistory, setScanHistory] = useState<BarcodeResult[]>([]);

  useEffect(() => {
    checkCameraPermission();
    return () => {
      // Cleanup on unmount
      if (isScanning) {
        BarcodeScanner.stopScanning();
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleBarcodeScanned = (result: BarcodeResult) => {
    console.log('Barcode / QR Code scanned:', result.data, result.type);
    setScannedData(result);
    setScanHistory((prev) => [result, ...prev.slice(0, 9)]); // Keep last 10 scans
  };

  const startScanning = async () => {
    try {
      await BarcodeScanner.startScanning(handleBarcodeScanned);
      setIsScanning(true);
      console.log('Scanning started');
    } catch (error: any) {
      console.error('Error starting scanner:', error);
      Alert.alert('Error', error.message || 'Failed to start scanning');
    }
  };

  const stopScanning = async () => {
    try {
      await BarcodeScanner.stopScanning();
      setIsScanning(false);
      console.log('Scanning stopped');
    } catch (error: any) {
      console.error('Error stopping scanner:', error);
    }
  };

  const toggleFlashlight = async () => {
    try {
      if (flashEnabled) {
        await BarcodeScanner.disableFlashlight();
        setFlashEnabled(false);
        console.log('Flashlight disabled');
      } else {
        await BarcodeScanner.enableFlashlight();
        setFlashEnabled(true);
        console.log('Flashlight enabled');
      }
    } catch (error: any) {
      console.error('Error toggling flashlight:', error);
      Alert.alert('Error', 'Could not toggle flashlight');
    }
  };

  const releaseCamera = async () => {
    try {
      await BarcodeScanner.releaseCamera();
      setIsScanning(false);
      setFlashEnabled(false);
      console.log('Camera released');
    } catch (error: any) {
      console.error('Error releasing camera:', error);
    }
  };

  const clearHistory = () => {
    setScanHistory([]);
    setScannedData(null);
  };

  const checkCameraPermission = async () => {
    try {
      // Check if permission is already granted
      const hasPermission = await BarcodeScanner.hasCameraPermission();

      if (hasPermission) {
        setIsCameraPermissionGranted(true);
        return;
      }

      // Request permission if not granted
      const granted = await BarcodeScanner.requestCameraPermission();

      if (granted) {
        setIsCameraPermissionGranted(true);
        Alert.alert('Success', 'Camera permission granted!');
      } else {
        setIsCameraPermissionGranted(false);
        Alert.alert(
          'Permission Denied',
          'Camera permission is required to scan barcodes and QR codes.',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Try Again', onPress: checkCameraPermission },
          ]
        );
      }
    } catch (error: any) {
      console.error('Error checking camera permission:', error);
      Alert.alert('Error', 'Failed to check camera permission');
    }
  };

  if (!isCameraPermissionGranted) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.permissionContainer}>
          <Text style={styles.permissionText}>
            📷 Camera Permission Required
          </Text>
          <Text style={styles.permissionSubtext}>
            This app needs camera access to scan barcodes and QR codes.
          </Text>
          <Button
            title="Grant Permission"
            onPress={checkCameraPermission}
            color="#007AFF"
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Barcode Scanner</Text>
        <Text style={styles.headerSubtitle}>
          {isScanning ? '📸 Scanning...' : '⏸️ Ready to scan'}
        </Text>
      </View>

      {/* Camera Preview */}
      {isScanning && (
        <View style={styles.cameraContainer}>
          <CameraView style={styles.camera} />
          <View style={styles.scannerOverlay}>
            <View style={styles.scannerFrame} />
          </View>
        </View>
      )}

      <View style={styles.controls}>
        <TouchableOpacity
          style={[styles.button, isScanning && styles.buttonActive]}
          onPress={isScanning ? stopScanning : startScanning}
        >
          <Text style={styles.buttonText}>
            {isScanning ? '⏹ Stop Scanning' : '▶️ Start Scanning'}
          </Text>
        </TouchableOpacity>

        {isScanning && (
          <TouchableOpacity
            style={[styles.button, flashEnabled && styles.buttonActive]}
            onPress={toggleFlashlight}
          >
            <Text style={styles.buttonText}>
              {flashEnabled ? '🔦 Flash ON' : '🔦 Flash OFF'}
            </Text>
          </TouchableOpacity>
        )}

        <TouchableOpacity
          style={styles.buttonSecondary}
          onPress={releaseCamera}
        >
          <Text style={styles.buttonSecondaryText}>🔓 Release Camera</Text>
        </TouchableOpacity>
      </View>

      {scannedData && (
        <View style={styles.result}>
          <View style={styles.resultHeader}>
            <Text style={styles.resultTitle}>✅ Last Scanned</Text>
            <TouchableOpacity onPress={clearHistory}>
              <Text style={styles.clearButton}>Clear</Text>
            </TouchableOpacity>
          </View>
          <View style={styles.resultContent}>
            <View style={styles.resultRow}>
              <Text style={styles.resultLabel}>Type:</Text>
              <Text style={styles.resultValue}>{scannedData.type}</Text>
            </View>
            <View style={styles.resultRow}>
              <Text style={styles.resultLabel}>Data:</Text>
              <Text style={styles.resultValue} numberOfLines={3}>
                {scannedData.data}
              </Text>
            </View>
            {scannedData.bounds && (
              <View style={styles.resultRow}>
                <Text style={styles.resultLabel}>Size:</Text>
                <Text style={styles.resultValue}>
                  {scannedData.bounds.width.toFixed(0)} x{' '}
                  {scannedData.bounds.height.toFixed(0)}
                </Text>
              </View>
            )}
          </View>
        </View>
      )}

      {scanHistory.length > 0 && (
        <View style={styles.history}>
          <Text style={styles.historyTitle}>📋 Scan History</Text>
          <ScrollView style={styles.historyList}>
            {scanHistory.map((item, index) => (
              <View key={index} style={styles.historyItem}>
                <Text style={styles.historyType}>{item.type}</Text>
                <Text style={styles.historyData} numberOfLines={1}>
                  {item.data}
                </Text>
              </View>
            ))}
          </ScrollView>
        </View>
      )}

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          💡 Point camera at barcode or QR code
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  // Camera Preview
  cameraContainer: {
    height: 400,
    backgroundColor: '#000',
    margin: 16,
    marginTop: 0,
    borderRadius: 12,
    overflow: 'hidden',
    position: 'relative',
  },
  camera: {
    flex: 1,
  },
  scannerOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scannerFrame: {
    width: 250,
    height: 250,
    borderWidth: 2,
    borderColor: '#00FF00',
    borderRadius: 12,
    backgroundColor: 'transparent',
  },
  // Permission Screen
  permissionContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  permissionText: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 12,
    textAlign: 'center',
  },
  permissionSubtext: {
    fontSize: 16,
    color: '#666',
    marginBottom: 24,
    textAlign: 'center',
  },
  // Header
  header: {
    backgroundColor: '#007AFF',
    padding: 20,
    paddingTop: Platform.OS === 'ios' ? 10 : 20,
  },
  headerTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: 'white',
    textAlign: 'center',
  },
  headerSubtitle: {
    fontSize: 16,
    color: 'white',
    textAlign: 'center',
    marginTop: 8,
    opacity: 0.9,
  },
  // Controls
  controls: {
    padding: 16,
    gap: 12,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonActive: {
    backgroundColor: '#34C759',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  buttonSecondary: {
    backgroundColor: '#FF3B30',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  buttonSecondaryText: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  // Result
  result: {
    margin: 16,
    padding: 16,
    backgroundColor: 'white',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  resultHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  resultTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  clearButton: {
    color: '#007AFF',
    fontSize: 14,
    fontWeight: '600',
  },
  resultContent: {
    gap: 8,
  },
  resultRow: {
    flexDirection: 'row',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  resultLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    width: 60,
  },
  resultValue: {
    fontSize: 14,
    color: '#333',
    flex: 1,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  resultText: {
    fontSize: 16,
    marginVertical: 4,
  },
  // History
  history: {
    margin: 16,
    marginTop: 0,
    backgroundColor: 'white',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    maxHeight: 200,
  },
  historyTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 12,
    color: '#333',
  },
  historyList: {
    maxHeight: 150,
  },
  historyItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  historyType: {
    fontSize: 12,
    fontWeight: '600',
    color: '#007AFF',
    width: 100,
  },
  historyData: {
    fontSize: 12,
    color: '#666',
    flex: 1,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  // Footer
  footer: {
    padding: 16,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
});

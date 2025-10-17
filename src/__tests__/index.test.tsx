import { BarcodeScanner, type BarcodeResult, type BarcodeType } from '../index';
import NativeReactNativeScanner from '../NativeReactNativeScanner';

// Mock the native module
jest.mock('../NativeReactNativeScanner', () => ({
  onBarcodeScanned: jest.fn(),
  startScanning: jest.fn(),
  stopScanning: jest.fn(),
  enableFlashlight: jest.fn(),
  disableFlashlight: jest.fn(),
  releaseCamera: jest.fn(),
  hasCameraPermission: jest.fn(),
  requestCameraPermission: jest.fn(),
}));

describe('BarcodeScanner', () => {
  let mockRemoveListener: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();
    mockRemoveListener = jest.fn();
    // Reset the static listener property
    (BarcodeScanner as any).listener = null;
  });

  // Test 1: Start scanning with callback
  test('should start scanning and set up barcode listener', async () => {
    const mockCallback = jest.fn();
    const mockBarcodeResult: BarcodeResult = {
      data: 'QR_CODE_DATA',
      type: 'QR_CODE' as BarcodeType,
    };
    const mockListener = { remove: mockRemoveListener };

    (NativeReactNativeScanner.onBarcodeScanned as jest.Mock).mockReturnValue(
      mockListener
    );
    (NativeReactNativeScanner.startScanning as jest.Mock).mockResolvedValue(
      undefined
    );

    await BarcodeScanner.startScanning(mockCallback);

    expect(NativeReactNativeScanner.onBarcodeScanned).toHaveBeenCalled();
    expect(NativeReactNativeScanner.startScanning).toHaveBeenCalled();

    // Simulate barcode detection
    const eventCallback = (
      NativeReactNativeScanner.onBarcodeScanned as jest.Mock
    ).mock.calls[0][0];
    eventCallback(mockBarcodeResult);

    expect(mockCallback).toHaveBeenCalledWith(mockBarcodeResult);
  });

  // Test 2: Stop scanning
  test('should stop scanning and remove listener', async () => {
    const mockCallback = jest.fn();
    const mockListener = { remove: mockRemoveListener };

    (NativeReactNativeScanner.onBarcodeScanned as jest.Mock).mockReturnValue(
      mockListener
    );
    (NativeReactNativeScanner.startScanning as jest.Mock).mockResolvedValue(
      undefined
    );

    await BarcodeScanner.startScanning(mockCallback);
    await BarcodeScanner.stopScanning();

    expect(mockRemoveListener).toHaveBeenCalled();
    expect(NativeReactNativeScanner.stopScanning).toHaveBeenCalled();
  });

  // Test 3: Enable flashlight
  test('should enable flashlight', async () => {
    await BarcodeScanner.enableFlashlight();

    expect(NativeReactNativeScanner.enableFlashlight).toHaveBeenCalled();
  });

  // Test 4: Disable flashlight
  test('should disable flashlight', async () => {
    await BarcodeScanner.disableFlashlight();

    expect(NativeReactNativeScanner.disableFlashlight).toHaveBeenCalled();
  });

  // Test 5: Check camera permission
  test('should check camera permission', async () => {
    (
      NativeReactNativeScanner.hasCameraPermission as jest.Mock
    ).mockResolvedValue(true);

    const hasPermission = await BarcodeScanner.hasCameraPermission();

    expect(hasPermission).toBe(true);
    expect(NativeReactNativeScanner.hasCameraPermission).toHaveBeenCalled();
  });

  // Test 6: Request camera permission
  test('should request camera permission', async () => {
    (
      NativeReactNativeScanner.requestCameraPermission as jest.Mock
    ).mockResolvedValue(true);

    const permissionGranted = await BarcodeScanner.requestCameraPermission();

    expect(permissionGranted).toBe(true);
    expect(NativeReactNativeScanner.requestCameraPermission).toHaveBeenCalled();
  });

  // Test 7: Release camera
  test('should release camera and clean up listener', async () => {
    const mockCallback = jest.fn();
    const mockListener = { remove: mockRemoveListener };

    (NativeReactNativeScanner.onBarcodeScanned as jest.Mock).mockReturnValue(
      mockListener
    );
    (NativeReactNativeScanner.startScanning as jest.Mock).mockResolvedValue(
      undefined
    );

    await BarcodeScanner.startScanning(mockCallback);
    await BarcodeScanner.releaseCamera();

    expect(mockRemoveListener).toHaveBeenCalled();
    expect(NativeReactNativeScanner.releaseCamera).toHaveBeenCalled();
  });

  // Test 8: Handle multiple barcode detections
  test('should handle multiple barcode detections', async () => {
    const mockCallback = jest.fn();
    const mockListener = { remove: mockRemoveListener };

    (NativeReactNativeScanner.onBarcodeScanned as jest.Mock).mockReturnValue(
      mockListener
    );
    (NativeReactNativeScanner.startScanning as jest.Mock).mockResolvedValue(
      undefined
    );

    await BarcodeScanner.startScanning(mockCallback);

    const eventCallback = (
      NativeReactNativeScanner.onBarcodeScanned as jest.Mock
    ).mock.calls[0][0];

    // First barcode
    const firstBarcode: BarcodeResult = {
      data: 'BARCODE_1',
      type: 'QR_CODE' as BarcodeType,
    };
    eventCallback(firstBarcode);

    // Second barcode
    const secondBarcode: BarcodeResult = {
      data: 'BARCODE_2',
      type: 'CODE_128' as BarcodeType,
    };
    eventCallback(secondBarcode);

    expect(mockCallback).toHaveBeenCalledTimes(2);
    expect(mockCallback).toHaveBeenNthCalledWith(1, firstBarcode);
    expect(mockCallback).toHaveBeenNthCalledWith(2, secondBarcode);
  });

  // Test 9: Handle different barcode types
  test('should handle different barcode types', async () => {
    const mockCallback = jest.fn();
    const mockListener = { remove: mockRemoveListener };

    (NativeReactNativeScanner.onBarcodeScanned as jest.Mock).mockReturnValue(
      mockListener
    );
    (NativeReactNativeScanner.startScanning as jest.Mock).mockResolvedValue(
      undefined
    );

    await BarcodeScanner.startScanning(mockCallback);

    const eventCallback = (
      NativeReactNativeScanner.onBarcodeScanned as jest.Mock
    ).mock.calls[0][0];

    const barcodeTypes: BarcodeType[] = [
      'QR_CODE',
      'CODE_128',
      'EAN_13',
      'PDF417',
    ];

    barcodeTypes.forEach((type, index) => {
      const barcode: BarcodeResult = {
        data: `DATA_${type}`,
        type: type,
      };
      eventCallback(barcode);
      expect(mockCallback).toHaveBeenNthCalledWith(index + 1, barcode);
    });
  });

  // Test 10: Request camera permission denied
  test('should handle camera permission denied', async () => {
    (
      NativeReactNativeScanner.requestCameraPermission as jest.Mock
    ).mockResolvedValue(false);

    const permissionGranted = await BarcodeScanner.requestCameraPermission();

    expect(permissionGranted).toBe(false);
    expect(NativeReactNativeScanner.requestCameraPermission).toHaveBeenCalled();
  });
});

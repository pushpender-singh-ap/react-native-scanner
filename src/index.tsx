import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import NativeReactNativeScanner from './NativeReactNativeScanner';

const LINKING_ERROR =
  `The package '@pushpendersingh/react-native-scanner' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- Run 'pod install'\n", default: '' }) +
  '- Rebuild the app';

const isTurboModuleEnabled = global.__turboModuleProxy != null;

const ReactNativeScannerModule = isTurboModuleEnabled
  ? NativeReactNativeScanner
  : NativeModules.ReactNativeScanner;

if (!ReactNativeScannerModule) {
  throw new Error(LINKING_ERROR);
}

const eventEmitter = new NativeEventEmitter(ReactNativeScannerModule);

// Types
export type BarcodeType =
  | 'QR_CODE'
  | 'AZTEC'
  | 'CODE_128'
  | 'CODE_39'
  | 'CODE_93'
  | 'CODABAR'
  | 'DATA_MATRIX'
  | 'EAN_13'
  | 'EAN_8'
  | 'ITF'
  | 'PDF417'
  | 'UPC_A'
  | 'UPC_E'
  | 'UNKNOWN';

export interface BarcodeResult {
  data: string;
  type: BarcodeType;
  bounds?: {
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

export type BarcodeScannerCallback = (result: BarcodeResult) => void;

// Scanner API
export class BarcodeScanner {
  private static listener: any = null;

  /**
   * Start scanning for barcodes
   * @param callback Function to call when a barcode is detected
   * @returns Promise that resolves when scanning starts
   */
  static async startScanning(callback: BarcodeScannerCallback): Promise<void> {
    // Remove any existing listener first
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }

    // Add new listener BEFORE starting scanning
    this.listener = eventEmitter.addListener(
      'onBarcodeScanned',
      (result: BarcodeResult) => {
        console.log('📱 Event received in JS:', result);
        callback(result);
      }
    );

    // Small delay to ensure listener is fully registered
    await new Promise((resolve) => setTimeout(resolve, 10));

    // Start scanning
    return ReactNativeScannerModule.startScanning();
  }

  /**
   * Stop scanning for barcodes
   * @returns Promise that resolves when scanning stops
   */
  static async stopScanning(): Promise<void> {
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }
    return ReactNativeScannerModule.stopScanning();
  }

  /**
   * Enable device flashlight/torch
   * @returns Promise that resolves when flashlight is enabled
   */
  static async enableFlashlight(): Promise<void> {
    return ReactNativeScannerModule.enableFlashlight();
  }

  /**
   * Disable device flashlight/torch
   * @returns Promise that resolves when flashlight is disabled
   */
  static async disableFlashlight(): Promise<void> {
    return ReactNativeScannerModule.disableFlashlight();
  }

  /**
   * Release camera resources
   * @returns Promise that resolves when camera is released
   */
  static async releaseCamera(): Promise<void> {
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }
    return ReactNativeScannerModule.releaseCamera();
  }

  /**
   * Check if camera permission is granted
   * @returns Promise that resolves with permission status
   */
  static async hasCameraPermission(): Promise<boolean> {
    return ReactNativeScannerModule.hasCameraPermission();
  }

  /**
   * Request camera permission
   * @returns Promise that resolves with whether permission was granted
   */
  static async requestCameraPermission(): Promise<boolean> {
    return ReactNativeScannerModule.requestCameraPermission();
  }
}

// Export camera view
export { CameraView } from './CameraView';
export type { CameraViewProps } from './CameraView';

// Export default
export default BarcodeScanner;

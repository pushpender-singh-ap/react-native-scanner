import NativeReactNativeScanner from './NativeReactNativeScanner';

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

  static async startScanning(callback: BarcodeScannerCallback): Promise<void> {
    // Remove existing listener
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }

    this.listener = NativeReactNativeScanner.onBarcodeScanned((event) => {
      callback(event as BarcodeResult);
    });

    // Start scanning
    return NativeReactNativeScanner.startScanning();
  }

  static async stopScanning(): Promise<void> {
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }
    return NativeReactNativeScanner.stopScanning();
  }

  static async enableFlashlight(): Promise<void> {
    return NativeReactNativeScanner.enableFlashlight();
  }

  static async disableFlashlight(): Promise<void> {
    return NativeReactNativeScanner.disableFlashlight();
  }

  static async releaseCamera(): Promise<void> {
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }
    return NativeReactNativeScanner.releaseCamera();
  }

  static async hasCameraPermission(): Promise<boolean> {
    return NativeReactNativeScanner.hasCameraPermission();
  }

  static async requestCameraPermission(): Promise<boolean> {
    return NativeReactNativeScanner.requestCameraPermission();
  }
}

// Export camera view
export { CameraView } from './CameraView';
export type { CameraViewProps } from './CameraView';

// Export default
export default BarcodeScanner;

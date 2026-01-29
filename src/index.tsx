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

export type BarcodeScannerCallback = (results: BarcodeResult[]) => void;

// Scanner API
export class BarcodeScanner {
  private static listener: any = null;

  // Allowed URI schemes for scanImage
  private static readonly ALLOWED_SCHEMES = ['file://', 'content://', 'ph://'];

  static async startScanning(callback: BarcodeScannerCallback): Promise<void> {
    // Remove existing listener
    if (this.listener) {
      this.listener.remove();
      this.listener = null;
    }

    this.listener = NativeReactNativeScanner.onBarcodeScanned((event) => {
      callback(event as unknown as BarcodeResult[]);
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

  static async scanImage(imageUri: string): Promise<BarcodeResult[]> {
    // Validate input type
    if (!imageUri || typeof imageUri !== 'string') {
      throw new Error('Invalid image URI: must be a non-empty string');
    }

    // Block path traversal
    if (imageUri.includes('..')) {
      throw new Error('Invalid image URI: path traversal not allowed');
    }

    // Validate scheme (allow file://, content://, ph://)
    const hasValidScheme = this.ALLOWED_SCHEMES.some((scheme) =>
      imageUri.startsWith(scheme)
    );
    if (!hasValidScheme) {
      throw new Error(
        `Invalid image URI: unsupported scheme. Use: ${this.ALLOWED_SCHEMES.join(', ')}`
      );
    }

    return NativeReactNativeScanner.scanImage(imageUri) as unknown as Promise<
      BarcodeResult[]
    >;
  }
}

// Export camera view
export { CameraView } from './CameraView';
export type { CameraViewProps } from './CameraView';

// Export default
export default BarcodeScanner;

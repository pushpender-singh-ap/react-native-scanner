import type { CodegenTypes, TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export type BarcodeScannedEvent = {
  data: string;
  type: string;
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
};

export interface Spec extends TurboModule {
  // Start scanning - results will be emitted via events
  startScanning(): Promise<void>;

  // Stop scanning
  stopScanning(): Promise<void>;

  // Enable flashlight/torch
  enableFlashlight(): Promise<void>;

  // Disable flashlight/torch
  disableFlashlight(): Promise<void>;

  // Release camera resources
  releaseCamera(): Promise<void>;

  // Check if camera permission is granted
  hasCameraPermission(): Promise<boolean>;

  // Request camera permission
  requestCameraPermission(): Promise<boolean>;

  readonly onBarcodeScanned: CodegenTypes.EventEmitter<BarcodeScannedEvent>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('ReactNativeScanner');

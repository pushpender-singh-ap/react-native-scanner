import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

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

  // Add event emitter support
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('ReactNativeScanner');

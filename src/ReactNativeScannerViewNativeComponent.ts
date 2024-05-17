import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import type { ViewProps, HostComponent } from 'react-native';
import type { DirectEventHandler } from 'react-native/Libraries/Types/CodegenTypes';
import codegenNativeCommands from 'react-native/Libraries/Utilities/codegenNativeCommands';

type Event = Readonly<{
  value: string;
}>;

interface NativeCommands {
  enableFlashlight: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>
  ) => Promise<void>;
  disableFlashlight: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>
  ) => Promise<void>;
  releaseCamera: (
    viewRef: React.ElementRef<HostComponent<NativeProps>>
  ) => Promise<void>;
}

interface NativeProps extends ViewProps {
  onQrScanned?: DirectEventHandler<Event>; // Event name should start with "on"
}

export const Commands = codegenNativeCommands<NativeCommands>({
  supportedCommands: ['enableFlashlight', 'disableFlashlight', 'releaseCamera'],
});

export default codegenNativeComponent<NativeProps>('ReactNativeScannerView');

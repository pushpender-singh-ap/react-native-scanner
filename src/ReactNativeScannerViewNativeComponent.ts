import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import codegenNativeCommands from 'react-native/Libraries/Utilities/codegenNativeCommands';
import type { HostComponent, ViewProps } from 'react-native';
import type { DirectEventHandler, Int32, Double } from 'react-native/Libraries/Types/CodegenTypes';

type Event = Readonly<{
  bounds: Readonly<{
    width: Double,
    height: Double,
    origin: Readonly<{
      topLeft: Readonly<{ x: Double, y: Double }>;
      bottomLeft: Readonly<{ x: Double, y: Double }>;
      bottomRight: Readonly<{ x: Double, y: Double }>;
      topRight: Readonly<{ x: Double, y: Double }>;
    }>
  }>;
  type: string;
  data: string;
  target: Int32;
}>;

export interface NativeProps extends ViewProps {
  pauseAfterCapture?: boolean,
  onQrScanned?: DirectEventHandler<Event>; // Event name should start with "on"
}

interface NativeCommands {
  pausePreview: (viewRef: React.ElementRef<HostComponent<NativeProps>>) => void;
  resumePreview: (viewRef: React.ElementRef<HostComponent<NativeProps>>) => void;
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: [
    'pausePreview',
    'resumePreview'
  ],
});

export default codegenNativeComponent<NativeProps>(
  'ReactNativeScannerView', {}
) as HostComponent<NativeProps>;

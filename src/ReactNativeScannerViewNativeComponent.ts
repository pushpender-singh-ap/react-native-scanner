import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import type { ViewProps } from 'react-native';
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

interface NativeProps extends ViewProps {
  onQrScanned?: DirectEventHandler<Event>; // Event name should start with "on"
}

export default codegenNativeComponent<NativeProps>('ReactNativeScannerView');

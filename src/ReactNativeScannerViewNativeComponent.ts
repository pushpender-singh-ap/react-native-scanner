import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
import type { ViewProps } from 'react-native';
import type { DirectEventHandler } from 'react-native/Libraries/Types/CodegenTypes';

type Event = Readonly<{
  value: string;
}>;

interface NativeProps extends ViewProps {
  onQrScanned?: DirectEventHandler<Event>; // Event name should start with "on"
}

export default codegenNativeComponent<NativeProps>('ReactNativeScannerView');

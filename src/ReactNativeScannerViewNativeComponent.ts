import type { ViewProps, HostComponent } from 'react-native';
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';

export interface NativeProps extends ViewProps {
  // Add any custom props here if needed in the future
}

export default codegenNativeComponent<NativeProps>(
  'ReactNativeScannerView'
) as HostComponent<NativeProps>;

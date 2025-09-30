import React from 'react';
import { requireNativeComponent } from 'react-native';
import type { ViewProps } from 'react-native';

export interface CameraViewProps extends ViewProps {
  style?: ViewProps['style'];
}

const NativeCameraView = requireNativeComponent<CameraViewProps>(
  'ReactNativeScannerView'
);

export const CameraView = React.forwardRef<any, CameraViewProps>(
  (props, ref) => {
    return <NativeCameraView {...props} ref={ref} />;
  }
);

CameraView.displayName = 'CameraView';

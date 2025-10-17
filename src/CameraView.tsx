import React from 'react';
import type { ViewProps } from 'react-native';
import ReactNativeScannerViewNativeComponent from './ReactNativeScannerViewNativeComponent';

export interface CameraViewProps extends ViewProps {
  style?: ViewProps['style'];
}

export const CameraView = React.forwardRef<any, CameraViewProps>(
  (props, ref) => {
    return <ReactNativeScannerViewNativeComponent {...props} ref={ref} />;
  }
);

CameraView.displayName = 'CameraView';

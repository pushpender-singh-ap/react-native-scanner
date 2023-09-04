import {
  NativeSyntheticEvent,
  ViewProps,
  UIManagerStatic,
} from 'react-native';

type ScannerViewCommands =
  | 'pausePreview'
  | 'resumePreview';

interface RNScannerViewUIManager<Commands extends string> extends UIManagerStatic {
  getViewManagerConfig: (name: string) => {
    Commands: { [key in Commands]: number };
  };
}

export type ReactNativeScannerViewUIManager = RNScannerViewUIManager<ScannerViewCommands>;

export interface Point {
  x: number;
  y: number;
}

export interface Origin {
  topLeft: Point;
  bottomLeft: Point;
  bottomRight: Point;
  topRight: Point;
}

export interface Bounds {
  width: number;
  height: number;
  origin: Origin | Point[];
}

export interface ScannerViewNativeEvent {
  type: string;
  data: string;
  target: number;
}

export interface ScannerViewQRScan extends ScannerViewNativeEvent {
  bounds: Bounds;
}

export type ScannerViewQRScanEvent = NativeSyntheticEvent<ScannerViewQRScan>;

export interface ScannerViewProps extends ViewProps {
  pauseAfterCapture?: boolean;
  onQrScanned?: (event: ScannerViewQRScanEvent) => void;
}
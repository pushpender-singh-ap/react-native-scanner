#import <React/RCTEventEmitter.h>
#import <ReactNativeScannerSpec/ReactNativeScannerSpec.h>

@class CameraManager;

@interface ReactNativeScanner : RCTEventEmitter <NativeReactNativeScannerSpec>

@property (nonatomic, strong, readonly) CameraManager *cameraManager;

@end

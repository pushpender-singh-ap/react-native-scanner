#import <Foundation/Foundation.h>
#import <ReactNativeScannerSpec/ReactNativeScannerSpec.h>

@class CameraManager;

@interface ReactNativeScanner : NativeReactNativeScannerSpecBase <NativeReactNativeScannerSpec>

@property (nonatomic, strong, readonly) CameraManager *cameraManager;

@end



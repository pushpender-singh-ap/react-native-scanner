//
//  CameraViewManager.mm
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

#import "ReactNativeScanner.h"
#import <AVFoundation/AVFoundation.h>
#import <React/RCTViewManager.h>

// Import the Swift bridging header
#if __has_include(<ReactNativeScanner/ReactNativeScanner-Swift.h>)
// For dynamic frameworks or when installed as a framework
#import <ReactNativeScanner/ReactNativeScanner-Swift.h>
#else
// For static libraries or when included directly in the project
#import "ReactNativeScanner-Swift.h"
#endif

@interface ReactNativeScannerViewManager : RCTViewManager
@end

@implementation ReactNativeScannerViewManager

RCT_EXPORT_MODULE(ReactNativeScannerView)

- (UIView *)view {
  CameraView *cameraView = [[CameraView alloc] init];

  ReactNativeScanner *scannerModule =
      [self.bridge moduleForClass:[ReactNativeScanner class]];
  if (scannerModule && scannerModule.cameraManager) {
    [cameraView setCameraManager:scannerModule.cameraManager];
  }

  return cameraView;
}

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

@end

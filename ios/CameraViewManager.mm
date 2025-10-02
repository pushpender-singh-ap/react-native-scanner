//
//  CameraViewManager.mm
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

#import <React/RCTViewManager.h>
#import <React/RCTBridge.h>
#import <AVFoundation/AVFoundation.h>
#import "ReactNativeScanner.h"

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

- (UIView *)view
{
    CameraView *cameraView = [[CameraView alloc] init];
    
    // Bind immediately on the current thread (main queue)
    // Since requiresMainQueueSetup returns YES, we're already on main queue
    ReactNativeScanner *scannerModule = [self.bridge moduleForClass:[ReactNativeScanner class]];
    if (scannerModule && scannerModule.cameraManager) {
        [cameraView setCameraManager:scannerModule.cameraManager];
        NSLog(@"✅ Camera manager bound to view");
    } else {
        NSLog(@"⚠️ Scanner module or camera manager not available");
    }
    
    return cameraView;
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

@end

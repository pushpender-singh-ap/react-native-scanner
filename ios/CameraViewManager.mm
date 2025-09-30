//
//  CameraViewManager.mm
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

#import <React/RCTViewManager.h>
#import <React/RCTBridge.h>
#import <AVFoundation/AVFoundation.h>
#import "ReactNativeScanner-Swift.h"
#import "ReactNativeScanner.h"

@interface ReactNativeScannerViewManager : RCTViewManager
@end

@implementation ReactNativeScannerViewManager

RCT_EXPORT_MODULE(ReactNativeScannerView)

- (UIView *)view
{
    CameraView *cameraView = [[CameraView alloc] init];
    
    // Get the camera manager from the module and bind it to the view
    dispatch_async(dispatch_get_main_queue(), ^{
        ReactNativeScanner *scannerModule = [self.bridge moduleForClass:[ReactNativeScanner class]];
        if (scannerModule && scannerModule.cameraManager) {
            [cameraView setCameraManager:scannerModule.cameraManager];
        }
    });
    
    return cameraView;
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

@end

#import <React/RCTViewManager.h>
#import <React/RCTUIManager.h>
#import "RCTBridge.h"
#import <React/RCTLog.h>

@interface ReactNativeScannerViewManager : RCTViewManager
@end

@implementation ReactNativeScannerViewManager

RCT_EXPORT_MODULE(ReactNativeScannerView)

RCT_EXPORT_VIEW_PROPERTY(onQrScanned, RCTDirectEventBlock)
RCT_EXPORT_VIEW_PROPERTY(pauseAfterCapture, BOOL)

@end

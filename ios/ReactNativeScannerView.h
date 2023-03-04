// This guard prevent this file to be compiled in the old architecture.
#ifdef RCT_NEW_ARCH_ENABLED
#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>

#ifndef ReactNativeScannerViewNativeComponent_h
#define ReactNativeScannerViewNativeComponent_h

NS_ASSUME_NONNULL_BEGIN

@interface ReactNativeScannerView : RCTViewComponentView <AVCaptureMetadataOutputObjectsDelegate>
@end

NS_ASSUME_NONNULL_END

#endif /* ReactNativeScannerViewNativeComponent_h */
#endif /* RCT_NEW_ARCH_ENABLED */

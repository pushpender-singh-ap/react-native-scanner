#import "ReactNativeScanner.h"
#import <AVFoundation/AVFoundation.h>

#if __has_include("ReactNativeScanner-Swift.h")
#import "ReactNativeScanner-Swift.h"
#else
#import <ReactNativeScanner/ReactNativeScanner-Swift.h>
#endif

@interface ReactNativeScanner ()
@property(nonatomic, strong) CameraManager *cameraManager;
@end

@implementation ReactNativeScanner

RCT_EXPORT_MODULE()

- (instancetype)init {
  if (self = [super init]) {
    _cameraManager = [[CameraManager alloc] init];
  }
  return self;
}

// Expose cameraManager for CameraViewManager
- (CameraManager *)cameraManager {
  return _cameraManager;
}

- (NSArray<NSString *> *)supportedEvents {
  return @[ @"onBarcodeScanned" ];
}

- (void)startScanning:(RCTPromiseResolveBlock)resolve
               reject:(RCTPromiseRejectBlock)reject {
  if (![_cameraManager hasCameraPermission]) {
    reject(@"PERMISSION_DENIED", @"Camera permission not granted", nil);
    return;
  }

  NSError *error = nil;
  @try {
    [_cameraManager
        startScanningWithCallback:^(NSDictionary *result) {
          [self sendEventWithName:@"onBarcodeScanned" body:result];
        }
                            error:&error];
    if (error) {
      reject(@"START_SCANNING_ERROR", error.localizedDescription, error);
    } else {
      resolve(nil);
    }
  } @catch (NSException *exception) {
    reject(@"START_SCANNING_ERROR", exception.reason, nil);
  }
}

- (void)stopScanning:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject {
  @try {
    [_cameraManager stopScanning];
    resolve(nil);
  } @catch (NSException *exception) {
    reject(@"STOP_SCANNING_ERROR", exception.reason, nil);
  }
}

- (void)enableFlashlight:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject {
  @try {
    [_cameraManager enableFlashlight];
    resolve(nil);
  } @catch (NSException *exception) {
    reject(@"FLASHLIGHT_ERROR", exception.reason, nil);
  }
}

- (void)disableFlashlight:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject {
  @try {
    [_cameraManager disableFlashlight];
    resolve(nil);
  } @catch (NSException *exception) {
    reject(@"FLASHLIGHT_ERROR", exception.reason, nil);
  }
}

- (void)releaseCamera:(RCTPromiseResolveBlock)resolve
               reject:(RCTPromiseRejectBlock)reject {
  @try {
    [_cameraManager releaseCamera];
    resolve(nil);
  } @catch (NSException *exception) {
    reject(@"RELEASE_CAMERA_ERROR", exception.reason, nil);
  }
}

- (void)hasCameraPermission:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject {
  BOOL hasPermission = [_cameraManager hasCameraPermission];
  resolve(@(hasPermission));
}

- (void)requestCameraPermission:(RCTPromiseResolveBlock)resolve
                         reject:(RCTPromiseRejectBlock)reject {
  [_cameraManager requestCameraPermissionWithCompletion:^(BOOL granted) {
    resolve(@(granted));
  }];
}

- (void)addListener:(NSString *)eventName {
  // Required for RCTEventEmitter
  // Keep track of listeners to avoid "no listeners" warning
  [super addListener:eventName];
}

- (void)removeListeners:(double)count {
  // Required for RCTEventEmitter
  [super removeListeners:count];
}

- (void)invalidate {
  // Capture strong reference to camera manager first to ensure it stays alive during cleanup
  CameraManager *cameraManager = _cameraManager;
  
  if (cameraManager) {
    [cameraManager releaseCamera];
  }
  
  [super invalidate];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativeReactNativeScannerSpecJSI>(
      params);
}

@end

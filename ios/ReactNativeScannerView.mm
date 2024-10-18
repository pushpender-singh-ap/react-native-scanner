#import "ReactNativeScannerView.h"

#import <react/renderer/components/RNReactNativeScannerViewSpec/ComponentDescriptors.h>
#import <react/renderer/components/RNReactNativeScannerViewSpec/EventEmitters.h>
#import <react/renderer/components/RNReactNativeScannerViewSpec/Props.h>
#import <react/renderer/components/RNReactNativeScannerViewSpec/RCTComponentViewHelpers.h>

#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

@interface ReactNativeScannerView () <RCTReactNativeScannerViewViewProtocol>
@end

@implementation ReactNativeScannerView {
    UIView * _view;
    
    AVCaptureSession *_session;
    AVCaptureDevice *_device;
    AVCaptureDeviceInput *_input;
    AVCaptureMetadataOutput *_output;
    AVCaptureVideoPreviewLayer *_prevLayer;
    
    BOOL pauseAfterCapture;
    BOOL isActive;
}

+ (NSArray *)metadataObjectTypes
{
    return @[AVMetadataObjectTypeUPCECode,
             AVMetadataObjectTypeCode39Code,
             AVMetadataObjectTypeCode39Mod43Code,
             AVMetadataObjectTypeEAN13Code,
             AVMetadataObjectTypeEAN8Code,
             AVMetadataObjectTypeCode93Code,
             AVMetadataObjectTypeCode128Code,
             AVMetadataObjectTypePDF417Code,
             AVMetadataObjectTypeQRCode,
             AVMetadataObjectTypeAztecCode,
             AVMetadataObjectTypeDataMatrixCode];
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
    return concreteComponentDescriptorProvider<ReactNativeScannerViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
    if (self = [super initWithFrame:frame]) {
        static const auto defaultProps = std::make_shared<const ReactNativeScannerViewProps>();
        _props = defaultProps;
        
        _view = [[UIView alloc] initWithFrame:CGRectMake(0, 0, 500, 500)];
        _session = [[AVCaptureSession alloc] init];
        _device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
        
        NSError *error = nil;
        _input = [AVCaptureDeviceInput deviceInputWithDevice:_device error:&error];
        if (_input) {
            [_session addInput:_input];
        } else {
            NSLog(@"%@", [error localizedDescription]);
        }
        
        _output = [[AVCaptureMetadataOutput alloc] init];
        [_output setMetadataObjectsDelegate:self queue:dispatch_get_main_queue()];
        [_session addOutput:_output];
        
        _output.metadataObjectTypes = [ReactNativeScannerView metadataObjectTypes];
        
        _prevLayer = [AVCaptureVideoPreviewLayer layerWithSession:_session];
        _prevLayer.videoGravity = AVLayerVideoGravityResizeAspectFill;
        [_view.layer addSublayer:_prevLayer];

        // Create a dispatch queue.
        dispatch_queue_t sessionQueue = dispatch_queue_create("session queue", DISPATCH_QUEUE_SERIAL);

        // Use dispatch_async to call the startRunning method on the sessionQueue.
        dispatch_async(sessionQueue, ^{
            [self->_session startRunning];
        });

        self.contentView = _view;
    }
    
    return self;
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputMetadataObjects:(NSArray *)metadataObjects fromConnection:(AVCaptureConnection *)connection
{
    if (_eventEmitter == nullptr) {
        return;
    }
    
    NSMutableArray *validBarCodes = [[NSMutableArray alloc] init];
    NSArray *barCodeTypes = [ReactNativeScannerView metadataObjectTypes];
    
    for (AVMetadataObject *metadata in metadataObjects) {
        BOOL isValidCode = NO;
        for (NSString *type in barCodeTypes) {
            if ([metadata.type isEqualToString:type]) {
                isValidCode = YES;
                break;
            }
        }
        
        if (isValidCode == YES) {
            [validBarCodes addObject:metadata];
        }
    }
    
    // pauseAfterCapture:
    // * Pause AVCaptureSession for further processing, after valid barcodes found,
    // * Can be resumed back by calling resumePreview from the owner of the component
    if (pauseAfterCapture == YES && validBarCodes.count > 0) {
        [self pausePreview];
    }
    
    for (AVMetadataObject *metadata in validBarCodes) {
        AVMetadataMachineReadableCodeObject *barCodeObject = (AVMetadataMachineReadableCodeObject *)[_prevLayer transformedMetadataObjectForMetadataObject:(AVMetadataMachineReadableCodeObject *)metadata];
        CGRect highlightViewRect = barCodeObject.bounds;
        NSArray *corners = barCodeObject.corners;
        NSString *codeString = [(AVMetadataMachineReadableCodeObject *)metadata stringValue];
        
        CGPoint topLeft, bottomLeft, bottomRight, topRight = CGPointMake(0, 0);
        
        if (corners.count >= 0) {
            topLeft = [self mapObject: corners[0]];
        }
        
        if (corners.count >= 1) {
            bottomLeft = [self mapObject: corners[1]];
        }
        
        if (corners.count >= 2) {
            bottomRight = [self mapObject: corners[2]];
        }
        
        if (corners.count >= 3) {
            topRight = [self mapObject: corners[3]];
        }
        
        facebook::react::ReactNativeScannerViewEventEmitter::OnQrScannedBounds bounds = {
            .width = highlightViewRect.size.width,
            .height = highlightViewRect.size.height,
            .origin = {
                .topLeft = {.x = topLeft.x, .y = topLeft.y},
                .bottomLeft = {.x = bottomLeft.x, .y = bottomLeft.y},
                .bottomRight = {.x = bottomRight.x, .y = bottomRight.y},
                .topRight = {.x = topRight.x, .y = topRight.y}
            }
        };
        
        std::dynamic_pointer_cast<const facebook::react::ReactNativeScannerViewEventEmitter>(_eventEmitter)->onQrScanned(facebook::react::ReactNativeScannerViewEventEmitter::OnQrScanned{
            .bounds = bounds,
            .type = std::string([metadata.type UTF8String]),
            .data = std::string([codeString UTF8String]),
            .target = std::int32_t([codeString lengthOfBytesUsingEncoding:NSUTF8StringEncoding])
        });
    }
}

(void)releaseCamera {

    NSLog(@"%@", @"Release Camera");

    if (_session != nil) {
      // Stop the session
      [_session stopRunning];

      // Release the session, input, output, and preview layer
      _session = nil;
      _input = nil;
      _output = nil;
      _prevLayer = nil;

    }
}

- (void)enableFlashlight {
    if ([_device hasTorch] && [_device isTorchModeSupported:AVCaptureTorchModeOn]) {
        NSError *error = nil;
        if ([_device lockForConfiguration:&error]) {
            [_device setTorchMode:AVCaptureTorchModeOn];
            [_device unlockForConfiguration];
        } else {
            // Handle error
            NSLog(@"%@", [error localizedDescription]);
        }
    }
}

- (void)disableFlashlight {
    if ([_device hasTorch] && [_device isTorchModeSupported:AVCaptureTorchModeOff]) {
        NSError *error = nil;
        if ([_device lockForConfiguration:&error]) {
            [_device setTorchMode:AVCaptureTorchModeOff];
            [_device unlockForConfiguration];
        } else {
            // Handle error
            NSLog(@"%@", [error localizedDescription]);
        }
    }
}

- (CGPoint)mapObject:(NSDictionary *)object {
    if (object == nil) {
        return CGPointMake(0, 0);
    }
    
    return CGPointMake([[object objectForKey:@"X"] doubleValue], [[object objectForKey:@"Y"] doubleValue]);
}

- (void)setIsActive:(BOOL)active {
    isActive = active;

    // Enable/Disable Preview Layer
    if (isActive) {
        [self resumePreview];
    } else {
        [self pausePreview];
    }

    if (isActive == _session.isRunning) {
        return;
    }
    // Start/Stop session
    if (isActive) {
        [_session startRunning];
    } else {
        [_session stopRunning];
    }
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
    const auto &oldViewProps = *std::static_pointer_cast<ReactNativeScannerViewProps const>(_props);
    const auto &newViewProps = *std::static_pointer_cast<ReactNativeScannerViewProps const>(props);
    
    pauseAfterCapture = newViewProps.pauseAfterCapture;
    [self setIsActive:newViewProps.isActive];
    
    [super updateProps:props oldProps:oldProps];
}

- (void)handleCommand:(nonnull const NSString *)commandName args:(nonnull const NSArray *)args {
    RCTReactNativeScannerViewHandleCommand(self, commandName, args);
}

- (void)updateLayoutMetrics:(const facebook::react::LayoutMetrics &)layoutMetrics oldLayoutMetrics:(const facebook::react::LayoutMetrics &)oldLayoutMetrics{
    [super updateLayoutMetrics:layoutMetrics oldLayoutMetrics:oldLayoutMetrics];
    _prevLayer.frame = [_view.layer bounds];
}

- (void)pausePreview {
    if ([[_prevLayer connection] isEnabled]) {
        [[_prevLayer connection] setEnabled:NO];
    }
}

- (void)resumePreview {
    if (![[_prevLayer connection] isEnabled]) {
        [[_prevLayer connection] setEnabled:YES];
    }
}

- (void)startScanning {
    [self setIsActive:YES];
}

- (void)stopScanning {
    [self setIsActive:NO];
}

@end

Class<RCTComponentViewProtocol> ReactNativeScannerViewCls(void)
{
    return ReactNativeScannerView.class;
}


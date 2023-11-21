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
             AVMetadataObjectTypeAztecCode];
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
        [_session startRunning];
        
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
        BOOL isValidCode = false;
        for (NSString *type in barCodeTypes) {
            if ([metadata.type isEqualToString:type]) {
                isValidCode = true;
                break;
            }
        }
        
        if (isValidCode == true) {
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

- (CGPoint)mapObject:(NSDictionary *)object {
    if (object == nil) {
        return CGPointMake(0, 0);
    }
    
    return CGPointMake([[object objectForKey:@"X"] doubleValue], [[object objectForKey:@"Y"] doubleValue]);
}

- (void)checkIsActive {
    if (isActive == _session.isRunning) {
        return;
    }

    // Start/Stop session
    if (isActive) {
        [_session startRunning];
    } else {
        [captureSession stopRunning];
    }
  }

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
    const auto &oldViewProps = *std::static_pointer_cast<ReactNativeScannerViewProps const>(_props);
    const auto &newViewProps = *std::static_pointer_cast<ReactNativeScannerViewProps const>(props);
    
    pauseAfterCapture = newViewProps.pauseAfterCapture;

    if (isActive != newViewProps.isActive) {
        isActive = newViewProps.pauseAfterCapture;

        [self checkIsActive];
    }
    
    [super updateProps:props oldProps:oldProps];
}

- (void)updateLayoutMetrics:(const facebook::react::LayoutMetrics &)layoutMetrics oldLayoutMetrics:(const facebook::react::LayoutMetrics &)oldLayoutMetrics{
    [super updateLayoutMetrics:layoutMetrics oldLayoutMetrics:oldLayoutMetrics];
    _prevLayer.frame = [_view.layer bounds];
}

- (void)handleCommand:(nonnull const NSString *)commandName args:(nonnull const NSArray *)args {
    RCTReactNativeScannerViewHandleCommand(self, commandName, args);
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

@end

Class<RCTComponentViewProtocol> ReactNativeScannerViewCls(void)
{
    return ReactNativeScannerView.class;
}


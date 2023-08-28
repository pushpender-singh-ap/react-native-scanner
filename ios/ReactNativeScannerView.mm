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
        
        _output.metadataObjectTypes = [_output availableMetadataObjectTypes];
        
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
    
    NSArray *barCodeTypes = @[AVMetadataObjectTypeUPCECode, AVMetadataObjectTypeCode39Code, AVMetadataObjectTypeCode39Mod43Code,
                              AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code, AVMetadataObjectTypeCode93Code, AVMetadataObjectTypeCode128Code,
                              AVMetadataObjectTypePDF417Code, AVMetadataObjectTypeQRCode, AVMetadataObjectTypeAztecCode];
    
    for (AVMetadataObject *metadata in metadataObjects) {
        BOOL isValidCode = false;
        for (NSString *type in barCodeTypes) {
            if ([metadata.type isEqualToString:type]) {
                isValidCode = true;
                break;
            }
        }
        
        if (isValidCode == true)  {
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
}

- (CGPoint)mapObject:(NSDictionary *)object {
    if (object == nil) {
        return CGPointMake(0, 0);
    }
    
    return CGPointMake([[object objectForKey:@"X"] doubleValue], [[object objectForKey:@"Y"] doubleValue]);
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
    [super updateProps:props oldProps:oldProps];
}

- (void)updateLayoutMetrics:(const facebook::react::LayoutMetrics &)layoutMetrics oldLayoutMetrics:(const facebook::react::LayoutMetrics &)oldLayoutMetrics{
    [super updateLayoutMetrics:layoutMetrics oldLayoutMetrics:oldLayoutMetrics];
    _prevLayer.frame = [_view.layer bounds];
}

@end

Class<RCTComponentViewProtocol> ReactNativeScannerViewCls(void)
{
    return ReactNativeScannerView.class;
}


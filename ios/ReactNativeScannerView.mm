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
  CGRect highlightViewRect = CGRectZero;
  AVMetadataMachineReadableCodeObject *barCodeObject;
  NSString *detectionString = nil;
  NSArray *barCodeTypes = @[AVMetadataObjectTypeUPCECode, AVMetadataObjectTypeCode39Code, AVMetadataObjectTypeCode39Mod43Code,
                            AVMetadataObjectTypeEAN13Code, AVMetadataObjectTypeEAN8Code, AVMetadataObjectTypeCode93Code, AVMetadataObjectTypeCode128Code,
                            AVMetadataObjectTypePDF417Code, AVMetadataObjectTypeQRCode, AVMetadataObjectTypeAztecCode];

  for (AVMetadataObject *metadata in metadataObjects) {
    for (NSString *type in barCodeTypes) {
      if ([metadata.type isEqualToString:type]) {
        barCodeObject = (AVMetadataMachineReadableCodeObject *)[_prevLayer transformedMetadataObjectForMetadataObject:(AVMetadataMachineReadableCodeObject *)metadata];
        highlightViewRect = barCodeObject.bounds;
        detectionString = [(AVMetadataMachineReadableCodeObject *)metadata stringValue];
        break;
      }
    }
    if (detectionString != nil)  {
      if (_eventEmitter != nullptr) {
        std::dynamic_pointer_cast<const facebook::react::ReactNativeScannerViewEventEmitter>(_eventEmitter)->onQrScanned(facebook::react::ReactNativeScannerViewEventEmitter::OnQrScanned{
        .value = std::string([detectionString UTF8String])
        });
      }
    }
  }
}

- (void)releaseCamera {
    
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

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
    [super updateProps:props oldProps:oldProps];
}

- (void)updateLayoutMetrics:(const facebook::react::LayoutMetrics &)layoutMetrics oldLayoutMetrics:(const facebook::react::LayoutMetrics &)oldLayoutMetrics{
  [super updateLayoutMetrics:layoutMetrics oldLayoutMetrics:oldLayoutMetrics];
  _prevLayer.frame = [_view.layer bounds];
}

- (void)handleCommand:(nonnull const NSString *)commandName args:(nonnull const NSArray *)args {
    RCTReactNativeScannerViewHandleCommand(self, commandName, args);
}

@end

Class<RCTComponentViewProtocol> ReactNativeScannerViewCls(void)
{
    return ReactNativeScannerView.class;
}

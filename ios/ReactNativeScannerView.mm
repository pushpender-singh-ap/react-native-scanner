#import "ReactNativeScannerView.h"

#import <react/renderer/components/RNReactNativeScannerViewSpec/ComponentDescriptors.h>
#import <react/renderer/components/RNReactNativeScannerViewSpec/EventEmitters.h>
#import <react/renderer/components/RNReactNativeScannerViewSpec/Props.h>
#import <react/renderer/components/RNReactNativeScannerViewSpec/RCTComponentViewHelpers.h>

#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

@interface ReactNativeScannerView () <RCTReactNativeScannerViewViewProtocol>
@property (nonatomic, strong) dispatch_queue_t sessionQueue;
@end

@implementation ReactNativeScannerView {
    UIView * _view;
    AVCaptureSession *_session;
    AVCaptureDevice *_device;
    AVCaptureDeviceInput *_input;
    AVCaptureMetadataOutput *_output;
    AVCaptureVideoPreviewLayer *_prevLayer;
    CAShapeLayer *_boundingBoxLayer;
    
    BOOL pauseAfterCapture;
    BOOL isActive;
    BOOL showBox;
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

/**
 * Initializes a ReactNativeScannerView with the specified frame.
 *
 * This method sets up the session queue for handling scanner operations,
 * initializes the view and bounding box layer to display detected object bounds,
 * and ensures the bounding box is rendered above other view elements.
 */
- (instancetype)initWithFrame:(CGRect)frame
{
    if (self = [super initWithFrame:frame]) {
        _sessionQueue = dispatch_queue_create("com.pushpendersingh.reactNativeScanner.sessionQueue", DISPATCH_QUEUE_SERIAL);

        dispatch_async(dispatch_get_main_queue(), ^{
            self->_view = [[UIView alloc] initWithFrame:self.bounds];
            self->_boundingBoxLayer = [CAShapeLayer layer];
            self->_boundingBoxLayer.strokeColor = [UIColor greenColor].CGColor;
            self->_boundingBoxLayer.lineWidth = 2.0;
            self->_boundingBoxLayer.fillColor = [UIColor clearColor].CGColor;

            // Ensure bounding box is on top
            self->_boundingBoxLayer.zPosition = 999;

            [self->_view.layer addSublayer:self->_boundingBoxLayer];
            self.contentView = self->_view;
        });

        dispatch_async(_sessionQueue, ^{
            [self setupSessionIfNeeded];
        });
    }
    return self;
}

/**
 * Overrides the layoutSubviews method to update the frames of _view and _prevLayer.
 * Ensures that the updates are performed on the main dispatch queue for proper UI rendering.
 */
- (void)layoutSubviews
{
    [super layoutSubviews];
    dispatch_async(dispatch_get_main_queue(), ^{
        self->_view.frame = self.bounds;
        self->_prevLayer.frame = self->_view.bounds;
    });
}

/**
 * Handles the output from the capture session and processes detected metadata objects.
 *
 * @param captureOutput The capture output that is providing the metadata objects.
 * @param metadataObjects An array of metadata objects detected by the capture session.
 * @param connection The connection from which the metadata objects are received.
 *
 * This method filters the detected metadata objects to include only valid barcodes based on
 * predefined barcode types. If `pauseAfterCapture` is enabled and valid barcodes are found,
 * the scanning session is paused. For each valid barcode, it transforms the metadata object,
 * extracts the bounding box and corner points, and emits a scanned event with the barcode's
 * details. Additionally, if `showBox` is enabled, it draws a bounding box around the detected
 * barcode and removes it after a short delay.
 */
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
    // * Can be resumed back by calling resumeScanning from the owner of the component
    if (pauseAfterCapture == YES && validBarCodes.count > 0) {
        [self stopScanning];
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
        
        // Draw the bounding box if showBox is true
        if (showBox == YES) {
            UIBezierPath *path = [UIBezierPath bezierPathWithRect:highlightViewRect];
            _boundingBoxLayer.path = path.CGPath;
            
            // Hide bounding box after 1 second
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                self->_boundingBoxLayer.path = nil;
            });
        } else {
            _boundingBoxLayer.path = nil;
        }
    }
}

/**
 * Maps a dictionary to a CGPoint.
 *
 * This method extracts the "X" and "Y" values from the provided dictionary and creates a CGPoint.
 * If the dictionary is nil or missing either the "X" or "Y" value, it logs an error and returns CGPointZero.
 *
 * @param object The dictionary containing "X" and "Y" keys with NSNumber values.
 * @return A CGPoint constructed from the "X" and "Y" values, or CGPointZero if inputs are invalid.
 */
- (CGPoint)mapObject:(NSDictionary *)object {
    if (object == nil) {
        NSLog(@"Corner dictionary is nil.");
        return CGPointMake(0, 0);
    }
    NSNumber *xValue = object[@"X"];
    NSNumber *yValue = object[@"Y"];
    if (xValue == nil || yValue == nil) {
        NSLog(@"Missing corner X/Y values.");
        return CGPointMake(0, 0);
    }
    return CGPointMake([xValue doubleValue], [yValue doubleValue]);
}

/**
 * Sets the active state of the scanner.
 *
 * @param active A boolean value indicating whether the scanner should be active.
 *               - If `YES`, the preview and scanning will resume.
 *               - If `NO`, the preview and scanning will pause, and the camera will be released.
 */
- (void)setIsActive:(BOOL)active {
    isActive = active;

    // Enable/Disable Preview Layer
    if (isActive) {
        [self resumePreview];
        [self resumeScanning];
    } else {
        [self pausePreview];
        [self stopScanning];
        [self releaseCamera];
    }
}

/**
 * Releases the camera session and associated resources.
 *
 * This method stops the camera session from running, removes all inputs and outputs,
 * removes the preview layer from its superlayer, and sets all related properties to nil.
 * It ensures that the camera and session resources are properly released.
 */
- (void)releaseCamera {
    NSLog(@"%@", @"Release Camera");
    if (_session != nil) {
        [_session stopRunning];
        for (AVCaptureInput *input in _session.inputs) {
            [_session removeInput:input];
        }
        for (AVCaptureOutput *output in _session.outputs) {
            [_session removeOutput:output];
        }
        [_prevLayer removeFromSuperlayer];
        _prevLayer = nil;
        _session = nil;
        _device = nil;
        _input = nil;
        _output = nil;
        NSLog(@"Camera and session resources released.");
    }
}

/**
 * Sets up the AVCaptureSession if it hasn't been initialized.
 * 
 * This method initializes the capture session, configures the video device input,
 * sets up metadata output for barcode scanning, and creates a preview layer
 * to display the camera feed. It ensures that the session is only set up once
 * and starts running the session.
 */
- (void)setupSessionIfNeeded {
    if (_session != nil) {
        return; // Already set up
    }

    _session = [[AVCaptureSession alloc] init];
    if (!_session) {
        NSLog(@"Failed to create AVCaptureSession.");
        return;
    }

    // Disable automatic configuration of audio session since we don't need it for scanning barcodes
    _session.automaticallyConfiguresApplicationAudioSession = NO;
    _session.automaticallyConfiguresCaptureDeviceForWideColor = NO;

    _device = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    if (!_device) {
        NSLog(@"Failed to get default AVCaptureDevice for video.");
        return;
    }

    NSError *error = nil;
    _input = [AVCaptureDeviceInput deviceInputWithDevice:_device error:&error];
    if (_input) {
        [_session addInput:_input];
    } else {
        NSLog(@"Error creating AVCaptureDeviceInput: %@", [error localizedDescription]);
    }

    _output = [[AVCaptureMetadataOutput alloc] init];
    if (_output) {
        [_output setMetadataObjectsDelegate:self queue:dispatch_get_main_queue()];
        [_session addOutput:_output];
        _output.metadataObjectTypes = [ReactNativeScannerView metadataObjectTypes];
    } else {
        NSLog(@"Failed to create AVCaptureMetadataOutput.");
    }

    // Create preview layer on main thread
    dispatch_async(dispatch_get_main_queue(), ^{
        self->_prevLayer = [AVCaptureVideoPreviewLayer layerWithSession:self->_session];
        self->_prevLayer.videoGravity = AVLayerVideoGravityResizeAspectFill;
        [self->_view.layer addSublayer:self->_prevLayer];
        self->_prevLayer.frame = self->_view.bounds;
    });

    // Start the session
    [_session startRunning];
}

/**
 * Initiates the camera by setting up the session and starting it if it's not already running.
 * Executes the setup on the session queue asynchronously to ensure thread safety.
 */
- (void)startCamera {
    dispatch_async(_sessionQueue, ^{
        [self setupSessionIfNeeded];
        if (self->_session && !self->_session.isRunning) {
            [self->_session startRunning];
            NSLog(@"Capture session started.");
        }
    });
}

/**
 * Enables the device's flashlight (torch).
 *
 * This method checks if the device has a torch and if turning it on is supported.
 * If supported, it attempts to lock the device for configuration,
 * sets the torch mode to on, and then unlocks the device.
 * If configuration fails, it logs the encountered error.
 */
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

/**
 * Disables the device's flashlight if it is available and supported.
 * Locks the device configuration, sets the torch mode to off, and then unlocks the configuration.
 * Logs an error message if the device configuration cannot be locked.
 */
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

- (void)pausePreview {
    // Check if the preview layer's connection is currently enabled
    if ([[_prevLayer connection] isEnabled]) {
        // Disable the preview layer's connection to pause the preview
        [[_prevLayer connection] setEnabled:NO];
    }
}

/**
 * Resumes the camera preview by enabling the connection of the previous layer if it is not already enabled.
 */
- (void)resumePreview {
    if (![[_prevLayer connection] isEnabled]) {
        [[_prevLayer connection] setEnabled:YES];
    }
}

/**
 * Stop the scanning session if it is currently running.
 *
 * This method first checks if the scanning session exists. If the session is active,
 * it stops the session asynchronously and logs a message indicating that the capture
 * session has been stop.
 */
- (void)stopScanning {
    if (_session == nil) {
        NSLog(@"Session is nil, cannot stop scanning.");
        return;
    }
    dispatch_async(_sessionQueue, ^{
        if (self->_session.isRunning) {
            [self->_session stopRunning];
            NSLog(@"Capture session stop.");
        }
    });
}

/**
 * Resumes the scanning session.
 *
 * This method checks whether the scanning session is initialized. If the session
 * exists and is not currently running, it starts the session and logs the action.
 * If the session is nil, it logs an appropriate message and does not attempt to resume.
 */
- (void)resumeScanning {
    if (_session == nil) {
        NSLog(@"Session is nil, cannot resume scanning.");
        return;
    }
    dispatch_async(_sessionQueue, ^{
        if (!self->_session.isRunning) {
            [self->_session startRunning];
            NSLog(@"Capture session resumed.");
        }
    });
}

/**
 * Updates the properties of the ReactNativeScannerView.
 *
 * This method receives new and old property sets, updates internal state variables
 * such as `pauseAfterCapture` and `showBox` based on the new properties, sets the active
 * state, and calls the superclass's `updateProps` method to handle any additional updates.
 *
 * @param props The new set of properties to apply.
 * @param oldProps The previous set of properties before the update.
 */
- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
    const auto &oldViewProps = *std::static_pointer_cast<ReactNativeScannerViewProps const>(_props);
    const auto &newViewProps = *std::static_pointer_cast<ReactNativeScannerViewProps const>(props);
    
    pauseAfterCapture = newViewProps.pauseAfterCapture;
    showBox = newViewProps.showBox;
    [self setIsActive:newViewProps.isActive];
    
    [super updateProps:props oldProps:oldProps];
}

/**
 * Updates the layout metrics for the React Native Scanner view.
 *
 * This method adjusts the layout based on the new layout metrics provided.
 * It ensures that the previous layer's frame matches the bounds of the current view's layer.
 *
 * @param layoutMetrics The new layout metrics to apply.
 * @param oldLayoutMetrics The previous layout metrics before the update.
 */
- (void)updateLayoutMetrics:(const facebook::react::LayoutMetrics &)layoutMetrics oldLayoutMetrics:(const facebook::react::LayoutMetrics &)oldLayoutMetrics{
    [super updateLayoutMetrics:layoutMetrics oldLayoutMetrics:oldLayoutMetrics];
    _prevLayer.frame = [_view.layer bounds];
}

/**
 * Handles the specified command with the provided arguments.
 *
 * @param commandName The name of the command to execute.
 * @param args An array of arguments associated with the command.
 */
- (void)handleCommand:(nonnull const NSString *)commandName args:(nonnull const NSArray *)args {
    RCTReactNativeScannerViewHandleCommand(self, commandName, args);
}

@end

/**
 * Retrieves the ReactNativeScannerView class that conforms to the RCTComponentViewProtocol.
 */
Class<RCTComponentViewProtocol> ReactNativeScannerViewCls(void)
{
    return ReactNativeScannerView.class;
}
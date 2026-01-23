//
//  CameraManager.swift
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

@preconcurrency import AVFoundation
import Foundation
import UIKit
import CoreImage
@preconcurrency import Vision

// Actor that manages camera session state and operations
// Ensures all camera operations are thread-safe using Swift's actor isolation
actor CameraSessionActor {
    private var captureSession: AVCaptureSession?
    private var videoDevice: AVCaptureDevice?
    private var videoOutput: AVCaptureVideoDataOutput?
    private var isScanning = false
    
    func getSession() -> AVCaptureSession? {
        return captureSession
    }
    
    func setSession(_ session: AVCaptureSession?) {
        captureSession = session
    }
    
    func getVideoDevice() -> AVCaptureDevice? {
        return videoDevice
    }
    
    func setVideoDevice(_ device: AVCaptureDevice?) {
        videoDevice = device
    }
    
    func getVideoOutput() -> AVCaptureVideoDataOutput? {
        return videoOutput
    }
    
    func setVideoOutput(_ output: AVCaptureVideoDataOutput?) {
        videoOutput = output
    }
    
    func getScanningState() -> Bool {
        return isScanning
    }
    
    func setScanningState(_ scanning: Bool) {
        isScanning = scanning
    }
    
    // Eliminates race condition between check and set
    func startScanningIfNotActive() -> Bool {
        guard !isScanning else { return false }
        isScanning = true
        return true
    }

    // Safe stop operation
    func stopScanningIfActive() -> Bool {
        guard isScanning else { return false }
        isScanning = false
        return true
    }
}

// Actor that manages scan callbacks thread-safely
actor CallbackActor {
    private var scanCallback: (([[String: Any]]) -> Void)?
    
    func setCallback(_ callback: (([[String: Any]]) -> Void)?) {
        scanCallback = callback
    }
    
    func getCallback() -> (([[String: Any]]) -> Void)? {
        return scanCallback
    }
    
    func invokeCallback(with result: [[String: Any]]) {
        if let callback = scanCallback {
            Task { @MainActor in
                callback(result)
            }
        }
    }
}

@objc public class CameraManager: NSObject {

    private let sessionActor = CameraSessionActor()
    private let callbackActor = CallbackActor()
    private let sessionQueue = DispatchQueue(label: "com.pushpendersingh.scanner.sessionQueue")

    // Session interruption handling
    private var sessionInterruptionObserver: NSObjectProtocol?
    private var sessionInterruptionEndedObserver: NSObjectProtocol?
    @objc public var onSessionReady: ((AVCaptureSession) -> Void)?

    // Barcode types to detect
    private let supportedBarcodeTypes: [VNBarcodeSymbology] = [
        .qr,
        .aztec,
        .code128,
        .code39,
        .code93,
        .codabar,
        .dataMatrix,
        .ean13,
        .ean8,
        .itf14,
        .pdf417,
        .upce,
    ]

    @objc public func hasCameraPermission() -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        return status == .authorized
    }

    @objc public func requestCameraPermission(completion: @escaping (Bool) -> Void) {
        AVCaptureDevice.requestAccess(for: .video) { granted in
            completion(granted)
        }
    }

    // Async version - don't block main thread
    // Use this from Swift async contexts
    public func getCurrentSession() async -> AVCaptureSession? {
        return await sessionActor.getSession()
    }
    
    // Callback version for Objective-C bridge
    // Non-blocking alternative for synchronous contexts
    @objc public func getCurrentSession(completion: @escaping (AVCaptureSession?) -> Void) {
        Task {
            let session = await sessionActor.getSession()
            await MainActor.run {
                completion(session)
            }
        }
    }

    @objc(startScanningWithCallback:error:)
    public func startScanning(callback: @escaping ([[String: Any]]) -> Void) throws {
        // Check permission status immediately, but asynchronously request if needed.
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            // Permission already granted. Proceed with scanning setup.
            sessionQueue.async { [weak self] in
                guard let self = self else { return }
                Task {
                    await self.configureAndStartScanning(callback: callback)
                }
            }
        case .notDetermined:
            // Request permission. The result is handled asynchronously.
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self = self else { return }
                self.sessionQueue.async {
                    Task {
                        if granted {
                            await self.configureAndStartScanning(callback: callback)
                        } else {
                            // Handle denial.
                            let errorInfo = [["error": "Camera permission denied"]]
                            await self.callbackActor.invokeCallback(with: errorInfo)
                        }
                    }
                }
            }
        case .denied, .restricted:
            // Handle denied/restricted status immediately.
            let errorInfo = [["error": "Camera permission not granted"]]
            Task {
                await callbackActor.invokeCallback(with: errorInfo)
            }
        @unknown default:
            // Handle any future unknown cases.
            let errorInfo = [["error": "Unknown camera permission status"]]
            Task {
                await callbackActor.invokeCallback(with: errorInfo)
            }
        }
    }

    // Private helper with atomic check-and-set
    // Eliminates race condition in session initialization
    private func configureAndStartScanning(callback: @escaping ([[String: Any]]) -> Void) async {
        // Atomic operation - no race condition
        guard await sessionActor.startScanningIfNotActive() else {
            print("⚠️ Session already running, updating callback only")
            await callbackActor.setCallback(callback)
            return
        }
        
        // At this point, we're guaranteed to be the only thread starting scanning
        await callbackActor.setCallback(callback)
        
        do {
            try await setupCaptureSession()
        } catch {
            // Reset state on error
            await sessionActor.setScanningState(false)
            await callbackActor.setCallback(nil)
            print("❌ Failed to setup camera session: \(error.localizedDescription)")
        }
    }

    private func setupCaptureSession() async throws {
        // Reuse existing session if available
        let existingSession = await sessionActor.getSession()
        
        if let existingSession = existingSession {
            // If session exists but not running, just restart it
            if !existingSession.isRunning {
                print("♻️ Restarting existing session")
                existingSession.startRunning()
                print("✅ Camera session restarted - Running: \(existingSession.isRunning)")
            } else {
                print("⚠️ Session already running")
            }
            if let onSessionReady = self.onSessionReady {
                DispatchQueue.main.async { [weak self] in
                    guard self != nil else { return }
                    onSessionReady(existingSession)
                }
            }
            return
        }

        // Create new session only if none exists
        let newSession = AVCaptureSession()

        newSession.beginConfiguration()
        newSession.sessionPreset = .high

        // Setup video device
        guard let videoDevice = AVCaptureDevice.default(
            .builtInWideAngleCamera, for: .video, position: .back)
        else {
            print("❌ Failed to get video device")
            throw CameraError.deviceNotAvailable
        }
        await sessionActor.setVideoDevice(videoDevice)

        // Setup video input
        do {
            let videoInput = try AVCaptureDeviceInput(device: videoDevice)
            if newSession.canAddInput(videoInput) {
                newSession.addInput(videoInput)
            } else {
                print("❌ Cannot add video input")
                throw CameraError.cannotAddInput
            }
        } catch {
            print("❌ Error creating video input: \(error.localizedDescription)")
            throw error
        }

        // Setup video output
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.setSampleBufferDelegate(self, queue: sessionQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true

        if newSession.canAddOutput(videoOutput) {
            newSession.addOutput(videoOutput)
            await sessionActor.setVideoOutput(videoOutput)
        } else {
            print("❌ Cannot add video output")
            throw CameraError.cannotAddOutput
        }

        newSession.commitConfiguration()

        // Assign to actor AFTER configuration
        await sessionActor.setSession(newSession)

        // Start session (already on sessionQueue)
        newSession.startRunning()
        print("✅ Camera session started - Running: \(newSession.isRunning)")
        
        let output = await sessionActor.getVideoOutput()
        print("✅ Video output delegate set: \(output?.sampleBufferDelegate != nil)")

        // Notify UI after the session is running
        if let onSessionReady = self.onSessionReady {
            DispatchQueue.main.async { [weak self] in
                guard self != nil else { return }
                onSessionReady(newSession)
            }
        }
    }
    
    // Camera error types for better error handling
    enum CameraError: Error {
        case deviceNotAvailable
        case cannotAddInput
        case cannotAddOutput
        case sessionNotRunning
        case torchUnavailable
        
        var localizedDescription: String {
            switch self {
            case .deviceNotAvailable:
                return "Camera device not available"
            case .cannotAddInput:
                return "Cannot add video input to session"
            case .cannotAddOutput:
                return "Cannot add video output to session"
            case .sessionNotRunning:
                return "Camera session is not running"
            case .torchUnavailable:
                return "Torch/flashlight is not available on this device"
            }
        }
    }

    @objc public func stopScanning() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }

            Task {
                // ✅ Use atomic operation to stop scanning
                guard await self.sessionActor.stopScanningIfActive() else {
                    print("⚠️ Scanning is not active")
                    return
                }
                
                await self.callbackActor.setCallback(nil)

                let session = await self.sessionActor.getSession()
                if let session = session, session.isRunning {
                    session.stopRunning()
                    print("✅ Scanning stopped")
                } else {
                    print("⚠️ No active session to stop")
                }
            }
        }
    }

    @objc public func scanImage(_ imagePath: String, completion: @escaping ([[String: Any]]) -> Void) {
        // Ensure completion is called exactly once
        let completionLock = NSLock()
        var hasCompleted = false
        
        let safeCompletion: ([[String: Any]]) -> Void = { results in
            completionLock.lock()
            defer { completionLock.unlock() }
            if !hasCompleted {
                hasCompleted = true
                completion(results)
            }
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            
            let path: String
            if imagePath.hasPrefix("file://") {
                 if let url = URL(string: imagePath) {
                     path = url.path
                 } else {
                     // Try to strip file:// manually if URL parsing fails
                     path = String(imagePath.dropFirst(7))
                 }
            } else {
                path = imagePath
            }
            
            guard let image = UIImage(contentsOfFile: path),
                  let cgImage = image.cgImage else {
                print("Failed to load image from path: \(path)")
                safeCompletion([])
                return
            }

            // Convert UIImageOrientation to CGImagePropertyOrientation
            let orientation = self.cgImageOrientation(from: image.imageOrientation)
            
            let handler = VNImageRequestHandler(cgImage: cgImage, orientation: orientation, options: [:])
            let request = VNDetectBarcodesRequest { [weak self] request, error in
                guard let self = self else { return }
                
                // 1. Check Vision Results
                if error == nil, let observations = request.results as? [VNBarcodeObservation], !observations.isEmpty {
                    var results: [[String: Any]] = []
                    for barcode in observations {
                        if let payloadString = barcode.payloadStringValue {
                            results.append(self.createBarcodeResult(barcode: barcode, payloadString: payloadString))
                        }
                    }
                    if !results.isEmpty {
                        safeCompletion(results)
                        return
                    }
                }
                
                // 2. Fallback to CIDetector if Vision fails or returns empty
                print("Vision returned no results, trying CIDetector fallback...")
                
                // Create CIImage from UIImage to preserve orientation
                // CIImage(image:) returns an optional CIImage?
                // CIImage(cgImage:) returns a non-optional CIImage
                let ciImage: CIImage? = CIImage(image: image) ?? CIImage(cgImage: cgImage)
                
                guard let finalCIImage = ciImage else {
                    safeCompletion([])
                    return
                }
                
                let context = CIContext()
                let options = [CIDetectorAccuracy: CIDetectorAccuracyHigh]
                let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: context, options: options)
                
                // Use CIDetector features
                // Note: CIImage(image: UIImage) should handle orientation automatically
                let features = detector?.features(in: finalCIImage) as? [CIQRCodeFeature]
                
                var fallbackResults: [[String: Any]] = []
                if let features = features {
                    for feature in features {
                        if let messageString = feature.messageString {
                            fallbackResults.append(self.createFallbackResult(feature: feature))
                        }
                    }
                }
                
                print("CIDetector found \(fallbackResults.count) results")
                safeCompletion(fallbackResults)
            }
            
            request.symbologies = self.supportedBarcodeTypes
            
            do {
                try handler.perform([request])
            } catch {
                print("Failed to perform barcode request: \(error)")
                safeCompletion([])
            }
        }
    }
    
    private func cgImageOrientation(from uiOrientation: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch uiOrientation {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }

    @objc public func enableFlashlight() {
        // Execute on sessionQueue for thread safety
        sessionQueue.async { [weak self] in
            guard let self = self else { return }

            Task {
                // Check if session is running
                let session = await self.sessionActor.getSession()
                guard let session = session, session.isRunning else {
                    print("⚠️ Cannot enable flashlight - camera not running")
                    return
                }

                let device = await self.sessionActor.getVideoDevice()
                guard let device = device, device.hasTorch else {
                    print("⚠️ Torch not available")
                    return
                }

                do {
                    try device.lockForConfiguration()
                    device.torchMode = .on
                    device.unlockForConfiguration()
                    print("✅ Flashlight enabled")
                } catch {
                    print("❌ Failed to enable torch: \(error.localizedDescription)")
                }
            }
        }
    }

    @objc public func disableFlashlight() {
        // Execute on sessionQueue for thread safety
        sessionQueue.async { [weak self] in
            guard let self = self else { return }

            Task {
                // Check if session is running
                let session = await self.sessionActor.getSession()
                guard let session = session, session.isRunning else {
                    print("⚠️ Cannot disable flashlight - camera not running")
                    return
                }

                let device = await self.sessionActor.getVideoDevice()
                guard let device = device, device.hasTorch else {
                    print("⚠️ Torch not available")
                    return
                }

                do {
                    try device.lockForConfiguration()
                    device.torchMode = .off
                    device.unlockForConfiguration()
                    print("✅ Flashlight disabled")
                } catch {
                    print("❌ Failed to disable torch: \(error.localizedDescription)")
                }
            }
        }
    }

    // Setup interruption observers in init
    public override init() {
        super.init()
        // Ensure observers are set up on the main actor without synchronously crossing isolation boundaries.
        Task { @MainActor in
            self.setupInterruptionObservers()
        }
    }

    @MainActor
    private func setupInterruptionObservers() {
        // Handle session interruption (e.g., incoming call, alarm)
        sessionInterruptionObserver = NotificationCenter.default.addObserver(
            forName: .AVCaptureSessionWasInterrupted,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            guard let self = self else { return }

            if let reasonValue = notification.userInfo?[AVCaptureSessionInterruptionReasonKey]
                as? Int,
                let reason = AVCaptureSession.InterruptionReason(rawValue: reasonValue)
            {
                print("⚠️ Session interrupted: \(reason)")

                // Pause scanning during interruption
                Task {
                    await self.sessionActor.setScanningState(false)
                }
            }
        }

        // Handle session interruption ended
        sessionInterruptionEndedObserver = NotificationCenter.default.addObserver(
            forName: .AVCaptureSessionInterruptionEnded,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            guard let self = self else { return }
            print("✅ Session interruption ended")

            // Resume scanning only if we were actually scanning before interruption
            Task {
                // Check scanning state first to prevent restarting released session
                let isScanning = await self.sessionActor.getScanningState()
                let callback = await self.callbackActor.getCallback()
                let session = await self.sessionActor.getSession()
                
                // Only resume if still supposed to be scanning
                if isScanning, callback != nil, let session = session, !session.isRunning {
                    session.startRunning()
                    print("✅ Scanning resumed after interruption")
                } else if !isScanning {
                    print("ℹ️ Not resuming - scanning was stopped during interruption")
                }
            }
        }
    }

    @objc public func releaseCamera() {
        // Remove observers on main thread to keep lifecycle consistent
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if let observer = self.sessionInterruptionObserver {
                NotificationCenter.default.removeObserver(observer)
                self.sessionInterruptionObserver = nil
            }
            if let observer = self.sessionInterruptionEndedObserver {
                NotificationCenter.default.removeObserver(observer)
                self.sessionInterruptionEndedObserver = nil
            }
        }

        sessionQueue.async { [weak self] in
            guard let self = self else { return }

            Task {
                // Stop scanning first
                await self.sessionActor.setScanningState(false)
                await self.callbackActor.setCallback(nil)

                // Stop session before modifying it
                let session = await self.sessionActor.getSession()
                if let session = session {
                    if session.isRunning {
                        session.stopRunning()
                    }

                    // Use beginConfiguration when removing inputs/outputs
                    session.beginConfiguration()

                    for input in session.inputs {
                        session.removeInput(input)
                    }
                    for output in session.outputs {
                        session.removeOutput(output)
                    }

                    session.commitConfiguration()
                }

                await self.sessionActor.setSession(nil)
                await self.sessionActor.setVideoDevice(nil)
                await self.sessionActor.setVideoOutput(nil)

                print("✅ Camera resources released")
            }
        }

        // Clear the onSessionReady callback to prevent retain cycles
        onSessionReady = nil

        // Re-setup observers for next use
        Task { @MainActor in
            self.setupInterruptionObservers()
        }
    }

    // Deinit to cleanup observers
    deinit {
        if let observer = sessionInterruptionObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        if let observer = sessionInterruptionEndedObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        print("🗑️ CameraManager deallocated")
    }
}

// AVCaptureVideoDataOutputSampleBufferDelegate 
extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {

    public func captureOutput(
        _ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        // Check scanning state using async context
        Task {
            let isCurrentlyScanning = await sessionActor.getScanningState()
            guard isCurrentlyScanning else { return }

            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
                print("⚠️ Failed to get pixel buffer")
                return
            }

            let imageRequestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])

            let barcodeRequest = VNDetectBarcodesRequest { [weak self] request, error in
                guard let self = self else { return }

                Task {
                    // Double-check isScanning inside async callback
                    let stillScanning = await self.sessionActor.getScanningState()
                    guard stillScanning else { return }

                    if let error = error {
                        print("Barcode detection error: \(error.localizedDescription)")
                        return
                    }

                    guard let results = request.results as? [VNBarcodeObservation], !results.isEmpty else {
                        return
                    }

                    var barcodeResults: [[String: Any]] = []
                    
                    for barcode in results {
                        if let payloadString = barcode.payloadStringValue {
                            let result = self.createBarcodeResult(
                                barcode: barcode, payloadString: payloadString)
                            barcodeResults.append(result)
                        }
                    }
                    
                    if !barcodeResults.isEmpty {
                        // Invoke callback through the actor for thread safety
                        await self.callbackActor.invokeCallback(with: barcodeResults)
                        print("📤 Callback invoked with \(barcodeResults.count) barcodes")
                    }
                }
            }

            barcodeRequest.symbologies = supportedBarcodeTypes

            do {
                try imageRequestHandler.perform([barcodeRequest])
            } catch {
                print("Failed to perform barcode detection: \(error.localizedDescription)")
            }
        }
    }

    private func createBarcodeResult(barcode: VNBarcodeObservation, payloadString: String)
        -> [String: Any]
    {
        var result: [String: Any] = [
            "data": payloadString,
            "type": getBarcodeTypeName(barcode.symbology),
        ]

        // Add bounds if available
        let boundingBox = barcode.boundingBox
        let bounds: [String: Any] = [
            "width": boundingBox.width,
            "height": boundingBox.height,
            "origin": [
                "topLeft": ["x": boundingBox.minX, "y": boundingBox.maxY],
                "bottomLeft": ["x": boundingBox.minX, "y": boundingBox.minY],
                "bottomRight": ["x": boundingBox.maxX, "y": boundingBox.minY],
                "topRight": ["x": boundingBox.maxX, "y": boundingBox.maxY],
            ],
        ]

        result["bounds"] = bounds

        return result
    }

    private func createFallbackResult(feature: CIQRCodeFeature) -> [String: Any] {
        var result: [String: Any] = [
            "data": feature.messageString ?? "",
            "type": "QR_CODE"
        ]
        
        // Add bounds if needed (converting from CoreImage coordinates)
        let boundingBox = feature.bounds
        let bounds: [String: Any] = [
            "width": boundingBox.width,
            "height": boundingBox.height,
            "origin": [
                "x": boundingBox.origin.x,
                "y": boundingBox.origin.y
            ]
        ]
        result["bounds"] = bounds

        return result
    }

    private func getBarcodeTypeName(_ symbology: VNBarcodeSymbology) -> String {
        switch symbology {
        case .qr:
            return "QR_CODE"
        case .aztec:
            return "AZTEC"
        case .code128:
            return "CODE_128"
        case .code39:
            return "CODE_39"
        case .code93:
            return "CODE_93"
        case .codabar:
            return "CODABAR"
        case .dataMatrix:
            return "DATA_MATRIX"
        case .ean13:
            return "EAN_13"
        case .ean8:
            return "EAN_8"
        case .itf14:
            return "ITF"
        case .pdf417:
            return "PDF417"
        case .upce:
            return "UPC_E"
        default:
            return "UNKNOWN"
        }
    }
}

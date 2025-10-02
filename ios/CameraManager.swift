//
//  CameraManager.swift
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

import Foundation
@preconcurrency import AVFoundation
@preconcurrency import Vision

@objc public class CameraManager: NSObject, @unchecked Sendable {
    
    private var captureSession: AVCaptureSession?
    private var videoDevice: AVCaptureDevice?
    private var videoOutput: AVCaptureVideoDataOutput?
    private let sessionQueue = DispatchQueue(label: "com.pushpendersingh.scanner.sessionQueue")
    private var isScanning = false
    private var scanCallback: (([String: Any]) -> Void)?
    
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
        .upce
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
    
    @objc public func currentSession() -> AVCaptureSession? {
        // CHANGE: Synchronize the read on sessionQueue to avoid cross-thread access warnings.
        // Reason: The session is produced/mutated on sessionQueue; reading it from main without
        // synchronization can trigger structural concurrency diagnostics.
        return sessionQueue.sync { captureSession }
    }
    
    @objc(startScanningWithCallback:error:)
    public func startScanning(callback: @escaping ([String: Any]) -> Void) throws {
        guard hasCameraPermission() else {
            throw NSError(domain: "CameraManager", code: 1, userInfo: [NSLocalizedDescriptionKey: "Camera permission not granted"])
        }
        
        // Execute on sessionQueue for thread safety
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Check if session is already running
            if self.isScanning, let session = self.captureSession, session.isRunning {
                print("⚠️ Session already running, updating callback only")
                self.scanCallback = callback
                return
            }
            
            // Set flag on the same queue where it's checked
            self.isScanning = true
            self.scanCallback = callback
            
            self.setupCaptureSession()
        }
    }
    
    private func setupCaptureSession() {
        // Reuse existing session if available
        if let existingSession = captureSession {
            // If session exists but not running, just restart it
            if !existingSession.isRunning {
                print("♻️ Restarting existing session")
                existingSession.startRunning()
                print("✅ Camera session restarted - Running: \(existingSession.isRunning)")
            } else {
                print("⚠️ Session already running")
            }
            if let onSessionReady = self.onSessionReady {
                DispatchQueue.main.async {
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
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            print("❌ Failed to get video device")
            isScanning = false
            return
        }
        self.videoDevice = videoDevice
        
        // Setup video input
        do {
            let videoInput = try AVCaptureDeviceInput(device: videoDevice)
            if newSession.canAddInput(videoInput) {
                newSession.addInput(videoInput)
            } else {
                print("❌ Cannot add video input")
                isScanning = false
                return
            }
        } catch {
            print("❌ Error creating video input: \(error.localizedDescription)")
            isScanning = false
            return
        }
        
        // Setup video output
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.setSampleBufferDelegate(self, queue: sessionQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true
        
        if newSession.canAddOutput(videoOutput) {
            newSession.addOutput(videoOutput)
            self.videoOutput = videoOutput
        } else {
            print("❌ Cannot add video output")
            isScanning = false
            return
        }
        
        newSession.commitConfiguration()
        
        // Assign to property AFTER configuration
        self.captureSession = newSession
        
        // Start session (already on sessionQueue)
        newSession.startRunning()
        print("✅ Camera session started - Running: \(newSession.isRunning)")
        print("✅ Video output delegate set: \(self.videoOutput?.sampleBufferDelegate != nil)")
        
        // Notify UI after the session is running to avoid startRunning during configuration
        if let onSessionReady = self.onSessionReady {
            DispatchQueue.main.async {
                onSessionReady(newSession)
            }
        }
    }
    
    @objc public func stopScanning() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Set flag first, then stop session
            self.isScanning = false
            self.scanCallback = nil
            
            if let session = self.captureSession, session.isRunning {
                session.stopRunning()
                print("✅ Scanning stopped")
            } else {
                print("⚠️ No active session to stop")
            }
        }
    }
    
    @objc public func enableFlashlight() {
        // Execute on sessionQueue for thread safety
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Check if session is running
            guard let session = self.captureSession, session.isRunning else {
                print("⚠️ Cannot enable flashlight - camera not running")
                return
            }
            
            guard let device = self.videoDevice, device.hasTorch else {
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
    
    @objc public func disableFlashlight() {
        // Execute on sessionQueue for thread safety
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Check if session is running
            guard let session = self.captureSession, session.isRunning else {
                print("⚠️ Cannot disable flashlight - camera not running")
                return
            }
            
            guard let device = self.videoDevice, device.hasTorch else {
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
            
            if let reasonValue = notification.userInfo?[AVCaptureSessionInterruptionReasonKey] as? Int,
               let reason = AVCaptureSession.InterruptionReason(rawValue: reasonValue) {
                print("⚠️ Session interrupted: \(reason)")
                
                // Optionally pause scanning during interruption
                sessionQueue.async {
                    self.isScanning = false
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
            
            // Resume scanning only if we had a callback (means scanning was active)
            self.sessionQueue.async {
                if self.scanCallback != nil, let session = self.captureSession, !session.isRunning {
                    session.startRunning()
                    self.isScanning = true
                    print("✅ Scanning resumed after interruption")
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
            
            // Stop scanning first
            self.isScanning = false
            self.scanCallback = nil
            
            // Stop session before modifying it
            if let session = self.captureSession {
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
            
            self.captureSession = nil
            self.videoDevice = nil
            self.videoOutput = nil
            
            print("✅ Camera resources released")
        }
        
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

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    
    public func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        // Early exit if not scanning
        guard isScanning else { return }
        
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            print("⚠️ Failed to get pixel buffer")
            return
        }
        
        let imageRequestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        
        let barcodeRequest = VNDetectBarcodesRequest { [weak self] request, error in
            guard let self = self else { return }
            
            // Double-check isScanning inside async callback
            guard self.isScanning else {
                print("⚠️ Barcode detected but scanning stopped, ignoring")
                return
            }
            
            if let error = error {
                print("Barcode detection error: \(error.localizedDescription)")
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation],
                  let firstBarcode = results.first,
                  let payloadString = firstBarcode.payloadStringValue else {
                return
            }
            
            print("✅ Barcode detected: \(payloadString) - Type: \(firstBarcode.symbology.rawValue)")
            
            // Create result dictionary
            let result = self.createBarcodeResult(barcode: firstBarcode, payloadString: payloadString)
            
            // Capture callback safely
            guard let callback = self.scanCallback else {
                print("⚠️ No callback available")
                return
            }
            
            // Call the callback on main thread
            DispatchQueue.main.async {
                if self.isScanning {
                    callback(result)
                    print("📤 Callback invoked with barcode data")
                } else {
                    print("⚠️ Scanning stopped before callback, ignoring")
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
    
    private func createBarcodeResult(barcode: VNBarcodeObservation, payloadString: String) -> [String: Any] {
        var result: [String: Any] = [
            "data": payloadString,
            "type": getBarcodeTypeName(barcode.symbology)
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
                "topRight": ["x": boundingBox.maxX, "y": boundingBox.maxY]
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


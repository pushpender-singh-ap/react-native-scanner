//
//  CameraManager.swift
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

import Foundation
import AVFoundation
import Vision

@objc public class CameraManager: NSObject {
    
    private var captureSession: AVCaptureSession?
    private var videoDevice: AVCaptureDevice?
    private var videoOutput: AVCaptureVideoDataOutput?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let sessionQueue = DispatchQueue(label: "com.pushpendersingh.scanner.sessionQueue")
    private var isScanning = false
    private var scanCallback: (([String: Any]) -> Void)?
    
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
    
    @objc public func getPreviewLayer() -> AVCaptureVideoPreviewLayer? {
        return previewLayer
    }
    
    @objc public func bindPreviewLayer(_ layer: AVCaptureVideoPreviewLayer) {
        self.previewLayer = layer
        if let session = captureSession {
            DispatchQueue.main.async {
                layer.session = session
            }
        }
    }
    
    @objc(startScanningWithCallback:error:)
    public func startScanning(callback: @escaping ([String: Any]) -> Void) throws {
        guard hasCameraPermission() else {
            throw NSError(domain: "CameraManager", code: 1, userInfo: [NSLocalizedDescriptionKey: "Camera permission not granted"])
        }
        
        if isScanning {
            print("Scanning already in progress")
            return
        }
        
        scanCallback = callback
        sessionQueue.async { [weak self] in
            self?.setupCaptureSession()
        }
    }
    
    private func setupCaptureSession() {
        captureSession = AVCaptureSession()
        guard let captureSession = captureSession else { return }
        
        captureSession.beginConfiguration()
        captureSession.sessionPreset = .high
        
        // Setup video device
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            print("Failed to get video device")
            return
        }
        self.videoDevice = videoDevice
        
        // Setup video input
        do {
            let videoInput = try AVCaptureDeviceInput(device: videoDevice)
            if captureSession.canAddInput(videoInput) {
                captureSession.addInput(videoInput)
            }
        } catch {
            print("Error creating video input: \(error.localizedDescription)")
            return
        }
        
        // Setup video output
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.setSampleBufferDelegate(self, queue: sessionQueue)
        videoOutput.alwaysDiscardsLateVideoFrames = true
        
        if captureSession.canAddOutput(videoOutput) {
            captureSession.addOutput(videoOutput)
            self.videoOutput = videoOutput
        }
        
        captureSession.commitConfiguration()
        
        // Setup preview layer if needed
        if let previewLayer = self.previewLayer {
            DispatchQueue.main.async {
                previewLayer.session = captureSession
            }
        }
        
        // Start the session
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.captureSession?.startRunning()
            self.isScanning = true
            print("✅ Camera session started - Running: \(self.captureSession?.isRunning ?? false)")
            print("✅ Video output delegate set: \(self.videoOutput?.sampleBufferDelegate != nil)")
        }
    }
    
    @objc public func stopScanning() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.isScanning = false
            self.scanCallback = nil
            self.captureSession?.stopRunning()
            print("Scanning stopped")
        }
    }
    
    @objc public func enableFlashlight() {
        guard let device = videoDevice, device.hasTorch else {
            print("Torch not available")
            return
        }
        
        do {
            try device.lockForConfiguration()
            device.torchMode = .on
            device.unlockForConfiguration()
            print("Flashlight enabled")
        } catch {
            print("Failed to enable torch: \(error.localizedDescription)")
        }
    }
    
    @objc public func disableFlashlight() {
        guard let device = videoDevice, device.hasTorch else {
            print("Torch not available")
            return
        }
        
        do {
            try device.lockForConfiguration()
            device.torchMode = .off
            device.unlockForConfiguration()
            print("Flashlight disabled")
        } catch {
            print("Failed to disable torch: \(error.localizedDescription)")
        }
    }
    
    @objc public func releaseCamera() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            self.isScanning = false
            self.captureSession?.stopRunning()
            
            // Remove inputs and outputs
            if let session = self.captureSession {
                for input in session.inputs {
                    session.removeInput(input)
                }
                for output in session.outputs {
                    session.removeOutput(output)
                }
            }
            
            self.captureSession = nil
            self.videoDevice = nil
            self.videoOutput = nil
            self.previewLayer = nil
            self.scanCallback = nil
            print("Camera resources released")
        }
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension CameraManager: AVCaptureVideoDataOutputSampleBufferDelegate {
    
    public func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard isScanning else { return }
        
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            print("⚠️ Failed to get pixel buffer")
            return
        }
        
        let imageRequestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        
        let barcodeRequest = VNDetectBarcodesRequest { [weak self] request, error in
            guard let self = self, self.isScanning else { return }
            
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
            
            // Call the callback on main thread with thread-safe copy
            let callback = self.scanCallback
            DispatchQueue.main.async {
                callback?(result)
                print("📤 Callback invoked with barcode data")
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

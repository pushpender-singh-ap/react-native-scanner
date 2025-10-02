//
//  CameraView.swift
//  ReactNativeScanner
//
//  Created by Pushpender Singh
//

import UIKit
import AVFoundation

@objc(CameraView)
public class CameraView: UIView {
    
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var cameraManager: CameraManager?
    
    public override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }
    
    private func setupView() {
        backgroundColor = .black
        
        // Create preview layer
        previewLayer = AVCaptureVideoPreviewLayer()
        if let previewLayer = previewLayer {
            previewLayer.videoGravity = .resizeAspectFill
            previewLayer.frame = bounds
            layer.addSublayer(previewLayer)
        }
    }
    
    @objc public func setCameraManager(_ manager: CameraManager) {
        self.cameraManager = manager
        
        // CHANGE: Subscribe to CameraManager's onSessionReady callback.
        // Reason: CameraView owns the preview layer and binds it to the session on the main thread,
        // keeping UI work on main and avoiding concurrency violations.
        manager.onSessionReady = { [weak self] session in
            guard let self = self else { return }
            DispatchQueue.main.async {
                if let previewLayer = self.previewLayer {
                    previewLayer.session = session
                    previewLayer.connection?.videoOrientation = .portrait
                    print("✅ Preview layer bound to session from onSessionReady callback")
                } else {
                    print("⚠️ Preview layer missing when session became ready")
                }
            }
        }
        
        // CHANGE: If a session already exists, bind it immediately on the main thread.
        // Reason: Ensures the preview shows even if the session was created before the view.
        if let existingSession = manager.currentSession() {
            DispatchQueue.main.async { [weak self] in
                guard let self = self, let previewLayer = self.previewLayer else { return }
                previewLayer.session = existingSession
                previewLayer.connection?.videoOrientation = .portrait
                print("✅ Preview layer bound to existing session")
            }
        }
        
        print("✅ Camera manager set on CameraView")
    }
    
    public override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }
    
    deinit {
        // CHANGE: Clear session on teardown to avoid retaining references.
        previewLayer?.session = nil
        previewLayer?.removeFromSuperlayer()
        previewLayer = nil
    }
}


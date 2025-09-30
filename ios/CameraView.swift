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
        if let previewLayer = previewLayer {
            manager.bindPreviewLayer(previewLayer)
        }
    }
    
    public override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }
    
    deinit {
        previewLayer?.removeFromSuperlayer()
        previewLayer = nil
    }
}

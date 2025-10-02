require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "ReactNativeScanner"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/pushpender-singh-ap/react-native-scanner.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.private_header_files = "ios/**/*.h"

  # Enable modules and set the Swift bridging header to allow Swift and Objective-C to interoperate
  s.pod_target_xcconfig = {
    # Enables the use of modules (i.e., frameworks) in the generated Xcode project
    'DEFINES_MODULE' => 'YES',
    
    # Sets the name of the generated Swift header for Objective-C code to use
    'SWIFT_OBJC_INTERFACE_HEADER_NAME' => 'ReactNativeScanner-Swift.h'
  }

  # The name of the module that will be generated for this pod
  s.module_name = s.name
  
  # Swift support
  s.swift_version = '5.0'
  
  # Frameworks
  s.frameworks = 'AVFoundation', 'Vision'

  install_modules_dependencies(s)
end

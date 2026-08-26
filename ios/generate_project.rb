#!/usr/bin/env ruby

require "xcodeproj"
require "fileutils"

root = File.expand_path(__dir__)
project_path = File.join(root, "Whappy.xcodeproj")
FileUtils.rm_rf(project_path)

project = Xcodeproj::Project.new(project_path)
project.root_object.attributes["LastSwiftUpdateCheck"] = "2660"
project.root_object.attributes["LastUpgradeCheck"] = "2660"

target = project.new_target(:application, "Whappy", :ios, "17.0")
target.product_name = "Whappy"

group = project.main_group.new_group("Whappy", "Whappy")
sources = %w[WapiDesignSystem.swift WapiNativeLive.swift WapiDirectCall.swift WiaAssistantView.swift WapiGameSceneKit.swift WhappyApp.swift Models.swift WhappyStore.swift WhappyFirebaseMessaging.swift ContentView.swift]
source_refs = sources.map { |name| group.new_file(name) }
target.add_file_references(source_refs)

# LiveKit est ajouté avec Swift Package Manager, la voie native recommandée
# pour iOS. Seul le client public entre dans l'application ; les clés serveur
# restent dans Firebase Functions.
livekit_package = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
livekit_package.repositoryURL = "https://github.com/livekit/client-sdk-swift.git"
livekit_package.requirement = {
  "kind" => "upToNextMajorVersion",
  "minimumVersion" => "2.16.0"
}
project.root_object.package_references << livekit_package

livekit_product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
livekit_product.package = livekit_package
livekit_product.product_name = "LiveKit"
target.package_product_dependencies << livekit_product

livekit_build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
livekit_build_file.product_ref = livekit_product
target.frameworks_build_phase.files << livekit_build_file

# Direct one-to-one calls use the WebRTC framework without going through the
# LiveKit room API. Keep the exact version aligned with LiveKit's dependency.
webrtc_package = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
webrtc_package.repositoryURL = "https://github.com/livekit/webrtc-xcframework.git"
webrtc_package.requirement = {
  "kind" => "exactVersion",
  "version" => "144.7559.11"
}
project.root_object.package_references << webrtc_package

webrtc_product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
webrtc_product.package = webrtc_package
webrtc_product.product_name = "LiveKitWebRTC"
target.package_product_dependencies << webrtc_product

webrtc_build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
webrtc_build_file.product_ref = webrtc_product
target.frameworks_build_phase.files << webrtc_build_file

assets = group.new_file("Assets.xcassets")
target.resources_build_phase.add_file_reference(assets)
firebase_config = group.new_file("GoogleService-Info.plist")
target.resources_build_phase.add_file_reference(firebase_config)

audio_group = group.new_group("GameAudio", "GameAudio")
audio_names = %w[
  wapi_dice_roll.wav
  wapi_piece_select.wav
  wapi_piece_move.wav
  wapi_piece_capture.wav
  wapi_piece_crown.wav
  wapi_pool_hit.wav
  wapi_pool_pocket.wav
  wapi_card_flip.wav
  wapi_victory.wav
]
audio_refs = audio_names.map { |name| audio_group.new_file(name) }
audio_refs.each { |reference| target.resources_build_phase.add_file_reference(reference) }

target.build_configurations.each do |config|
  config.build_settings.merge!({
    "ASSETCATALOG_COMPILER_APPICON_NAME" => "AppIcon",
    "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME" => "AccentColor",
    "CODE_SIGN_STYLE" => "Automatic",
    "CURRENT_PROJECT_VERSION" => "2",
    "ENABLE_PREVIEWS" => "YES",
    "GENERATE_INFOPLIST_FILE" => "YES",
    "INFOPLIST_KEY_CFBundleDisplayName" => "WAPI",
    "INFOPLIST_KEY_LSApplicationCategoryType" => "public.app-category.social-networking",
    "INFOPLIST_KEY_NSCameraUsageDescription" => "WAPI utilise la caméra pour les photos, vidéos et directs que vous choisissez de partager.",
    "INFOPLIST_KEY_NSMicrophoneUsageDescription" => "WAPI utilise le microphone pour les messages vocaux, appels et directs.",
    "INFOPLIST_KEY_NSPhotoLibraryUsageDescription" => "WAPI accède aux médias que vous choisissez de publier ou d’envoyer.",
    "INFOPLIST_KEY_UIApplicationSceneManifest_Generation" => "YES",
    "INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents" => "YES",
    "INFOPLIST_KEY_UILaunchScreen_Generation" => "YES",
    "INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone" => "UIInterfaceOrientationPortrait",
    "IPHONEOS_DEPLOYMENT_TARGET" => "17.0",
    "MARKETING_VERSION" => "2.1.1",
    "PRODUCT_BUNDLE_IDENTIFIER" => "com.whappy.chat",
    "PRODUCT_NAME" => "$(TARGET_NAME)",
    "OTHER_LDFLAGS" => "$(inherited) -ObjC",
    "SWIFT_EMIT_LOC_STRINGS" => "YES",
    "SWIFT_VERSION" => "5.0",
    "TARGETED_DEVICE_FAMILY" => "1,2"
  })
end

project.build_configurations.each do |config|
  config.build_settings.merge!({
    "CLANG_ENABLE_MODULES" => "YES",
    "SWIFT_VERSION" => "5.0"
  })
end

project.save
puts "Projet généré : #{project_path}"

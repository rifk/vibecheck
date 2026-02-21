# iOS App Launcher

This folder now contains a runnable Xcode app wrapper:

- Project: `/Users/rif/code/testProject/iosApp/iosApp.xcodeproj`
- Scheme: `iosApp`
- Shared entrypoint: `MainViewControllerKt.MainViewController()` from `ComposeApp.framework`

## How it works

- The Xcode target has a build phase (`Build Kotlin Framework`) that runs:
  - `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
- The resulting framework is resolved from:
  - `../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/ComposeApp.framework`

## Run

1. Open `iosApp.xcodeproj` in Xcode.
2. Select scheme `iosApp`.
3. Run on an iOS Simulator or device.

If Xcode reports missing iOS platform components, install them from `Xcode > Settings > Components`.

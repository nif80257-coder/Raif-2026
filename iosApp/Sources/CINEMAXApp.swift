import UIKit
import SwiftUI
import AVFoundation
import CoreSpotlight
import CinemaxKit

/// Which orientations the window allows right now. The app itself stays portrait;
/// the player asks for landscape while it is on screen.
final class OrientationManager {
    static let shared = OrientationManager()

    private(set) var mask: UIInterfaceOrientationMask = .portrait

    var isPlayerActive: Bool { mask != .portrait }

    /// Lets the player rotate (or locks the interface back to portrait) and asks
    /// the window to follow immediately, so the picture turns with the phone.
    func setPlayerRotation(_ allowed: Bool) {
        mask = allowed ? .allButUpsideDown : .portrait
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
    }

    /// Turns the screen right now, for the player's own rotate button.
    func rotate(toLandscape: Bool) {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        let target: UIInterfaceOrientationMask = toLandscape ? .landscapeRight : .portrait
        scene.requestGeometryUpdate(.iOS(interfaceOrientations: target)) { _ in }
    }
}

/// UIKit entry point adopting the scene lifecycle. The window is created by the
/// scene delegate, which is what iOS expects from an app built with a current SDK.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // A larger shared HTTP cache keeps posters and manifests on disk between
        // launches, which is what makes the first screen appear instantly.
        URLCache.shared = URLCache(
            memoryCapacity: 64 * 1024 * 1024,
            diskCapacity: 512 * 1024 * 1024
        )
        // The category is set once at launch; the players activate the session only
        // while they are on screen, so opening the app never stops other audio.
        do {
            try AVAudioSession.sharedInstance().setCategory(
                .playback,
                mode: .moviePlayback,
                options: [.allowAirPlay, .allowBluetoothA2DP]
            )
        } catch {
            // A failing audio session must never stop the app from opening.
        }
        return true
    }

    /// iOS woke the app for a download it kept fetching while CINEMAX was closed.
    /// The handler is held until every event reached the session's delegate, which
    /// is what tells the system the wake-up is finished.
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        guard identifier == BackgroundTransfers.identifier else {
            completionHandler()
            return
        }
        BackgroundTransfers.shared.hold(completionHandler)
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        OrientationManager.shared.mask
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(
            name: "Default Configuration",
            sessionRole: connectingSceneSession.role
        )
        configuration.delegateClass = SceneDelegate.self
        configuration.sceneClass = UIWindowScene.self
        return configuration
    }
}

/// The window's root is either the SwiftUI interface (the default) or the classic
/// Compose one, chosen by the flag both interfaces write. Either way it is the
/// window's own controller, so layout, density and safe areas come from the system.
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        window.backgroundColor = .black
        window.overrideUserInterfaceStyle = .dark
        self.window = window
        applyInterface(animated: false)
        window.makeKeyAndVisible()

        #if canImport(Libmpv)
        DispatchQueue.main.async {
            CinemaxMPVRegistration.register()
        }
        #endif

        // Asking for the download list restores the transfers on the way in, so a
        // file iOS kept downloading while the app was closed is joined now rather
        // than on the first visit to its tab.
        _ = DownloadManager.shared

        showLastKotlinCrashIfAny()
    }

    /// A title the viewer tapped in the system's search: the identifier carries the
    /// media id, and the root screen opens that title's page once it is active.
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        guard userActivity.activityType == CSSearchableItemActionType,
              let identifier = userActivity.userInfo?[CSSearchableItemActivityIdentifier] as? String,
              identifier.hasPrefix("item:") else {
            return
        }
        UserDefaults.standard.set(identifier, forKey: "cinemax.pendingCommand")
    }

    /// The SwiftUI interface owns the window; the Kotlin framework underneath is
    /// the data engine (addons, caches, history, addon protocol) and nothing else.
    private func applyInterface(animated: Bool) {
        guard let window = self.window else { return }
        window.overrideUserInterfaceStyle = .dark
        window.rootViewController = UIHostingController(rootView: CinemaRootView())
        if animated {
            UIView.transition(with: window, duration: 0.28, options: .transitionCrossDissolve, animations: nil)
        }
    }

    /// A Kotlin exception that killed a previous launch is reported here, with
    /// the details copyable so a real cause replaces guessing.
    private func showLastKotlinCrashIfAny() {
        guard let crash = CinemaxDiagnostics.shared.lastCrash(), !crash.isEmpty else { return }
        let alert = UIAlertController(
            title: "توقف CINEMAX سابقًا",
            message: String(crash.prefix(1_200)),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "نسخ التفاصيل", style: .default) { _ in
            UIPasteboard.general.string = crash
        })
        alert.addAction(UIAlertAction(title: "إغلاق", style: .cancel) { _ in
            CinemaxDiagnostics.shared.clear()
        })
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.window?.rootViewController?.present(alert, animated: true)
        }
    }
}

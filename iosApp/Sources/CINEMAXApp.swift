import UIKit
import SwiftUI
import CinemaxKit

/// UIKit entry point adopting the scene lifecycle. The window is created by the
/// scene delegate, which is what iOS expects from an app built with a current SDK.
@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        true
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

        showLastKotlinCrashIfAny()
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

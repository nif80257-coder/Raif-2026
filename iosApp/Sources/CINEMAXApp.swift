import UIKit
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

/// The shared Compose UI is the window's root controller. Nothing sits between
/// UIKit and it, so the layout, display density and safe-area insets all come
/// straight from the system and the interface always fills the screen.
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
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        #if canImport(Libmpv)
        DispatchQueue.main.async {
            CinemaxMPVRegistration.register()
        }
        #endif
        showLastKotlinCrashIfAny()
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

import BackgroundTasks
import ComposeApp
import Foundation

/// Runs the episode-release refresh while the app is in the background, through BGTaskScheduler.
enum BackgroundRefreshScheduler {
    static let identifier = "com.raif.stream.refresh"

    /// The earliest the system may start the task; iOS decides the actual time.
    private static let minimumInterval: TimeInterval = 6 * 60 * 60

    /// Must run before the app finishes launching.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handle(refreshTask)
        }
    }

    /// Asks the system for the next refresh; called when the app moves to the background.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: minimumInterval)
        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            NSLog("Background refresh could not be scheduled: \(error.localizedDescription)")
        }
    }

    private static func handle(_ task: BGAppRefreshTask) {
        // Queue the next run first, so a refresh that ends early still leaves one pending.
        schedule()
        let job = BackgroundRefreshBridgeKt.startBackgroundRefresh { success in
            task.setTaskCompleted(success: success.boolValue)
        }
        task.expirationHandler = {
            BackgroundRefreshBridgeKt.cancelBackgroundRefresh(job: job)
        }
    }
}

import XCTest

final class RaifUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testHomeScreenShowsCoreActions() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["ماذا تشاهد اليوم؟"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["searchButton"].exists)
        XCTAssertTrue(app.buttons["continueWatchingCard"].exists)
        XCTAssertTrue(app.buttons["downloadsButton"].exists)
        XCTAssertTrue(app.buttons["watchlistButton"].exists)
    }

    @MainActor
    func testFeaturedMediaIsAccessible() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.buttons["mediaCard_مسارات بعيدة"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["mediaCard_ليلة في المدينة"].exists)
    }

    @MainActor
    func testLaunchPerformance() throws {
        measure(metrics: [XCTApplicationLaunchMetric()]) {
            XCUIApplication().launch()
        }
    }
}

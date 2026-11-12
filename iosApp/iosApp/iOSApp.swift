import SwiftUI
import Shared

@main
struct iOSApp: App {
    /// One per process: a `let` on the `App`, so a SwiftUI re-render never rebuilds it.
    let graph = MainViewControllerKt.createAppGraph()

    var body: some Scene {
        WindowGroup {
            ContentView(graph: graph)
        }
    }
}

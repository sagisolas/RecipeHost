import UIKit
import SwiftUI
import Shared

private let pairingScheme = "recipehost"

struct ComposeView: UIViewControllerRepresentable {
    let graph: AppGraph

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(graph: graph)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    let graph: AppGraph

    var body: some View {
        ComposeView(graph: graph)
            .ignoresSafeArea()
            // `recipehost://pair` links, e.g. from the system Camera reading the in-game QR, warm or cold.
            .onOpenURL { url in
                if url.scheme == pairingScheme {
                    graph.pairing.start(uri: url.absoluteString)
                }
            }
    }
}

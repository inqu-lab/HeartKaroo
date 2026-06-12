import SwiftUI

@main
struct TriPlannerApp: App {
    @StateObject private var store = PlanStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .preferredColorScheme(.dark)
                .tint(.cyan)
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var store: PlanStore

    var body: some View {
        TabView {
            TodayView()
                .tabItem { Label("Today", systemImage: "heart.text.square") }
            PlanView()
                .tabItem { Label("Plan", systemImage: "calendar") }
            RacesView()
                .tabItem { Label("Races", systemImage: "flag.checkered") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gear") }
        }
        .task { await store.refresh() }
    }
}

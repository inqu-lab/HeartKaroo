import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var store: PlanStore
    @AppStorage("backendURL") private var backendURL = ""
    @AppStorage("athleteID") private var athleteID = ""
    @State private var apiKey = Keychain.load("intervalsAPIKey") ?? ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Backend") {
                    TextField("https://your-server.example", text: $backendURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section {
                    TextField("Athlete ID (e.g. i12345)", text: $athleteID)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("API key", text: $apiKey)
                        .onChange(of: apiKey) { _, newValue in
                            Keychain.save("intervalsAPIKey", value: newValue)
                        }
                } header: {
                    Text("intervals.icu")
                } footer: {
                    Text("Find both under intervals.icu → Settings → Developer Settings. The API key is stored in the iOS Keychain and sent only to your backend.")
                }
                Button("Test connection") {
                    Task { await store.refresh() }
                }
                if let error = store.errorMessage {
                    Text(error).foregroundStyle(.red).font(.caption)
                } else if store.readiness != nil {
                    Label("Connected", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                }
            }
            .navigationTitle("Settings")
        }
    }
}

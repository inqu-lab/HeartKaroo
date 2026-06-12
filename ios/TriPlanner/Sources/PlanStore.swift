import Foundation

@MainActor
final class PlanStore: ObservableObject {
    @Published var plan: Plan?
    @Published var readiness: Readiness?
    @Published var races: [Race] = []
    @Published var isLoading = false
    @Published var errorMessage: String?

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let client = try APIClient.fromSettings()
            races = try await client.races()
            readiness = try await client.readiness()
            plan = races.isEmpty ? nil : (try? await client.plan())  // 404 when no future race
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func addRace(_ race: Race) async {
        do {
            let client = try APIClient.fromSettings()
            _ = try await client.addRace(race)
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func deleteRace(_ race: Race) async {
        guard let id = race.id else { return }
        do {
            let client = try APIClient.fromSettings()
            try await client.deleteRace(id: id)
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

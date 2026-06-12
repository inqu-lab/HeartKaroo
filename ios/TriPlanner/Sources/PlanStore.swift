import Foundation

@MainActor
final class PlanStore: ObservableObject {
    @Published var plan: Plan?
    @Published var readiness: Readiness?
    @Published var forecast: Forecast?
    @Published var races: [Race] = []
    @Published var isLoading = false
    @Published var isSyncing = false
    @Published var syncMessage: String?
    @Published var errorMessage: String?

    func refresh() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let client = try APIClient.fromSettings()
            races = try await client.races()
            readiness = try await client.readiness()
            // 404 when no future race
            plan = races.isEmpty ? nil : (try? await client.plan())
            forecast = plan == nil ? nil : (try? await client.forecast())
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func syncToCalendar() async {
        isSyncing = true
        syncMessage = nil
        defer { isSyncing = false }
        do {
            let client = try APIClient.fromSettings()
            let result = try await client.syncToCalendar()
            syncMessage = "\(result.created) workouts sent for the next \(result.horizonDays) days"
        } catch {
            syncMessage = error.localizedDescription
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

import SwiftUI

struct RacesView: View {
    @EnvironmentObject var store: PlanStore
    @State private var showingAdd = false

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.races, id: \.listID) { race in
                    VStack(alignment: .leading) {
                        Text(race.name).font(.headline)
                        Text("\(race.distance.label) • \(race.day) • Priority \(race.priority)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .onDelete { offsets in
                    let races = offsets.map { store.races[$0] }
                    Task { for race in races { await store.deleteRace(race) } }
                }
                if store.races.isEmpty {
                    Text("No races yet. The plan targets your next race.")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Races")
            .toolbar {
                Button { showingAdd = true } label: { Image(systemName: "plus") }
            }
            .sheet(isPresented: $showingAdd) {
                AddRaceView()
            }
            .refreshable { await store.refresh() }
        }
    }
}

struct AddRaceView: View {
    @EnvironmentObject var store: PlanStore
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var day = Calendar.current.date(byAdding: .month, value: 3, to: .now) ?? .now
    @State private var distance: RaceDistance = .olympic
    @State private var priority = "A"

    var body: some View {
        NavigationStack {
            Form {
                TextField("Race name", text: $name)
                DatePicker("Date", selection: $day, in: Date.now..., displayedComponents: .date)
                Picker("Distance", selection: $distance) {
                    ForEach(RaceDistance.allCases) { d in
                        Text(d.label).tag(d)
                    }
                }
                Picker("Priority", selection: $priority) {
                    ForEach(["A", "B", "C"], id: \.self) { Text($0) }
                }
            }
            .navigationTitle("Add race")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let formatter = DateFormatter()
                        formatter.dateFormat = "yyyy-MM-dd"
                        let race = Race(
                            id: nil,
                            name: name,
                            day: formatter.string(from: day),
                            distance: distance,
                            priority: priority
                        )
                        Task {
                            await store.addRace(race)
                            dismiss()
                        }
                    }
                    .disabled(name.isEmpty)
                }
            }
        }
    }
}

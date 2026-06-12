import SwiftUI

struct RacesView: View {
    @EnvironmentObject var store: PlanStore
    @State private var showingAdd = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if store.races.isEmpty {
                        Text("No races yet. The plan targets your next race.")
                            .foregroundStyle(.secondary)
                            .card()
                    }
                    ForEach(store.races, id: \.listID) { race in
                        RaceCard(race: race) {
                            Task { await store.deleteRace(race) }
                        }
                    }
                }
                .padding(.horizontal)
            }
            .background(Theme.background)
            .navigationTitle("Races")
            .toolbar {
                Button { showingAdd = true } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.cyan)
                }
            }
            .sheet(isPresented: $showingAdd) {
                AddRaceView()
            }
            .refreshable { await store.refresh() }
        }
    }
}

struct RaceCard: View {
    let race: Race
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            VStack(spacing: 0) {
                if let days = race.daysToGo {
                    Text("\(days)")
                        .font(.system(.title2, design: .rounded).weight(.bold))
                        .foregroundStyle(.white)
                    Text("DAYS")
                        .font(.system(size: 9, weight: .semibold))
                        .tracking(1)
                        .foregroundStyle(.white.opacity(0.8))
                }
            }
            .frame(width: 60, height: 60)
            .background(Theme.accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text(race.name).font(.headline)
                Text("\(race.distance.label) • \(race.day)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Chip(text: "Priority \(race.priority)", color: race.priority == "A" ? .pink : .gray)
            }
            Spacer()
            Button(role: .destructive, action: onDelete) {
                Image(systemName: "trash")
                    .foregroundStyle(.secondary)
            }
        }
        .card()
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
            .scrollContentBackground(.hidden)
            .background(Theme.background)
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

import SwiftUI

struct PlanView: View {
    @EnvironmentObject var store: PlanStore

    var body: some View {
        NavigationStack {
            List {
                if let plan = store.plan {
                    Section {
                        VStack(alignment: .leading) {
                            Text(plan.race.name).font(.headline)
                            Text("\(plan.race.distance.label) • \(plan.race.day)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    ForEach(plan.weeks) { week in
                        Section("\(week.start) — \(week.phase.capitalized), \(week.targetHours, specifier: "%.1f") h") {
                            if week.sessions.isEmpty {
                                Text("No sessions").foregroundStyle(.secondary)
                            }
                            ForEach(week.sessions) { session in
                                SessionRow(session: session)
                            }
                        }
                    }
                } else {
                    Text("Add an upcoming race to generate a plan.")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Plan")
            .refreshable { await store.refresh() }
        }
    }
}

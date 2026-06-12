import SwiftUI

struct PlanView: View {
    @EnvironmentObject var store: PlanStore

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let plan = store.plan {
                        RaceHeaderCard(race: plan.race)
                        ForEach(plan.weeks) { week in
                            WeekCard(week: week)
                        }
                    } else {
                        Text("Add an upcoming race to generate a plan.")
                            .foregroundStyle(.secondary)
                            .card()
                    }
                }
                .padding(.horizontal)
            }
            .background(Theme.background)
            .navigationTitle("Plan")
            .refreshable { await store.refresh() }
        }
    }
}

struct RaceHeaderCard: View {
    let race: Race

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(race.distance.label.uppercased(), systemImage: "flag.checkered")
                .font(.caption.weight(.bold))
                .tracking(1)
                .foregroundStyle(.white.opacity(0.85))
            Text(race.name)
                .font(.title2.bold())
                .foregroundStyle(.white)
            if let days = race.daysToGo {
                Text("\(days) days to go")
                    .font(.system(.title3, design: .rounded).weight(.semibold))
                    .foregroundStyle(.white.opacity(0.9))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(Theme.accent, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct WeekCard: View {
    let week: WeekPlan

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Chip(text: week.phase.capitalized, color: Theme.phaseColor(week.phase))
                Text("Week of \(week.start)")
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(String(format: "%.1f h", week.targetHours))
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            if week.sessions.isEmpty {
                Text("No sessions").font(.caption).foregroundStyle(.secondary)
            }
            ForEach(week.sessions) { session in
                HStack(spacing: 12) {
                    SportIcon(sport: session.sport, size: 32)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(session.title).font(.subheadline.weight(.medium))
                        Text(session.intensity.capitalized)
                            .font(.caption2)
                            .foregroundStyle(
                                session.adjustment == .asPlanned ? .secondary : Color.orange)
                    }
                    Spacer()
                    if session.durationMin > 0 {
                        Text("\(session.durationMin) min")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .card()
    }
}

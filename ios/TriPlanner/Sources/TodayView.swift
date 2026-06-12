import SwiftUI

struct TodayView: View {
    @EnvironmentObject var store: PlanStore

    private var todayString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    private var todaysSessions: [Session] {
        store.plan?.weeks.flatMap(\.sessions).filter { $0.day == todayString } ?? []
    }

    var body: some View {
        NavigationStack {
            List {
                if let error = store.errorMessage {
                    Text(error).foregroundStyle(.red)
                }
                if let readiness = store.readiness {
                    Section("Readiness") {
                        ReadinessGauge(readiness: readiness)
                        ForEach(readiness.explanation, id: \.self) { line in
                            Text(line).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Section("Today's training") {
                    if todaysSessions.isEmpty {
                        Text(store.plan == nil
                            ? "Add a race to generate a plan."
                            : "Nothing scheduled today — recover well.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(todaysSessions) { session in
                        SessionRow(session: session)
                    }
                }
            }
            .navigationTitle("Today")
            .refreshable { await store.refresh() }
            .overlay { if store.isLoading { ProgressView() } }
        }
    }
}

struct ReadinessGauge: View {
    let readiness: Readiness

    private var color: Color {
        switch readiness.score {
        case 65...: return .green
        case 50..<65: return .yellow
        case 35..<50: return .orange
        default: return .red
        }
    }

    private var advice: String {
        switch readiness.adjustment {
        case .asPlanned: return "Train as planned"
        case .reduced: return "Reduce today's load"
        case .easy: return "Easy aerobic only"
        case .rest: return "Rest day recommended"
        }
    }

    var body: some View {
        HStack(spacing: 16) {
            Gauge(value: readiness.score, in: 0...100) {
                EmptyView()
            } currentValueLabel: {
                Text("\(Int(readiness.score))").font(.headline)
            }
            .gaugeStyle(.accessoryCircular)
            .tint(color)
            VStack(alignment: .leading) {
                Text(advice).font(.headline)
                Text("Based on wellness and how training has felt")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct SessionRow: View {
    let session: Session

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: session.sport.symbol)
                .font(.title3)
                .frame(width: 32)
                .foregroundStyle(session.adjustment == .asPlanned ? Color.accentColor : .orange)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(session.title).font(.headline)
                    Spacer()
                    if session.durationMin > 0 {
                        Text("\(session.durationMin) min")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                Text(session.description).font(.caption).foregroundStyle(.secondary)
                if session.adjustment != .asPlanned {
                    Label("Adjusted for readiness", systemImage: "wand.and.stars")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

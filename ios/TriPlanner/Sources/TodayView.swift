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
            ScrollView {
                VStack(spacing: 16) {
                    if let error = store.errorMessage {
                        Text(error).foregroundStyle(.red).card()
                    }
                    if let readiness = store.readiness {
                        ReadinessCard(readiness: readiness)
                    }
                    HStack {
                        Text("Today's training").font(.title3.bold())
                        Spacer()
                    }
                    .padding(.top, 8)
                    if todaysSessions.isEmpty {
                        Text(store.plan == nil
                            ? "Add a race to generate a plan."
                            : "Nothing scheduled today — recover well.")
                            .foregroundStyle(.secondary)
                            .card()
                    }
                    ForEach(todaysSessions) { session in
                        SessionCard(session: session)
                    }
                }
                .padding(.horizontal)
            }
            .background(Theme.background)
            .navigationTitle("Today")
            .refreshable { await store.refresh() }
            .overlay { if store.isLoading { ProgressView() } }
        }
    }
}

struct ReadinessCard: View {
    let readiness: Readiness

    private var advice: String {
        switch readiness.adjustment {
        case .asPlanned: return "Train as planned"
        case .reduced: return "Reduce today's load"
        case .easy: return "Easy aerobic only"
        case .rest: return "Rest day recommended"
        }
    }

    private var components: [(String, Double)] {
        [
            ("HRV", readiness.hrvScore),
            ("Resting HR", readiness.restingHrScore),
            ("Sleep", readiness.sleepScore),
            ("Wellness", readiness.subjectiveScore),
            ("Training feel", readiness.perceivedTrainingScore),
        ].compactMap { label, value in value.map { (label, $0) } }
    }

    var body: some View {
        VStack(spacing: 20) {
            ReadinessRing(score: readiness.score)
            VStack(spacing: 4) {
                Text(advice).font(.title3.bold())
                Text("From wellness and how training has felt")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            VStack(spacing: 10) {
                ForEach(components, id: \.0) { label, value in
                    HStack {
                        Text(label)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .frame(width: 92, alignment: .leading)
                        ScoreBar(value: value)
                        Text("\(Int(value))")
                            .font(.caption.monospacedDigit().weight(.semibold))
                            .frame(width: 28, alignment: .trailing)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct ReadinessRing: View {
    let score: Double

    var body: some View {
        ZStack {
            Circle()
                .stroke(.white.opacity(0.08), lineWidth: 14)
            Circle()
                .trim(from: 0, to: score / 100)
                .stroke(
                    AngularGradient(
                        colors: [Theme.scoreColor(score).opacity(0.5), Theme.scoreColor(score)],
                        center: .center,
                        startAngle: .degrees(0),
                        endAngle: .degrees(360 * score / 100)
                    ),
                    style: StrokeStyle(lineWidth: 14, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text("\(Int(score))")
                    .font(.system(size: 46, weight: .bold, design: .rounded))
                Text("READINESS")
                    .font(.system(size: 10, weight: .semibold))
                    .tracking(1.5)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: 150, height: 150)
    }
}

struct ScoreBar: View {
    let value: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.08))
                Capsule()
                    .fill(Theme.scoreColor(value))
                    .frame(width: geo.size.width * value / 100)
            }
        }
        .frame(height: 6)
    }
}

struct SessionCard: View {
    let session: Session

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            SportIcon(sport: session.sport)
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(session.title).font(.headline)
                    Spacer()
                    if session.durationMin > 0 {
                        Text("\(session.durationMin) min")
                            .font(.subheadline.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
                Text(session.description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                HStack(spacing: 6) {
                    Chip(text: session.intensity.capitalized,
                         color: Theme.phaseColor(session.phase))
                    if session.adjustment != .asPlanned {
                        Chip(text: "Adjusted for readiness", color: .orange)
                    }
                }
            }
        }
        .card()
    }
}

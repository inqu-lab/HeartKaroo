import Charts
import SwiftUI

struct PlanView: View {
    @EnvironmentObject var store: PlanStore

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let plan = store.plan {
                        RaceHeaderCard(race: plan.race)
                        if let forecast = store.forecast {
                            ForecastCard(forecast: forecast)
                        }
                        SyncCard()
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

struct ForecastCard: View {
    let forecast: Forecast

    private var ftpPoints: [(Int, Double)] {
        forecast.weekly.enumerated().compactMap { i, snapshot in
            snapshot.ftp.map { (i + 1, $0) }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(systemName: "chart.line.uptrend.xyaxis")
                    .foregroundStyle(.cyan)
                Text("Projected at race day").font(.headline)
            }
            if !ftpPoints.isEmpty {
                Chart(ftpPoints, id: \.0) { week, ftp in
                    LineMark(x: .value("Week", week), y: .value("FTP", ftp))
                        .foregroundStyle(Theme.accent)
                        .interpolationMethod(.catmullRom)
                        .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round))
                    AreaMark(x: .value("Week", week), y: .value("FTP", ftp))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [.cyan.opacity(0.25), .clear],
                                startPoint: .top, endPoint: .bottom))
                        .interpolationMethod(.catmullRom)
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .frame(height: 110)
            }
            VStack(spacing: 8) {
                if let now = forecast.current.ftp, let race = forecast.raceDay.ftp {
                    ForecastRow(
                        label: "FTP",
                        now: "\(Int(now)) W", race: "\(Int(race)) W",
                        improved: race > now)
                }
                if let now = forecast.current.runThresholdPace,
                   let race = forecast.raceDay.runThresholdPace {
                    ForecastRow(
                        label: "Run threshold",
                        now: formatPace(now, unit: "/km"), race: formatPace(race, unit: "/km"),
                        improved: race < now)
                }
                if let now = forecast.current.swimThresholdPace,
                   let race = forecast.raceDay.swimThresholdPace {
                    ForecastRow(
                        label: "Swim pace",
                        now: formatPace(now, unit: "/100m"), race: formatPace(race, unit: "/100m"),
                        improved: race < now)
                }
            }
            ForEach(forecast.explanation, id: \.self) { line in
                Text(line).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .card()
    }
}

struct ForecastRow: View {
    let label: String
    let now: String
    let race: String
    let improved: Bool

    var body: some View {
        HStack {
            Text(label).font(.subheadline).foregroundStyle(.secondary)
            Spacer()
            Text(now).font(.subheadline.monospacedDigit())
            Image(systemName: "arrow.right")
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(race)
                .font(.subheadline.monospacedDigit().weight(.bold))
                .foregroundStyle(improved ? .green : .orange)
        }
    }
}

struct SyncCard: View {
    @EnvironmentObject var store: PlanStore

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button {
                Task { await store.syncToCalendar() }
            } label: {
                HStack {
                    if store.isSyncing {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "applewatch.radiowaves.left.and.right")
                    }
                    Text("Send workouts to Garmin")
                        .font(.subheadline.weight(.semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Theme.accent, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .disabled(store.isSyncing)
            Text("Pushes the next two weeks to your intervals.icu calendar as structured workouts. With Garmin connected to intervals.icu, each one appears on your watch on its day.")
                .font(.caption2)
                .foregroundStyle(.secondary)
            if let message = store.syncMessage {
                Label(message, systemImage: "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundStyle(.green)
            }
        }
        .card()
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

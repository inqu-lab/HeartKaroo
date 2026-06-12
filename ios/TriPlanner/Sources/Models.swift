import Foundation

// Codable mirrors of the backend models (backend/app/models.py).

enum Sport: String, Codable {
    case swim, bike, run, brick, rest

    var symbol: String {
        switch self {
        case .swim: return "figure.pool.swim"
        case .bike: return "figure.outdoor.cycle"
        case .run: return "figure.run"
        case .brick: return "arrow.triangle.2.circlepath"
        case .rest: return "bed.double"
        }
    }
}

enum RaceDistance: String, Codable, CaseIterable, Identifiable {
    case sprint, olympic, half, full
    var id: String { rawValue }

    var label: String {
        switch self {
        case .sprint: return "Sprint"
        case .olympic: return "Olympic"
        case .half: return "Half (70.3)"
        case .full: return "Full (140.6)"
        }
    }
}

enum Adjustment: String, Codable {
    case asPlanned = "as_planned"
    case reduced, easy, rest
}

struct Race: Codable, Identifiable, Hashable {
    var id: Int?
    var name: String
    var day: String  // yyyy-MM-dd
    var distance: RaceDistance
    var priority: String

    // Stable identity for SwiftUI lists even before the backend assigns an id.
    var listID: String { id.map(String.init) ?? "\(name)-\(day)" }

    var daysToGo: Int? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let raceDay = formatter.date(from: day) else { return nil }
        return Calendar.current.dateComponents(
            [.day], from: Calendar.current.startOfDay(for: .now), to: raceDay
        ).day
    }
}

struct Readiness: Codable {
    var day: String
    var score: Double
    var hrvScore: Double?
    var restingHrScore: Double?
    var sleepScore: Double?
    var subjectiveScore: Double?
    var perceivedTrainingScore: Double?
    var adjustment: Adjustment
    var explanation: [String]

    enum CodingKeys: String, CodingKey {
        case day, score, adjustment, explanation
        case hrvScore = "hrv_score"
        case restingHrScore = "resting_hr_score"
        case sleepScore = "sleep_score"
        case subjectiveScore = "subjective_score"
        case perceivedTrainingScore = "perceived_training_score"
    }
}

struct Session: Codable, Identifiable {
    var day: String
    var sport: Sport
    var title: String
    var description: String
    var durationMin: Int
    var intensity: String
    var phase: String
    var adjustment: Adjustment

    var id: String { "\(day)-\(sport.rawValue)-\(title)" }

    enum CodingKeys: String, CodingKey {
        case day, sport, title, description, intensity, phase, adjustment
        case durationMin = "duration_min"
    }
}

struct WeekPlan: Codable, Identifiable {
    var start: String
    var phase: String
    var targetHours: Double
    var sessions: [Session]

    var id: String { start }

    enum CodingKeys: String, CodingKey {
        case start, phase, sessions
        case targetHours = "target_hours"
    }
}

struct FitnessSnapshot: Codable {
    var day: String
    var ftp: Double?
    var runThresholdPace: Double?  // seconds per km
    var swimThresholdPace: Double?  // seconds per 100m
    var ctl: Double?

    enum CodingKeys: String, CodingKey {
        case day, ftp, ctl
        case runThresholdPace = "run_threshold_pace"
        case swimThresholdPace = "swim_threshold_pace"
    }
}

struct Forecast: Codable {
    var race: Race
    var current: FitnessSnapshot
    var raceDay: FitnessSnapshot
    var weekly: [FitnessSnapshot]
    var explanation: [String]

    enum CodingKeys: String, CodingKey {
        case race, current, weekly, explanation
        case raceDay = "race_day"
    }
}

struct SyncResult: Codable {
    var created: Int
    var deleted: Int
    var horizonDays: Int

    enum CodingKeys: String, CodingKey {
        case created, deleted
        case horizonDays = "horizon_days"
    }
}

/// "4:30 /km" or "1:45 /100m" from seconds.
func formatPace(_ seconds: Double, unit: String) -> String {
    let total = Int(seconds.rounded())
    return String(format: "%d:%02d %@", total / 60, total % 60, unit)
}

struct Plan: Codable {
    var race: Race
    var generatedOn: String
    var readiness: Readiness?
    var weeks: [WeekPlan]

    enum CodingKeys: String, CodingKey {
        case race, readiness, weeks
        case generatedOn = "generated_on"
    }
}

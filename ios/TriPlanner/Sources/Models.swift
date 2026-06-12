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

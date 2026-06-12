import Foundation

enum APIError: LocalizedError {
    case notConfigured
    case server(Int, String)

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Set the backend URL, athlete ID and intervals.icu API key in Settings."
        case .server(let code, let detail):
            return "Server error \(code): \(detail)"
        }
    }
}

struct APIClient {
    let baseURL: URL
    let athleteID: String
    let apiKey: String

    static func fromSettings() throws -> APIClient {
        let defaults = UserDefaults.standard
        guard
            let urlString = defaults.string(forKey: "backendURL"),
            let url = URL(string: urlString),
            let athleteID = defaults.string(forKey: "athleteID"), !athleteID.isEmpty,
            let apiKey = Keychain.load("intervalsAPIKey"), !apiKey.isEmpty
        else { throw APIError.notConfigured }
        return APIClient(baseURL: url, athleteID: athleteID, apiKey: apiKey)
    }

    private func request(_ path: String, method: String = "GET", body: Data? = nil) -> URLRequest {
        var req = URLRequest(url: baseURL.appendingPathComponent(path))
        req.httpMethod = method
        req.httpBody = body
        req.setValue(athleteID, forHTTPHeaderField: "X-Athlete-Id")
        req.setValue(apiKey, forHTTPHeaderField: "X-Api-Key")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return req
    }

    private func send<T: Decodable>(_ req: URLRequest) async throws -> T {
        let (data, response) = try await URLSession.shared.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let detail = String(data: data, encoding: .utf8) ?? ""
            throw APIError.server(status, detail)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    func readiness() async throws -> Readiness {
        try await send(request("readiness"))
    }

    func plan() async throws -> Plan {
        try await send(request("plan"))
    }

    func races() async throws -> [Race] {
        try await send(request("races"))
    }

    func addRace(_ race: Race) async throws -> Race {
        let body = try JSONEncoder().encode(race)
        return try await send(request("races", method: "POST", body: body))
    }

    func deleteRace(id: Int) async throws {
        let (data, response) = try await URLSession.shared.data(
            for: request("races/\(id)", method: "DELETE"))
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            throw APIError.server(status, String(data: data, encoding: .utf8) ?? "")
        }
    }
}

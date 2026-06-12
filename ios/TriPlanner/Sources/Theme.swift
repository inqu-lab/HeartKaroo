import SwiftUI

/// Dark, card-based visual language shared by all screens.
enum Theme {
    static let background = Color(red: 0.05, green: 0.06, blue: 0.09)
    static let card = Color(red: 0.11, green: 0.13, blue: 0.17)

    static let accent = LinearGradient(
        colors: [.cyan, .blue],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

    static func sportGradient(_ sport: Sport) -> LinearGradient {
        let colors: [Color]
        switch sport {
        case .swim: colors = [.cyan, .blue]
        case .bike: colors = [.orange, .pink]
        case .run: colors = [.green, .mint]
        case .brick: colors = [.purple, .indigo]
        case .rest: colors = [.gray, Color(white: 0.35)]
        }
        return LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    static func scoreColor(_ score: Double) -> Color {
        switch score {
        case 65...: return .green
        case 50..<65: return .yellow
        case 35..<50: return .orange
        default: return .red
        }
    }

    static func phaseColor(_ phase: String) -> Color {
        switch phase {
        case "base": return .blue
        case "build": return .purple
        case "peak": return .pink
        case "taper": return .mint
        case "recovery": return .green
        default: return .gray
        }
    }
}

struct CardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

extension View {
    func card() -> some View { modifier(CardModifier()) }
}

/// Small rounded label, e.g. for intensity or phase.
struct Chip: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.18), in: Capsule())
            .foregroundStyle(color)
    }
}

/// Sport icon in a gradient rounded square.
struct SportIcon: View {
    let sport: Sport
    var size: CGFloat = 44

    var body: some View {
        Image(systemName: sport.symbol)
            .font(.system(size: size * 0.42, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(
                Theme.sportGradient(sport),
                in: RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
            )
    }
}

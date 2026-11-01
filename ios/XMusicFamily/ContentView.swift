import SwiftUI

/// iOS-specific starting screen. Stitch HTML will replace this placeholder
/// once its assets are bundled in the Xcode target.
struct ContentView: View {
    private let background = Color(red: 11 / 255, green: 15 / 255, blue: 18 / 255)
    private let accent = Color(red: 32 / 255, green: 223 / 255, blue: 100 / 255)

    var body: some View {
        ZStack {
            background.ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "music.note.list")
                    .font(.system(size: 48))
                    .foregroundStyle(accent)
                Text("XMusic Family")
                    .font(.title.bold())
                    .foregroundStyle(.white)
                Text("iPhone sürümü hazırlanıyor")
                    .font(.subheadline)
                    .foregroundStyle(.gray)
                Text("Müzik kitaplığınız ve aile profilleriniz için iOS altyapısı.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(0.8))
            }
            .padding(24)
        }
    }
}

#Preview {
    ContentView()
}

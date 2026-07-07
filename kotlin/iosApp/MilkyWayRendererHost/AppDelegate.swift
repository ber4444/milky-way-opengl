import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let screen = UIScreen.main
        window = UIWindow(frame: screen.bounds)

        let scale = screen.scale
        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let density = scale >= 2 ? Float(scale) : 1.0

        let glView = MilkyWayGLView(frame: screen.bounds, density: density, isTablet: isPad)
        glView.backgroundColor = .black
        window?.rootViewController = UIViewController()
        window?.rootViewController?.view = glView
        window?.makeKeyAndVisible()

        // Overlay buttons (same UX as Android).
        let caption = UILabel()
        caption.textColor = UIColor(white: 0.91, alpha: 1)
        caption.font = .systemFont(ofSize: 11)
        caption.numberOfLines = 0
        caption.backgroundColor = UIColor(white: 0, alpha: 0.5)
        caption.layer.cornerRadius = 6; caption.layer.masksToBounds = true
        caption.isHidden = true
        caption.frame = CGRect(x: 16, y: 96, width: glView.bounds.width - 32, height: 0)
        caption.autoresizingMask = [.flexibleWidth, .flexibleBottomMargin]
        glView.addSubview(caption)

        func makeButton(_ title: String) -> UIButton {
            let b = UIButton(type: .system)
            b.setTitle(title, for: .normal)
            b.setTitleColor(.white, for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 13)
            b.backgroundColor = UIColor(white: 0, alpha: 0.4)
            b.layer.cornerRadius = 8
            return b
        }
        let sunBtn = makeButton(" ⊙ Sun ")
        let wondersBtn = makeButton(" ✦ Wonders ")
        let stack = UIStackView(arrangedSubviews: [sunBtn, wondersBtn])
        stack.axis = .horizontal; stack.spacing = 6
        stack.frame = CGRect(x: 16, y: 56, width: glView.bounds.width - 32, height: 36)
        stack.autoresizingMask = [.flexibleWidth, .flexibleBottomMargin]
        glView.addSubview(stack)

        func updateCaption() {
            var lines: [String] = []
            if glView.renderer.annotationsEnabled { lines.append("☉ Our address: ~8.2 kpc from the galactic center, on the Local (Orion) Spur.") }
            if glView.renderer.wondersOverlay { lines.append("✦ Galactic habitable zone (6.5–9.8 kpc): where a star can host a stable, heavy-element-rich, low-radiation planetary system.") }
            caption.text = lines.joined(separator: "\n\n")
            caption.isHidden = lines.isEmpty
            caption.sizeToFit()
            var f = caption.frame; f.origin.x = 16; f.size.width = glView.bounds.width - 32
            caption.frame = f
        }
        sunBtn.addAction(UIAction { _ in
            glView.renderer.annotationsEnabled = !glView.renderer.annotationsEnabled; updateCaption()
        }, for: .touchUpInside)
        wondersBtn.addAction(UIAction { _ in
            glView.renderer.wondersOverlay = !glView.renderer.wondersOverlay; updateCaption()
        }, for: .touchUpInside)

        // Start the render loop once the view is laid out.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            glView.startRendering()
        }

        // Phase 4: lifecycle — tear down/rebuild GL on background/foreground.
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification,
                                               object: nil, queue: .main) { _ in
            glView.onAppBackground()
        }
        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification,
                                               object: nil, queue: .main) { _ in
            glView.onAppForeground()
        }
        return true
    }
}

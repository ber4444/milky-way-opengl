import UIKit
import GLKit
import OpenGLES
import MetalANGLE
import MilkyWayRenderer
import CoreGraphics

/// A UIView backed by a CAEAGLLayer (when using system GL) or by MetalANGLE's
/// MGLLayer (when using MetalANGLE). Owns the GL context, drives a CADisplayLink
/// render loop, and forwards gestures to the shared ArcballCamera.
///
/// Phase 5: switched from EAGLContext to MGLContext (MetalANGLE). The GL calls
/// in the C shim are identical; only the context that makes them valid changes.
class MilkyWayGLView: UIView {
    private var mglContext: MGLContext!
    private var displayLink: CADisplayLink?
    private var glReady = false

    let renderer: MilkyWayRenderer
    let camera: ArcballCamera
    private let density: Float
    private let isTablet: Bool
    private var assets: BundleAssetProvider!

    private var panPrev = CGPoint.zero
    private var pinchPrev: Float = 1
    private var rotatePrev: Float = 0

    init(frame: CGRect, density: Float, isTablet: Bool) {
        self.density = density
        self.isTablet = isTablet
        self.renderer = MilkyWayRenderer(gl: GlIos())
        self.camera = ArcballCamera()
        super.init(frame: frame)
        // Render at native resolution: without this the MGLLayer stays at
        // contentsScale 1.0 and GL draws at point (not pixel) resolution,
        // then gets blur-upscaled by the compositor. The original app drew
        // into a Retina-scaled GLKView; this is the equivalent.
        contentScaleFactor = UIScreen.main.scale
        layer.contentsScale = UIScreen.main.scale
        setup()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func setup() {
        // MetalANGLE: create a GL-over-Metal context and bind it to this view's layer.
        mglContext = MGLContext(api: MGLRenderingAPI(rawValue: 2))
        let mglLayer = self.layer as! MGLLayer
        MGLContext.setCurrent(mglContext, for: mglLayer)

        assets = BundleAssetProvider()
        _ = renderer.doInit(assets: assets, density: density, isTablet: isTablet)

        let pan = UIPanGestureRecognizer(target: self, action: #selector(onPan(_:)))
        pan.minimumNumberOfTouches = 1; pan.maximumNumberOfTouches = 1
        addGestureRecognizer(pan)
        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(onPinch(_:)))
        addGestureRecognizer(pinch)
        let rot = UIRotationGestureRecognizer(target: self, action: #selector(onRotate(_:)))
        addGestureRecognizer(rot)
    }

    override class var layerClass: AnyClass { return MGLLayer.self }

    func setupGL() {
        let mglLayer = self.layer as! MGLLayer
        MGLContext.setCurrent(mglContext, for: mglLayer)
        NSLog("[glview] bounds=%@ contentScaleFactor=%.2f layer.contentsScale=%.2f drawableSize=%@ screenScale=%.2f",
              NSCoder.string(for: bounds), contentScaleFactor, mglLayer.contentsScale,
              NSCoder.string(for: mglLayer.drawableSize), UIScreen.main.scale)
        _ = renderer.onGlContextCreated()
        camera.setViewportWidthDp(widthDp: Float(bounds.width))
    }

    func startRendering() {
        let link = CADisplayLink(target: self, selector: #selector(onFrame))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }
    func stopRendering() { displayLink?.invalidate(); displayLink = nil }

    @objc private func onFrame() {
        let mglLayer = self.layer as! MGLLayer
        MGLContext.setCurrent(mglContext, for: mglLayer)
        let nowNs = Int64(CACurrentMediaTime() * 1e9)
        camera.update(nowNanos: nowNs)

        let mv = KotlinFloatArray(size: 16)
        let proj = KotlinFloatArray(size: 16)
        camera.modelViewMatrix(out: mv)
        camera.projectionMatrix(out: proj, width: Float(bounds.width), height: Float(bounds.height))
        // Viewport must be in drawable pixels, not points — gl_PointSize and
        // glViewport both operate in framebuffer pixels.
        let ds = mglLayer.drawableSize
        renderer.setFrame(modelView: mv, projection: proj, scale: camera.userScale(),
                          w: Int32(ds.width), h: Int32(ds.height))

        // Clear and draw. MetalANGLE's default framebuffer is managed by MGLLayer.
        glClearColor(0, 0, 0, 1)
        glClear(GLbitfield(GL_COLOR_BUFFER_BIT))
        renderer.draw(defaultFbo: 0)
        // Present via MetalANGLE's layer (static, not instance).
        mglContext.present(mglLayer)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if !glReady { glReady = true; setupGL() }
    }

    // MARK: - Context-loss lifecycle (Phase 4)
    func onAppBackground() { stopRendering() }
    func onAppForeground() {
        let mglLayer = self.layer as! MGLLayer
        MGLContext.setCurrent(mglContext, for: mglLayer)
        _ = renderer.onGlContextCreated()
        startRendering()
    }

    // MARK: - Gestures (same as before)
    @objc private func onPan(_ g: UIPanGestureRecognizer) {
        if g.state == .began { panPrev = .zero; return }
        if g.state == .changed || g.state == .ended {
            let t = g.translation(in: self)
            let dx = Float(t.x - panPrev.x)
            let dy = Float(t.y - panPrev.y)
            panPrev = t
            camera.pan(dxDp: dx, dyDp: dy, phase: Int32(MilkyWayConventions().PHASE_CHANGED),
                       velX: 0, velY: 0)
        }
        if g.state == .ended {
            let v = g.velocity(in: self)
            camera.pan(dxDp: 0, dyDp: 0, phase: Int32(MilkyWayConventions().PHASE_ENDED),
                       velX: Float(v.x), velY: Float(v.y))
        }
    }

    @objc private func onRotate(_ g: UIRotationGestureRecognizer) {
        if g.state == .began { rotatePrev = 0; return }
        if g.state == .changed || g.state == .ended {
            let dr = Float(g.rotation) - rotatePrev
            rotatePrev = Float(g.rotation)
            camera.roll(radians: dr, phase: Int32(MilkyWayConventions().PHASE_CHANGED), velocityRadS: 0)
        }
        if g.state == .ended {
            camera.roll(radians: 0, phase: Int32(MilkyWayConventions().PHASE_ENDED),
                        velocityRadS: Float(g.velocity))
        }
    }

    @objc private func onPinch(_ g: UIPinchGestureRecognizer) {
        if g.state == .began { pinchPrev = 1; return }
        if g.state == .changed || g.state == .ended {
            let s = Float(g.scale)
            camera.pinch(scaleStep: s / pinchPrev, phase: Int32(MilkyWayConventions().PHASE_CHANGED), velocity: 0)
            pinchPrev = s
        }
        if g.state == .ended {
            camera.pinch(scaleStep: 1, phase: Int32(MilkyWayConventions().PHASE_ENDED),
                         velocity: Float(g.velocity))
        }
    }
}

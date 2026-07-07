import UIKit
import MilkyWayRenderer

/// Wraps a Bundle-backed AssetProvider for the KMP renderer. Builds
/// KotlinByteArray by converting Data → [Int8] → KotlinByteArray via the
/// Kotlin/Native stdlib's NSArray bridge.
class BundleAssetProvider: NSObject, AssetProvider {
    func loadBytes(name: String) -> KotlinByteArray {
        guard let url = Bundle.main.url(forResource: (name as NSString).deletingPathExtension,
                                        withExtension: (name as NSString).pathExtension),
              let data = try? Data(contentsOf: url) else {
            fatalError("asset not found: \(name)")
        }
        return data.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) -> KotlinByteArray in
            let bytes = ptr.bindMemory(to: Int8.self)
            return KotlinByteArray(size: Int32(data.count)) { idx in
                KotlinByte(value: bytes[Int(idx)])
            }
        }
    }
    func loadText(name: String) -> String {
        guard let url = Bundle.main.url(forResource: (name as NSString).deletingPathExtension,
                                        withExtension: (name as NSString).pathExtension),
              let data = try? Data(contentsOf: url),
              let s = String(data: data, encoding: .utf8) else {
            fatalError("text asset not found: \(name)")
        }
        return s
    }
}

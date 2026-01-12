import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    // Use a stable canonical name so common code can reliably detect iOS
    override val name: String = "iOS"
}

actual fun getPlatform(): Platform = IOSPlatform()
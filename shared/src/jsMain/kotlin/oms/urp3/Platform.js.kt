package oms.urp3

class JsPlatform : oms.urp3.Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): oms.urp3.Platform = _root_ide_package_.oms.urp3.JsPlatform()
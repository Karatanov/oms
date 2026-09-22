package oms.umitaf

class JsPlatform : oms.umitaf.Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): oms.umitaf.Platform = _root_ide_package_.oms.umitaf.JsPlatform()
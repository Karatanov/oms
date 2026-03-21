package oms.ufsi

class JsPlatform : oms.ufsi.Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): oms.ufsi.Platform = _root_ide_package_.oms.ufsi.JsPlatform()
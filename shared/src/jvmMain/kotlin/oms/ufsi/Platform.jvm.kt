package oms.ufsi

class JVMPlatform : oms.ufsi.Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): oms.ufsi.Platform = _root_ide_package_.oms.ufsi.JVMPlatform()
package oms.umitaf

class JVMPlatform : oms.umitaf.Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): oms.umitaf.Platform = _root_ide_package_.oms.umitaf.JVMPlatform()
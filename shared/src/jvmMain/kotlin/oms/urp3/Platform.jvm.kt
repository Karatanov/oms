package oms.urp3

class JVMPlatform : oms.urp3.Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): oms.urp3.Platform = _root_ide_package_.oms.urp3.JVMPlatform()
package oms.urp3

class WasmPlatform : oms.urp3.Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): oms.urp3.Platform = _root_ide_package_.oms.urp3.WasmPlatform()
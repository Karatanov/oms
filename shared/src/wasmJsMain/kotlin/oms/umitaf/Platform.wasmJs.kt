package oms.umitaf

class WasmPlatform : oms.umitaf.Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): oms.umitaf.Platform = _root_ide_package_.oms.umitaf.WasmPlatform()
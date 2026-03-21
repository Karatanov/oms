package oms.ufsi

class WasmPlatform : oms.ufsi.Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): oms.ufsi.Platform = _root_ide_package_.oms.ufsi.WasmPlatform()
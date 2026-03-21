package oms.ufsi

class Greeting {
    private val platform = _root_ide_package_.oms.ufsi.getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}
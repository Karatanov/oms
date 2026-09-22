package oms.umitaf

class Greeting {
    private val platform = _root_ide_package_.oms.umitaf.getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}
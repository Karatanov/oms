package oms.urp3

class Greeting {
    private val platform = _root_ide_package_.oms.urp3.getPlatform()

    fun greet(): String {
        return "Hello, ${platform.name}!"
    }
}
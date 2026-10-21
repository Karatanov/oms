package oms.urp3

interface Platform {
    val name: String
}

expect fun getPlatform(): oms.urp3.Platform
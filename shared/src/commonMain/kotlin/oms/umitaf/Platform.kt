package oms.umitaf

interface Platform {
    val name: String
}

expect fun getPlatform(): oms.umitaf.Platform
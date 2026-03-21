package oms.ufsi

interface Platform {
    val name: String
}

expect fun getPlatform(): oms.ufsi.Platform
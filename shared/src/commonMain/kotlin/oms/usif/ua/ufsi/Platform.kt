package oms.usif.ua.ufsi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
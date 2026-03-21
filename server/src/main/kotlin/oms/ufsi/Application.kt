package oms.ufsi

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import oms.ufsi.module

fun main() {
    embeddedServer(
        Netty,
        port = _root_ide_package_.oms.ufsi.SERVER_PORT,
        host = "0.0.0.0",
        module = Application::module
    )
        .start(wait = true)
}

fun Application.module() {
    routing {
        get("/") {
            call.respondText("Ktor: ${_root_ide_package_.oms.ufsi.Greeting().greet()}")
        }
    }
}
package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.send
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondOutputStream
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.isActive
import java.io.PrintStream
import java.util.*
import kotlin.collections.HashMap

data class PaintRequest(val x: Int, val y: Int, val color: Int)

object Unknown

class RequestException(errorMessage: String) : Exception(errorMessage)

val board: Array<Array<Int>> = Array(400) {
    Array(800) {
        2
    }
}

lateinit var whiteList: List<String>
val timer: MutableMap<String, Long> = HashMap()

fun loadWhiteList() {
    whiteList =
        String(Unknown::class.java.getResourceAsStream("/whitelist.txt").readBytes()).lines().filter { it.isNotBlank() }
}

fun main() {
    loadWhiteList()
    embeddedServer(Netty, 8080) {
        install(WebSockets)

        routing {
            get("paintBoard/board") {
                buildString {
                    board.forEach { line ->
                        line.forEach {
                            append(it.toString(32).toUpperCase())
                        }

                        appendln()
                    }
                }.let {
                    call.respondText(it, ContentType.Text.Plain)
                }
            }

            post("paintBoard/paint") {
                try {
                    val clientId = call.request.cookies["__client_id"]
                    if (clientId != null && clientId in whiteList) {
                        val lastPaint = timer[clientId]
                        val current = System.currentTimeMillis()
                        if (lastPaint == null || current - lastPaint > 10000) {
                            val body = call.receive<String>()
                            val req = Gson().fromJson(body, PaintRequest::class.java)

                            if (req.color !in 0..31) throw RequestException("color out of bounds")

                            board[req.x][req.y] = req.color
                            timer[clientId] = current
                            call.respond(HttpStatusCode.OK)
                        } else throw RequestException("too frequently")
                    } else throw RequestException("invalid client id")
                } catch (e: Throwable) {
                    call.respondOutputStream(status = HttpStatusCode.BadRequest) {
                        e.printStackTrace(PrintStream(this))
                    }
                }
            }
        }
    }.start(true)
}
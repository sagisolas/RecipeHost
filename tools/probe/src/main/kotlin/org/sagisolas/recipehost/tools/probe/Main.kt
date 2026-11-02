package org.sagisolas.recipehost.tools.probe

import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import org.sagisolas.recipehost.protocol.Auth
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.EnvelopeFraming
import org.sagisolas.recipehost.protocol.ErrorCode
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import kotlin.system.exitProcess

/** Usage: probe 'recipehost://pair?v=1&h=…&p=…&t=…'  (copy the URI from the game log). */
fun main(args: Array<String>) {
    val uri = args.getOrNull(0) ?: run {
        System.err.println("usage: probe '<pairing payload URI from the game log>'")
        exitProcess(2)
    }
    val query = URI(uri).rawQuery.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        k to URLDecoder.decode(v, Charsets.UTF_8)
    }
    val hosts = query.getValue("h").split(",")
    val port = query.getValue("p").toInt()
    val token: ByteString = query.getValue("t").decodeBase64()
        ?: error("token is not valid base64url")

    // Hosts are unfiltered (see GLOSSARY: Pairing payload) — try each until one connects.
    val socket = hosts.firstNotNullOfOrNull { host ->
        runCatching { Socket().apply { connect(InetSocketAddress(host, port), 1000) } }
            .onFailure { println("[probe] $host:$port unreachable: ${it.message}") }
            .getOrNull()
            ?.also { println("[probe] connected to $host:$port") }
    } ?: run {
        System.err.println("[probe] no host reachable")
        exitProcess(1)
    }

    socket.use { socket ->
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        EnvelopeFraming.writeFrame(
            out,
            Envelope.Builder().auth(Auth.Builder().token(token).build()).build(),
        )
        out.flush()

        var itemCount = 0
        var recipeCount = 0
        var handshakePrinted = false

        while (true) {
            val envelope = EnvelopeFraming.readFrame(input) ?: break

            envelope.error?.let {
                System.err.println("[probe] server error: ${it.code ?: ErrorCode.UNSPECIFIED}")
                exitProcess(1)
            }
            envelope.handshake?.let { h ->
                println("protocol_version = ${h.protocol_version}")
                println("minecraft_version = ${h.minecraft_version}")
                println("locale = ${h.locale}")
                println("total_items (reported) = ${h.total_items}")
                println("total_recipes (reported) = ${h.total_recipes}")
                println("mods (${h.mods.size}):")
                for (m in h.mods) println("  - ${m.id}  ${m.name}  v${m.version}")
                handshakePrinted = true
            }
            envelope.item_batch?.let { itemCount += it.items.size }
            envelope.recipe_batch?.let { recipeCount += it.recipes.size }

            // Stop before draining icons; mobile-side would keep going, but the probe
            // only verifies metadata + batch counts.
            if (envelope.icon_batch != null || envelope.done != null) break
        }

        if (!handshakePrinted) {
            System.err.println("[probe] no handshake received")
            exitProcess(1)
        }

        println("item_batches drained: $itemCount items")
        println("recipe_batches drained: $recipeCount recipes")
    }
}

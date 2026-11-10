package org.sagisolas.recipehost.tools.probe

import com.google.protobuf.ByteString
import org.sagisolas.recipehost.protocol.Auth
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.EnvelopeFraming
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import kotlin.system.exitProcess

/** Usage: probe 'recipehost://pair?v=1&h=…&p=…&t=…'  (copy the URI from the game log). */
fun main(args: Array<String>) {
    val uri =
        args.getOrNull(0) ?: run {
            System.err.println("usage: probe '<pairing payload URI from the game log>'")
            exitProcess(2)
        }
    val query =
        URI(uri).rawQuery.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }
    val hosts = query.getValue("h").split(",")
    val port = query.getValue("p").toInt()
    val token: ByteString =
        runCatching { ByteString.copyFrom(Base64.getUrlDecoder().decode(query.getValue("t"))) }
            .getOrElse { error("token is not valid base64url") }

    // Hosts are unfiltered (see GLOSSARY: Pairing payload) — try each until one connects.
    val socket =
        hosts.firstNotNullOfOrNull { host ->
            runCatching { Socket().apply { connect(InetSocketAddress(host, port), 1000) } }
                .onFailure { println("[probe] $host:$port unreachable: ${it.message}") }
                .getOrNull()
                ?.also { println("[probe] connected to $host:$port") }
        } ?: run {
            System.err.println("[probe] no host reachable")
            exitProcess(1)
        }

    socket.use { socket ->
        val out = BufferedOutputStream(socket.getOutputStream())
        val input = BufferedInputStream(socket.getInputStream())

        EnvelopeFraming.writeFrame(
            out,
            Envelope.newBuilder().setAuth(Auth.newBuilder().setToken(token)).build(),
        )
        out.flush()

        var itemCount = 0
        var recipeCount = 0
        var handshakePrinted = false

        while (true) {
            val envelope = EnvelopeFraming.readFrame(input) ?: break

            if (envelope.hasError()) {
                System.err.println("[probe] server error: ${envelope.error.code}")
                exitProcess(1)
            }
            if (envelope.hasHandshake()) {
                val h = envelope.handshake
                println("protocol_version = ${h.protocolVersion}")
                println("minecraft_version = ${h.minecraftVersion}")
                println("locale = ${h.locale}")
                println("instance_name = ${h.instanceName}")
                println("total_items (reported) = ${h.totalItems}")
                println("total_recipes (reported) = ${h.totalRecipes}")
                println("mods (${h.modsCount}):")
                for (m in h.modsList) println("  - ${m.id}  ${m.name}  v${m.version}")
                handshakePrinted = true
            }
            if (envelope.hasItemBatch()) itemCount += envelope.itemBatch.itemsCount
            if (envelope.hasRecipeBatch()) {
                val batch = envelope.recipeBatch
                if (recipeCount == 0) {
                    println("first recipes:")
                    for (r in batch.recipesList.take(5)) println("  - ${r.id}  (${r.typeId})")
                }
                recipeCount += batch.recipesCount
            }

            // Stop before draining icons; mobile-side would keep going, but the probe
            // only verifies metadata + batch counts.
            if (envelope.hasIconBatch() || envelope.hasDone()) break
        }

        if (!handshakePrinted) {
            System.err.println("[probe] no handshake received")
            exitProcess(1)
        }

        println("item_batches drained: $itemCount items")
        println("recipe_batches drained: $recipeCount recipes")
    }
}

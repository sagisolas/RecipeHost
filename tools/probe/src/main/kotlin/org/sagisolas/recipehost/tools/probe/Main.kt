package org.sagisolas.recipehost.tools.probe

import okio.ByteString
import org.sagisolas.recipehost.protocol.Auth
import org.sagisolas.recipehost.protocol.Envelope
import org.sagisolas.recipehost.protocol.EnvelopeFraming
import org.sagisolas.recipehost.protocol.ErrorCode
import java.net.Socket
import kotlin.system.exitProcess

private const val DEFAULT_HOST = "localhost"
private const val DEFAULT_PORT = 25566
private val DEV_TOKEN: ByteString = ByteString.of(*ByteArray(32))

fun main(args: Array<String>) {
    val host = args.getOrNull(0) ?: DEFAULT_HOST
    val port = args.getOrNull(1)?.toInt() ?: DEFAULT_PORT

    println("[probe] connecting to $host:$port")

    Socket(host, port).use { socket ->
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        EnvelopeFraming.writeFrame(
            out,
            Envelope.Builder().auth(Auth.Builder().token(DEV_TOKEN).build()).build(),
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

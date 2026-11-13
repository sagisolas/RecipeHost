/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.data.scan

import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import org.sagisolas.recipehost.protocol.RecipeBatch

/**
 * The file a `Scan` stages its RecipeBatches in until its commit, so a large modpack's recipes
 * never sit in memory all at once (ADR 0017). Each batch is stored as it travelled, shared Slots
 * unexpanded, behind a 4-byte length.
 *
 * One `Scan` uses it at a time: [ScanIngest] begins it on each `Handshake` and deletes it once
 * the Scan ends.
 */
internal class RecipeSpool(
    private val fileSystem: FileSystem,
    private val path: Path,
) {
    private var sink: BufferedSink? = null

    /** Opens an empty spool for a new attempt, discarding whatever an earlier one left. */
    fun begin() {
        delete()
        sink = fileSystem.sink(path).buffer()
    }

    fun append(batch: RecipeBatch) {
        val sink = checkNotNull(sink) { "RecipeBatch appended before the spool began" }
        val bytes = RecipeBatch.ADAPTER.encode(batch)
        sink.writeInt(bytes.size)
        sink.write(bytes)
    }

    /** Runs [block] over the appended batches, in order, decoding one at a time. */
    fun <R> read(block: (Sequence<RecipeBatch>) -> R): R {
        checkNotNull(sink) { "spool read before it began" }.flush()
        return fileSystem.source(path).buffer().use { source ->
            block(
                generateSequence {
                    if (source.exhausted()) {
                        null
                    } else {
                        RecipeBatch.ADAPTER.decode(
                            source.readByteString(source.readInt().toLong()),
                        )
                    }
                },
            )
        }
    }

    /** Closes and removes the file; does nothing when there is none. */
    fun delete() {
        sink?.close()
        sink = null
        fileSystem.delete(path, mustExist = false)
    }
}

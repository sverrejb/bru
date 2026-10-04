package works.bru

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTest {
    private fun split(vararg chunks: String): Pair<String, String> = runBlocking {
        val queue = ArrayDeque(chunks.map { it.toByteArray() } + ByteArray(0))
        val (header, rest) = readHeader { queue.removeFirst() }
        String(header) to String(rest)
    }

    @Test
    fun legacyFrameWithoutNewlineIsAllHeader() {
        assertEquals("""{"op":"health"}""" to "", split("""{"op":""", """"health"}"""))
    }

    @Test
    fun bodyBytesAfterTheNewlineAreKept() {
        assertEquals("""{"op":"file"}""" to "ab", split("{\"op\":\"file\"}\nab"))
    }

    @Test
    fun newlineInALaterChunk() {
        assertEquals("""{"op":"file"}""" to "", split("""{"op":""", "\"file\"}\n"))
    }

    @Test
    fun namesCannotEscapeTheDirectory() {
        assertEquals("x", safeName("../../x"))
        assertEquals("b", safeName("a\\b"))
        assertEquals("file", safeName(".."))
        assertEquals("file", safeName(" "))
        assertEquals("report.pdf", safeName("report.pdf"))
    }
}

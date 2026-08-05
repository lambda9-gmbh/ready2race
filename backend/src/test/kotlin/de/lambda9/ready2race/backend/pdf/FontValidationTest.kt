package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontValidationTest {

    /**
     * Echte Schrift-Bytes, ohne eine zusätzliche Datei ins Repo zu legen: PDFBox bringt in seinem
     * eigenen Jar eine TTF-Testressource mit, die auf dem Test-Classpath liegt.
     */
    private fun embeddedFontBytes(): ByteArray =
        PDDocument::class.java.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")!!
            .use { it.readBytes() }

    @Test
    fun validFontBytesPassValidation() {
        assertTrue(checkValidFont(embeddedFontBytes()))
    }

    @Test
    fun arbitraryBytesAreRejected() {
        assertFalse(checkValidFont("Das ist keine Schriftdatei, nur Text.".toByteArray()))
    }

    @Test
    fun emptyBytesAreRejected() {
        assertFalse(checkValidFont(ByteArray(0)))
    }
}

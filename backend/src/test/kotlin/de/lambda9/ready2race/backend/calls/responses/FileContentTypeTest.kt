package de.lambda9.ready2race.backend.calls.responses

import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FileContentTypeTest {

    @Test
    fun resolvesKnownDocumentTypes() {
        assertEquals(ContentType.Application.Pdf, contentTypeForFileName("urkunde.pdf"))
        assertEquals(ContentType.Application.Zip, contentTypeForFileName("vorlage.zip"))
    }

    @Test
    fun fallsBackToOctetStreamForUnguessableFontExtensions() {
        // guessContentTypeFromName liefert für .ttf/.otf null; genau das hat vorher eine NPE
        // ausgelöst, weil ContentType.parse(null) nicht abgefangen wurde.
        assertEquals(ContentType.Application.OctetStream, contentTypeForFileName("schrift.ttf"))
        assertEquals(ContentType.Application.OctetStream, contentTypeForFileName("schrift.otf"))
    }

    @Test
    fun fallsBackToOctetStreamWithoutExtension() {
        assertEquals(ContentType.Application.OctetStream, contentTypeForFileName("README"))
    }

    @Test
    fun fallsBackToOctetStreamForEmptyName() {
        assertEquals(ContentType.Application.OctetStream, contentTypeForFileName(""))
    }
}

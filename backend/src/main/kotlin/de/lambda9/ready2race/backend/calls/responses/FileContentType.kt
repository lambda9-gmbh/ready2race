package de.lambda9.ready2race.backend.calls.responses

import io.ktor.http.*
import java.net.URLConnection

/**
 * Bestimmt den `Content-Type` für eine Datei anhand ihres Namens.
 *
 * `URLConnection.guessContentTypeFromName` liefert für unbekannte oder fehlende Endungen `null`
 * zurück, und `ContentType.parse(null)` wirft dafür eine NullPointerException statt eines
 * Fehlerwerts — betroffen sind z. B. `.ttf`/`.otf`-Schriftdateien und Dateien ohne Endung. Diese
 * Funktion liefert für jede Eingabe einen Wert und fällt in beiden Fehlerfällen auf
 * `application/octet-stream` zurück.
 */
fun contentTypeForFileName(name: String): ContentType {
    val guessed = URLConnection.guessContentTypeFromName(name)
        ?: return ContentType.Application.OctetStream

    return try {
        ContentType.parse(guessed)
    } catch (e: BadContentTypeFormatException) {
        ContentType.Application.OctetStream
    }
}

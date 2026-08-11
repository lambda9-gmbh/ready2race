package de.lambda9.ready2race.backend.app.appUserWithQrCode

import de.lambda9.ready2race.backend.app.appUserWithQrCode.boundary.AppUserWithQrCodeService
import de.lambda9.ready2race.backend.app.qrCodeApp.boundary.QrCodeAppService
import de.lambda9.ready2race.backend.app.qrCodeApp.entity.QrCodeError
import de.lambda9.ready2race.testing.testComprehension
import kotlin.test.Test

/**
 * Deckt eine Bug-Klasse ab, keine Feinheit dieses einen Endpoints: in einer `KIO.comprehension`
 * bindet nur der `!`-Operator einen KIO-Wert und führt ihn aus. Ein `KIO.fail(...)` ohne `!` baut
 * nur ein Objekt und verwirft es - der Fehlerfall greift nie, und der Compiler sagt nichts dazu.
 *
 * Genau das stand in [AppUserWithQrCodeService.deleteQrCode]: der Zweig für "keine Zeile gelöscht"
 * lief ins Leere, darunter gewann ein unbedingtes ok(). Das Entfernen eines längst entfernten
 * Bändchens meldete Erfolg, obwohl die OpenAPI-Beschreibung dort seit jeher einen 404 zusagt.
 */
class DeleteQrCodeTest {

    /**
     * Vor dem Fix war dieser Test rot - nicht mit einem falschen Fehler, sondern mit Erfolg.
     */
    @Test
    fun removingAnUnknownBandReportsNotFound() = testComprehension {
        assertKIOFails(QrCodeError.QrCodeNotFound) {
            AppUserWithQrCodeService.deleteQrCode("kein-bekanntes-baendchen")
        }
    }

    /**
     * Die Gegenprobe zum Scanner-Weg: dort ist das Löschen bewusst idempotent, damit ein Helfer,
     * der ein Bändchen zweimal scannt, keine Störung angezeigt bekommt. Die beiden Endpoints
     * liegen auf derselben Tabelle und sollen sich unterschiedlich verhalten - das darf niemand
     * beim Aufräumen versehentlich angleichen.
     */
    @Test
    fun theScannerPathStaysIdempotent() = testComprehension {
        assertKIOSucceeds {
            QrCodeAppService.deleteQrCode("kein-bekanntes-baendchen")
        }
    }
}

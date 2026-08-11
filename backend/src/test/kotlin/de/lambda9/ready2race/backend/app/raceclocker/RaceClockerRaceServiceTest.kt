package de.lambda9.ready2race.backend.app.raceclocker

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.raceclocker.boundary.RaceClockerRaceService
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test

/**
 * Der Dubletten-Schutz am Service, gegen eine echte Datenbank — und zwar gegen den Datenbestand,
 * den der Backfill aus V202608101100 wirklich hinterlassen hat: Adressen in www-Form. Neue
 * Eingaben faltet `normalizeUrl` auf den Apex; als Zeichenketten sind beide Formen verschieden,
 * als Feed dieselbe Antwort. Der Schutz muss deshalb über die NORMALISIERTE Form vergleichen,
 * sonst entstehen zwei Rennen mit einem Feed — zwei Abrufe je Takt für dasselbe Ergebnis.
 */
class RaceClockerRaceServiceTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun TestComprehensionScope<JEnv>.seedEventWithRace(url: String): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(id = eventId, name = "Testregatta", createdAt = now, updatedAt = now)
        )
        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = UUID.randomUUID(),
                event = eventId,
                name = "Läufe",
                resultsUrl = url,
                capturesLaps = false,
                position = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        return eventId
    }

    private fun request(url: String) = RaceClockerRaceRequest(
        name = "Kurzstrecke",
        resultsUrl = url,
        capturesLaps = false,
    )

    @Test
    fun `Apex-Form einer vorhandenen www-Adresse ist ein Duplikat`() = testComprehension {
        // Die www-Zeile direkt eingefügt, am Service vorbei — so sähe eine Backfill-Zeile aus,
        // die der Normalisierung entgangen ist. Auch dann darf der Apex-Zwilling nicht entstehen.
        val eventId = seedEventWithRace("https://www.raceclocker.com/2a8c59a6")

        assertKIOFails(RaceClockerRaceError.UrlTaken) {
            RaceClockerRaceService.addRace(eventId, UUID.randomUUID(), request("https://raceclocker.com/2a8c59a6"))
        }
    }

    @Test
    fun `www-Form einer vorhandenen Apex-Adresse ist ein Duplikat`() = testComprehension {
        // Die Gegenrichtung: der Bestand ist schon normalisiert, die Eingabe kommt mit www aus der
        // Browserzeile. `normalizeUrl` faltet die Eingabe, der Vergleich muss trotzdem treffen.
        val eventId = seedEventWithRace("https://raceclocker.com/2a8c59a6")

        assertKIOFails(RaceClockerRaceError.UrlTaken) {
            RaceClockerRaceService.addRace(eventId, UUID.randomUUID(), request("https://www.raceclocker.com/2a8c59a6"))
        }
    }
}

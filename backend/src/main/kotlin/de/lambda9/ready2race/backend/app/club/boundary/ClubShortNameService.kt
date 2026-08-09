package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.app.club.entity.ClubShortNameDto
import de.lambda9.ready2race.backend.app.club.entity.ClubShortNameError
import de.lambda9.ready2race.backend.app.club.entity.ClubShortNameRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.generated.tables.records.ClubShortNameRecord
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Pflegeseite "Vereinskurzformen".
 *
 * Sie arbeitet nicht auf der Tabelle `club_short_name`, sondern auf den Namen, die im System
 * vorkommen: gepflegt wird eine Kurzform immer für einen Namen, den jemand tatsächlich zu sehen
 * bekommt. Die Tabelle ist nur das Gedächtnis dazu - was nicht darin steht, beantwortet die
 * Heuristik.
 */
object ClubShortNameService {

    fun list(
        eventId: UUID?,
    ): App<Nothing, ApiResponse.ListDto<ClubShortNameDto>> = KIO.comprehension {

        val aliases = !ClubShortNameRepo.aliases().orDie()

        val occurring = !when (eventId) {
            null -> ClubShortNameRepo.occurringNames()
            else -> ClubShortNameRepo.occurringNamesForEvent(eventId)
        }.orDie()

        // Ohne Veranstaltungsfilter kommen die gepflegten Schreibweisen dazu, auch wenn sie sonst
        // nirgends mehr stehen. Sonst bliebe der Eintrag zu einem umbenannten oder gelöschten
        // Verein für immer in der Tabelle, ohne je wieder auf der Seite aufzutauchen - und damit
        // ohne Möglichkeit, ihn loszuwerden.
        val orphans = when (eventId) {
            null -> (!ClubShortNameRepo.all().orDie()).map { it.sampleName }
            else -> emptyList()
        }

        val rows = (occurring + orphans)
            .mapNotNull { name -> name.trim().takeIf { it.isNotEmpty() } }
            .groupBy { ClubNameKey.of(it) }
            // Ein Name ganz ohne Buchstaben und Ziffern ergäbe einen leeren Schlüssel. Der ließe
            // sich weder in einen Pfad schreiben noch später wieder auflösen.
            .filterKeys { it.isNotEmpty() }
            .map { (nameKey, spellings) ->
                // Die kürzeste Schreibweise führt die Zeile an: sie ist in aller Regel die ohne
                // Rechtsform und Gründungsjahr und damit die, an der man den Verein wiedererkennt.
                val names = spellings.distinct().sortedWith(compareBy({ it.length }, { it }))
                ClubShortNameDto(
                    nameKey = nameKey,
                    names = names,
                    shortName = aliases[nameKey] ?: ClubShortNameLogic.heuristic(names.first()),
                    maintained = aliases.containsKey(nameKey),
                )
            }
            .sortedBy { it.names.first().lowercase() }

        KIO.ok(ApiResponse.ListDto(rows))
    }

    fun set(
        nameKey: String,
        request: ClubShortNameRequest,
        userId: UUID,
    ): App<ClubShortNameError, ApiResponse.NoData> = KIO.comprehension {

        if (ClubNameKey.of(request.sampleName) != nameKey) {
            KIO.fail(ClubShortNameError.NameKeyMismatch)
        } else {
            val now = LocalDateTime.now()
            !ClubShortNameRepo.upsert(
                ClubShortNameRecord(
                    nameKey = nameKey,
                    sampleName = request.sampleName,
                    shortName = request.shortName.trim(),
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                )
            ).orDie()

            noData
        }
    }

    /**
     * Bewusst ohne 404: die Seite meldet "Feld geleert", nicht "Zeile löschen". Ob zu dem Namen
     * je eine Kurzform gepflegt war, weiß danach ohnehin nur noch die Heuristik - ein Fehler wäre
     * für den Bearbeiter nichts, worauf er reagieren könnte.
     */
    fun remove(
        nameKey: String,
    ): App<Nothing, ApiResponse.NoData> =
        ClubShortNameRepo.delete(nameKey).orDie().map { ApiResponse.NoData }
}

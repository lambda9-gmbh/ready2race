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

        val settings = !ClubShortNameSettings.load()

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
                    shortName = settings.aliases[nameKey]
                        ?: ClubShortNameLogic.heuristic(names.first(), settings.rules),
                    maintained = settings.aliases.containsKey(nameKey),
                )
            }
            // Die Seite ist eine Arbeitsliste: sie soll die längsten - also noch nicht ordentlich
            // gekürzten - Kurzformen zuerst zeigen, damit man sie von oben abarbeiten kann
            // (Nutzerwunsch 11.08.2026). Bei gleicher Länge alphabetisch, damit die Reihenfolge
            // stabil und vorhersehbar bleibt.
            .sortedWith(
                compareByDescending<ClubShortNameDto> { it.shortName.length }
                    .thenBy { it.names.first().lowercase() }
            )

        KIO.ok(ApiResponse.ListDto(rows))
    }

    /**
     * Dieselbe Auskunft wie eine Zeile aus [list], aber für einen einzelnen Namen.
     *
     * Es gibt sie, weil der Bearbeiten-Dialog eines Vereins dasselbe Feld anbietet wie die Liste
     * und dafür zwei Dinge braucht, die nur der Server weiß: die automatisch erzeugte Kurzform
     * (Regeln stehen in der Datenbank) und ob zu diesem Namen etwas gepflegt ist. Ohne beides
     * stünde das Feld leer da, und ein unangetastetes Feld ließe sich nicht von einem
     * absichtlich geleerten unterscheiden.
     */
    fun forName(
        name: String,
    ): App<Nothing, ApiResponse.Dto<ClubShortNameDto>> = KIO.comprehension {

        val settings = !ClubShortNameSettings.load()
        val trimmed = name.trim()
        val nameKey = ClubNameKey.of(trimmed)

        KIO.ok(
            ApiResponse.Dto(
                ClubShortNameDto(
                    nameKey = nameKey,
                    names = listOf(trimmed),
                    shortName = settings.aliases[nameKey]
                        ?: ClubShortNameLogic.heuristic(trimmed, settings.rules),
                    maintained = settings.aliases.containsKey(nameKey),
                )
            )
        )
    }

    /**
     * Schreibt, was im Bearbeiten-Dialog eines Vereins im Feld "Kurzform" stand.
     *
     * Die drei Zustände von [shortName] sind in
     * [de.lambda9.ready2race.backend.app.club.entity.ClubUpsertDto] beschrieben; hier steht nur
     * ihre Wirkung.
     */
    fun applyForName(
        name: String,
        shortName: String?,
        userId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {

        val nameKey = ClubNameKey.of(name)

        // Ein Name ganz ohne Buchstaben und Ziffern ergäbe einen leeren Schlüssel - unter dem
        // schlägt keine Anzeige je nach. Lieber nichts schreiben als etwas Wirkungsloses.
        if (shortName == null || nameKey.isEmpty()) {
            KIO.ok(Unit)
        } else if (shortName.isBlank()) {
            !ClubShortNameRepo.delete(nameKey).orDie()
            KIO.ok(Unit)
        } else {
            val now = LocalDateTime.now()
            !ClubShortNameRepo.upsert(
                ClubShortNameRecord(
                    nameKey = nameKey,
                    sampleName = name.trim(),
                    shortName = shortName.trim(),
                    createdAt = now,
                    createdBy = userId,
                    updatedAt = now,
                    updatedBy = userId,
                )
            ).orDie()
            KIO.ok(Unit)
        }
    }

    /**
     * Wird ein Verein umbenannt, ändert sich sein Schlüssel - die gepflegte Kurzform hinge sonst
     * an einem Namen, den es nicht mehr gibt.
     *
     * Entscheidung: **die Kurzform wandert mit.** Wer sie gepflegt hat, hat den Verein gemeint,
     * nicht die Schreibweise. Die alte Zeile fällt dabei nur, wenn die alte Schreibweise nirgends
     * mehr im System vorkommt: Gastruderer tragen ihren Verein als Freitext, und dieser Freitext
     * wird beim Umbenennen eines Vereins-Datensatzes nicht mitgezogen. Steht die alte Schreibweise
     * also noch an einer Person, hat ihre Kurzform weiter zu tun und bleibt.
     *
     * Eine bereits gepflegte Kurzform am neuen Schlüssel wird nicht überschrieben - sie ist die
     * jüngere Aussage über diesen Namen.
     */
    fun followRename(
        oldName: String,
        newName: String,
        userId: UUID,
    ): App<Nothing, Unit> = KIO.comprehension {

        val oldKey = ClubNameKey.of(oldName)
        val newKey = ClubNameKey.of(newName)

        if (oldKey == newKey || oldKey.isEmpty() || newKey.isEmpty()) {
            KIO.ok(Unit)
        } else {
            val maintained = (!ClubShortNameRepo.all().orDie()).associateBy { it.nameKey }
            val moving = maintained[oldKey]

            if (moving == null) {
                KIO.ok(Unit)
            } else {
                if (!maintained.containsKey(newKey)) {
                    val now = LocalDateTime.now()
                    !ClubShortNameRepo.upsert(
                        ClubShortNameRecord(
                            nameKey = newKey,
                            sampleName = newName.trim(),
                            shortName = moving.shortName,
                            createdAt = now,
                            createdBy = userId,
                            updatedAt = now,
                            updatedBy = userId,
                        )
                    ).orDie()
                }

                // Der Schlüssel lässt sich nicht in SQL bilden, also wird die Frage "kommt die
                // alte Schreibweise noch vor" im Speicher beantwortet. Die Menge ist die Zahl der
                // Vereinsschreibweisen im System - Größenordnung Dutzende - und ein Verein wird
                // selten umbenannt.
                val stillOccurring = (!ClubShortNameRepo.occurringNames().orDie())
                    .any { ClubNameKey.of(it) == oldKey }

                if (!stillOccurring) {
                    !ClubShortNameRepo.delete(oldKey).orDie()
                }

                KIO.ok(Unit)
            }
        }
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

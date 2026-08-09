package de.lambda9.ready2race.backend.app.club.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.club.control.ClubNameRuleRepo
import de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo
import de.lambda9.ready2race.backend.app.club.control.clubNameRule
import de.lambda9.ready2race.backend.app.club.entity.ClubNameRule
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie

/**
 * Alles, was zum Kürzen eines Vereinsnamens nötig ist: die gepflegten Kurzformen und die Regeln.
 *
 * Es gibt diesen Typ, damit eine Anzeige **einen** Ladeweg hat statt zweier. Schiedsrichter-Board
 * und Athleten-Anzeige fragen im Sekundentakt ab und kürzen dabei jeden Vereinsnamen jedes Bootes;
 * beides je Mannschaft nachzuschlagen wäre ein Rückschritt gegenüber dem Stand, den [load] ablöst.
 *
 * Beide Tabellen sind so groß wie die Zahl der Vereinsschreibweisen bzw. der gepflegten Regeln -
 * Größenordnung Dutzende. Einmal je Abruf ziehen und danach ohne weitere Abfrage auflösen.
 */
data class ClubShortNameSettings(
    val aliases: Map<String, String>,
    val rules: List<ClubNameRule>,
) {

    fun shorten(name: String): String = ClubShortNameLogic.shorten(name, this)

    companion object {

        /** Für reine Funktionstests und Aufrufer ohne Datenbank: Heuristik ohne jede Regel. */
        val none = ClubShortNameSettings(aliases = emptyMap(), rules = emptyList())

        fun load(): App<Nothing, ClubShortNameSettings> = KIO.comprehension {
            val aliases = !ClubShortNameRepo.aliases().orDie()
            val rules = !ClubNameRuleRepo.all().orDie()

            KIO.ok(ClubShortNameSettings(aliases = aliases, rules = rules.map { it.clubNameRule() }))
        }
    }
}

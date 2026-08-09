package de.lambda9.ready2race.backend.app.club.boundary

/**
 * Die Vereine eines Bootes als fertige Zeile - der Baustein, der
 * `singletonOrFallback(clubs, mixedTeamTerm)` ablöst.
 *
 * Der Grund: bisher bekam jedes vereinsgemischte Boot dieselbe Zeile "Renngemeinschaft". Im
 * Produktivstand der CRF 2026 sind das 42 von 100 Meldungen - mehrere Boote desselben Laufs waren
 * für den Schiedsrichter nicht mehr auseinanderzuhalten. Statt der Menge der Vereine (ungeordnet,
 * bei Uneindeutigkeit verworfen) steht hier die vollständige Kette in Bootsreihenfolge.
 *
 * [full] und [short] können unterschiedlich viele Glieder haben, und das ist Absicht: zwei
 * Schreibweisen desselben Vereins, die die Pflegeseite auf dieselbe Kurzform gelegt hat, stehen in
 * der vollen Kette weiterhin beide (die Anzeige soll nicht behaupten, die Meldung sei einheitlich),
 * fallen in der Kurzform aber zusammen. Genau dafür ist die Pflegeseite da.
 */
data class ClubComposition(
    val full: String,
    val short: String,
) {

    companion object {

        const val SEPARATOR = " / "

        private val PLACEHOLDER_KEY = ClubNameKey.of("N.N.")

        /**
         * Der Verein, den eine Person *trägt* - die eine Regel, aus der jede Kette gebaut wird.
         *
         * Gastruderer sind nicht Mitglied eines im System gepflegten Vereins; ihr Verein steht als
         * Freitext an der Person ([externalClubName]). Für alle anderen zählt der Name ihres
         * eigenen Vereins ([ownClubName]) - ausdrücklich nicht der des *meldenden* Vereins, an dem
         * die Abfragen bisher hingen: wer meldet, ist reine Verwaltung und für die Durchführung
         * bedeutungslos.
         */
        fun clubWorn(external: Boolean?, externalClubName: String?, ownClubName: String?): String? =
            if (external == true) externalClubName else ownClubName

        /**
         * [clubNames] ist die Crew in Bootsreihenfolge - je Person der Verein, den sie *trägt*
         * (`participant.external_club_name` bei Gastruderern, sonst der Name ihres eigenen
         * Vereins), nicht der meldende Verein. [aliases] ist die Zuordnung aus `club_short_name`.
         *
         * Bei genau einem Verein steht schlicht dieser Verein da, ohne Trennzeichen - für die
         * reinen Vereinsboote ändert sich damit nichts.
         */
        fun of(clubNames: List<String?>, aliases: Map<String, String>): ClubComposition {
            val named = clubNames
                .mapNotNull { it?.trim()?.takeIf { name -> name.isNotEmpty() } }
                // "N.N." steht in den echten Meldedaten für "Platz noch offen". Ein Platzhalter
                // fällt still raus, statt eine leere Stelle in die Kette zu setzen.
                .filter { ClubNameKey.of(it) != PLACEHOLDER_KEY }

            // Zusammengefasst wird über den Schlüssel, nicht über den Namen: schreibt eine Meldung
            // denselben Verein für zwei Personen verschieden, ist es trotzdem ein Glied der Kette.
            val full = named
                .distinctBy { ClubNameKey.of(it) }
                .joinToString(SEPARATOR)

            val short = named
                .map { ClubShortNameLogic.shorten(it, aliases) }
                .distinct()
                .joinToString(SEPARATOR)

            return ClubComposition(full = full, short = short)
        }
    }
}

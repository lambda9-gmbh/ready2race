package de.lambda9.ready2race.backend.app.club.boundary

/**
 * Die Kurzform eines Vereinsnamens.
 *
 * Zwei Quellen, in dieser Reihenfolge: die gepflegte Kurzform aus `club_short_name`, sonst die
 * Heuristik. Die Heuristik lag bis zum 09.08.2026 als `shortClubName` im Frontend
 * (`liveDashboard/common.ts`); sie ist hierher gezogen, weil Schiedsrichter-Board,
 * Athleten-Anzeige und Pflegeseite jetzt dieselbe Regel brauchen - und die Pflegeseite ihr
 * Eingabefeld mit genau dem Wert vorbelegt, den die Heuristik erzeugt.
 */
object ClubShortNameLogic {

    private val CLUB_NAME_BALLAST = listOf(
        // Rechtsform "e.V." / "eV"
        Regex("""\s*\be\.?\s?V\.?(?=\s|${'$'})""", RegexOption.IGNORE_CASE),
        // Gründungsjahre in Klammern, z.B. "(1879/83)"
        Regex("""\s*\([^)]*\d[^)]*\)"""),
        // "von 1889"
        Regex("""\s*\bvon\s+\d{4}\b""", RegexOption.IGNORE_CASE),
        // nachgestellte Jahreszahl, z.B. "München 1972"
        Regex("""\s+\d{4}\b"""),
    )

    // Im Rudersport gängige Kürzel - Schiedsrichter lesen sie ohne Nachdenken.
    private val CLUB_TYPE_ABBREVIATIONS = listOf(
        Regex("""\bRudergesellschaft\b""", RegexOption.IGNORE_CASE) to "RG",
        Regex("""\bRuder-?vereinigung\b""", RegexOption.IGNORE_CASE) to "RVg",
        Regex("""\bRuder-?verein\b""", RegexOption.IGNORE_CASE) to "RV",
        Regex("""\bRuder-?club\b""", RegexOption.IGNORE_CASE) to "RC",
        Regex("""\bRuder-?klub\b""", RegexOption.IGNORE_CASE) to "RK",
        Regex("""\bSegel-?verein\b""", RegexOption.IGNORE_CASE) to "SV",
        Regex("""\bSegel-?club\b""", RegexOption.IGNORE_CASE) to "SC",
        Regex("""\bSportvereinigung\b""", RegexOption.IGNORE_CASE) to "SVg",
        Regex("""\bSportverein\b""", RegexOption.IGNORE_CASE) to "SV",
        Regex("""\bTurnverein\b""", RegexOption.IGNORE_CASE) to "TV",
        Regex("""\bAkademischer\b""", RegexOption.IGNORE_CASE) to "Akad.",
    )

    /**
     * Kurzform eines Vereinsnamens für die Listenansicht: Rechtsform und Gründungsjahre entfallen,
     * gängige Vereinstypen werden abgekürzt. Der vollständige Name bleibt in der breiten Karte und
     * im Detail-Dialog sichtbar.
     */
    fun heuristic(name: String): String {
        val withoutBallast = CLUB_NAME_BALLAST.fold(name) { acc, pattern -> acc.replace(pattern, " ") }
        val abbreviated = CLUB_TYPE_ABBREVIATIONS.fold(withoutBallast) { acc, (pattern, replacement) ->
            acc.replace(pattern, replacement)
        }
        return abbreviated
            .replace(Regex("""\s{2,}"""), " ")
            .replace(Regex("""\s+,"""), ",")
            .trim()
    }

    /**
     * Die anzuzeigende Kurzform. [aliases] ist die Zuordnung aus `club_short_name`, gelesen über
     * [de.lambda9.ready2race.backend.app.club.control.ClubShortNameRepo] - Schlüssel ist
     * [ClubNameKey.of], nicht der Name.
     */
    fun shorten(name: String, aliases: Map<String, String>): String =
        aliases[ClubNameKey.of(name)] ?: heuristic(name)
}

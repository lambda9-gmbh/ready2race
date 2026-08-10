package de.lambda9.ready2race.backend.app.club.boundary

import java.text.Normalizer

/**
 * Der Schlüssel, unter dem eine gepflegte Kurzform hängt (`club_short_name.name_key`).
 *
 * Gepflegt wird über den *Namen*, nicht über den Vereins-Datensatz: der Verein, den ein Athlet
 * trägt, ist bei Gastruderern Freitext, und derselbe Verein steht in den Meldedaten mehrfach
 * verschieden geschrieben. Der Schlüssel zieht genau den Ballast ab, in dem sich diese
 * Schreibweisen unterscheiden - Rechtsform, Gründungsjahr, Bindestriche, Leerzeichen -, damit
 * "Rostocker Ruderclub" und "Rostocker Ruder-Club von 1885 e.V." einmal statt zweimal gepflegt
 * werden müssen.
 *
 * Die Grenze ist bewusst: echte Abkürzungsvarianten ("ARV Kiel" gegen
 * "Akademischer Ruderverein Kiel e.V.") erkennt keine Regel, ohne verschiedene Vereine zu
 * verschmelzen. Die führt die Pflegeseite zusammen, indem beide Schlüssel dieselbe Kurzform
 * bekommen. [ClubNameKeyTest] nagelt genau das fest.
 */
object ClubNameKey {

    private val BALLAST = listOf(
        // Rechtsform "e.V." / "e. V." / "eV"
        Regex("""\s*\be\.?\s?v\.?(?=\s|${'$'})""", RegexOption.IGNORE_CASE),
        // Klammerzusätze mit Ziffern, z.B. "(1879/83)"
        Regex("""\s*\([^)]*\d[^)]*\)"""),
        // "von 1889" und die verkürzte Form "v. 1899"
        Regex("""\s*\bvon\s+\d{4}\b""", RegexOption.IGNORE_CASE),
        Regex("""\s*\bv\.\s*\d{4}\b""", RegexOption.IGNORE_CASE),
        // nachgestellte Jahreszahl, z.B. "München 1972"
        Regex("""\s+\d{4}\b"""),
    )

    fun of(name: String): String {
        // NFC zuerst: dieselbe Umlautschreibweise kommt aus Formular, CSV-Import und
        // RaceClocker-Feed unterschiedlich zerlegt an und ergäbe sonst zwei Schlüssel.
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFC).lowercase()
        val withoutBallast = BALLAST.fold(normalized) { acc, pattern -> acc.replace(pattern, " ") }
        // Alles Übrige an Trennzeichen fällt weg statt nur die bekannten Fälle: "Ruder-Club",
        // "Ruderclub" und "Ruder Club" sind derselbe Verein.
        return withoutBallast.filter { it.isLetterOrDigit() }
    }
}

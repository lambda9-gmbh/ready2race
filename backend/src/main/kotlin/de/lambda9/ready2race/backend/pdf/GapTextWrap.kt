package de.lambda9.ready2race.backend.pdf

/**
 * Zerlegt den Inhalt eines Platzhalters in Zeilen, die in seinen Kasten passen.
 *
 * Anlass ist die Vereinskette auf der Siegerurkunde: seit dem 09.08.2026 steht im Feld
 * "Vereinsname" nicht mehr ein Verein, sondern die Vereine aller Athleten eines Bootes. Bei fünf
 * Vereinen sind das rund 160 Zeichen - bei 18 pt gut das Doppelte der A4-Breite. Der Renderer
 * bricht von sich aus nicht um und schneidet auch nicht ab, die Zeile liefe also links und rechts
 * über das Papier hinaus.
 *
 * Umgebrochen wird an den **Vereinsgrenzen**, nicht an Wortgrenzen: ein Vereinsname wird nie
 * zerrissen, weil "Marburger Ruderverein von" / "1911 e.V." auf einer Urkunde, die im Bootshaus
 * hängt, wie ein Fehler aussieht. Erst wenn ein einzelner Name allein nicht in die Breite passt
 * (in den echten Meldedaten: "Ruder-Club Allemannia von 1866 e.V. - Leuphana Universität
 * Lüneburg"), wird für diese eine Zeile an Wortgrenzen weitergebrochen - ein umgebrochener langer
 * Name ist besser als einer, der über den Rand läuft.
 *
 * Der Trenner verschwindet am Umbruch, wie bei jedem Zeilenumbruch: die neue Zeile beginnt mit dem
 * nächsten vollständigen Vereinsnamen.
 *
 * Die Messung kommt als [measure] von außen, damit diese Zerlegung rein und ohne PDFBox prüfbar
 * bleibt - und damit **beide** Renderer dieselbe bekommen. Vorher brach nur der DOCX-Pfad um
 * (Word bricht innerhalb eines Rahmens von selbst), der PDF-Pfad gar nicht; dieselbe Urkunde sah
 * je nach Format anders aus.
 */
object GapTextWrap {

    /** Der Trenner, mit dem `ClubComposition` die Vereine eines Bootes verkettet. */
    const val CHAIN_SEPARATOR = " / "

    /**
     * [content] in Zeilen, die je höchstens [maxWidth] breit sind. Bereits vorhandene Zeilenumbrüche
     * (die Namensliste einer Mannschaftsurkunde bringt welche mit) bleiben erhalten und werden
     * jeder für sich weiter zerlegt.
     *
     * Breiter als [maxWidth] bleibt eine Zeile nur, wenn ein einzelnes Wort das schon ist - dagegen
     * hilft kein Umbruch, nur eine andere Vorlage.
     */
    fun lines(content: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val explicitLines = content.split("\n")
        // Ein Kasten ohne Breite kommt bei einem kaputt gepflegten Platzhalter vor. Ohne diese
        // Grenze liefe die Zerlegung in eine Zeile je Wort und der Text stünde senkrecht.
        if (maxWidth <= 0f) return explicitLines

        return explicitLines.flatMap { line -> wrapLine(line, maxWidth, measure) }
    }

    /** Wie [lines], als ein Text mit `\n` - die Form, die beide Renderer ohnehin erwarten. */
    fun wrap(content: String, maxWidth: Float, measure: (String) -> Float): String =
        lines(content, maxWidth, measure).joinToString("\n")

    private fun wrapLine(line: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        if (line.isEmpty() || measure(line) <= maxWidth) return listOf(line)

        val links = line.split(CHAIN_SEPARATOR)
        return if (links.size > 1) {
            packLinks(links, maxWidth, measure)
        } else {
            wrapAtWords(line, maxWidth, measure)
        }
    }

    /**
     * Füllt jede Zeile mit so vielen vollständigen Gliedern, wie hineingehen. Passt ein Glied
     * allein nicht, greift für dieses Glied die Rückfallebene [wrapAtWords]; dessen letztes Stück
     * bleibt offen, damit ein kurzer Verein dahinter noch auf dieselbe Zeile darf.
     */
    private fun packLinks(links: List<String>, maxWidth: Float, measure: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()
        var current: String? = null

        for (link in links) {
            val candidate = if (current == null) link else current + CHAIN_SEPARATOR + link
            if (measure(candidate) <= maxWidth) {
                current = candidate
                continue
            }

            current?.let { lines.add(it) }

            if (measure(link) <= maxWidth) {
                current = link
            } else {
                val broken = wrapAtWords(link, maxWidth, measure)
                lines.addAll(broken.dropLast(1))
                current = broken.last()
            }
        }

        current?.let { lines.add(it) }
        return lines
    }

    /**
     * Die Rückfallebene für einen einzelnen, zu langen Vereinsnamen. Ein Wort, das für sich schon
     * zu breit ist, bleibt stehen - es zu zerhacken machte den Namen unleserlich, ohne ihn
     * unterzubringen.
     */
    private fun wrapAtWords(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf(text)

        val lines = mutableListOf<String>()
        var current = ""

        for (word in words) {
            if (current.isEmpty()) {
                current = word
                continue
            }
            val candidate = "$current $word"
            if (measure(candidate) <= maxWidth) {
                current = candidate
            } else {
                lines.add(current)
                current = word
            }
        }

        lines.add(current)
        return lines
    }
}

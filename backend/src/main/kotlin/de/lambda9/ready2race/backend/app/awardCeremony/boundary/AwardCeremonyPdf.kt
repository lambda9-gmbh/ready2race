package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyRank
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListOptions
import de.lambda9.ready2race.backend.app.awardCeremony.entity.ResultListSize
import de.lambda9.ready2race.backend.pdf.BlockBuilder
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Der eine Ergebnislisten-Generator: je Abschnitt (Wettkampf, ggf. Wertung) ein A4-Bogen. Was auf
 * dem Blatt steht und wie groß es gesetzt wird, entscheiden die [ResultListOptions] - der
 * klassische Siegerehrungsbogen ist das Preset [ResultListOptions.ceremony] und läuft durch
 * exakt denselben Pfad.
 *
 * Die Passung auf ein Blatt ist *nicht* strukturell zugesichert: `page { }` legt bei Überlauf
 * still eine Folgeseite nach, und die trüge weder Veranstaltung noch Rennnummer noch Wertung -
 * ein Blatt, mit dem am Pult wie am Brett niemand etwas anfangen kann.
 *
 * Deshalb wird die Passung gemessen statt geschätzt. Jeder Bogen wird probeweise in den Stufen
 * seiner Maßtabelle gesetzt, von der großzügigsten Stufe abwärts, und gedruckt wird die erste
 * Stufe, die mit einer Seite auskommt. Eine Schätzung über die Personenzahl könnte das nicht
 * leisten: ob eine Zeile umbricht, hängt an der Länge der Vereinsnamen, und über die Bootsgröße
 * ist das Verhalten nicht einmal monoton.
 *
 * Die Stufenleiter hat einen lesbaren Boden: unterhalb der letzten Stufe wird nicht weiter
 * geschrumpft, weil man kleinere Namenszeilen vom Pult - beim Aushang: aus zwei Schritten
 * Entfernung - nicht mehr abliest. Passt ein Bogen auch auf dieser Stufe nicht auf ein Blatt,
 * bekommt er mehrere. Der Umbruch läuft zwischen zwei Rangblöcken - kein Boot wird von seiner
 * Mannschaft getrennt -, jede Seite trägt den vollen Kopf und die Rangzahl ihres ersten Blocks,
 * und jede ab der zweiten zusätzlich [CONTINUATION_MARK], damit sichtbar bleibt, dass das Blatt
 * zum selben Bogen gehört. Auch diese Aufteilung wird gemessen, nicht geschätzt.
 */
object AwardCeremonyPdf {

    // Ohne Wochentag: dessen Abkürzung hängt an der CLDR-Fassung des JDK ("Sa" vs. "Sa.") und
    // machte die Ausgabe von der Java-Version abhängig - dieselbe Regel wie in AwardCeremonyLogic.
    private val ceremonyTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

    /**
     * Eine Schriftstufe des Rangblocks. Die Rangzahl steht bewusst nicht darin: sie muss auf jeder
     * Stufe aus der Distanz zu lesen sein, sonst sucht der Leser den Platz.
     *
     * Sichtbar bis zur Modulgrenze, damit der Test nicht nur die Seitenzahl prüfen kann, sondern
     * auch, dass ein kleines Feld nicht grundlos eng gesetzt wird.
     */
    internal data class Scale(
        /** Die Personenzeilen - die mit Abstand längste Liste auf dem Blatt. */
        val nameSize: Float,
        /** Bootszeile, meldender Verein, Strafe, geteilter Rang. */
        val metaSize: Float,
        /** Abstand zwischen zwei Rangblöcken. */
        val gap: Float,
    )

    /**
     * Von großzügig nach eng. Die erste Stufe ist die Wunschform, die letzte der lesbare Boden:
     * kleiner wird nicht gesetzt, weil die Sprecherin das unter Zeitdruck nicht mehr vom Pult
     * abliest. Reicht auch der Boden nicht, wächst der Bogen auf mehrere Seiten, statt weiter zu
     * schrumpfen.
     */
    internal val scales = listOf(
        Scale(nameSize = 12f, metaSize = 10f, gap = 14f),
        Scale(nameSize = 11f, metaSize = 9.5f, gap = 11f),
        Scale(nameSize = 10f, metaSize = 9f, gap = 8f),
        Scale(nameSize = 8.5f, metaSize = 8f, gap = 5f),
    )

    /**
     * Die Aushang-Leiter beginnt deutlich größer und hat einen höheren Boden: ihr Blatt hängt am
     * Brett und wird aus Entfernung gelesen, nicht in der Hand gehalten. Lieber eine Seite mehr
     * als eine Liste, vor der sich eine Traube bilden muss.
     */
    internal val postingScales = listOf(
        Scale(nameSize = 15f, metaSize = 12f, gap = 16f),
        Scale(nameSize = 13f, metaSize = 11f, gap = 12f),
        Scale(nameSize = 12f, metaSize = 10.5f, gap = 9f),
        Scale(nameSize = 11f, metaSize = 10f, gap = 6f),
    )

    /**
     * Die festen Maße eines Schriftgrads - alles außerhalb der [Scale]-Stufenleiter. Sie stehen in
     * einer Tabelle je [ResultListSize] statt verstreut im Code, damit „Aushang" und „Siegerehrung"
     * sich nur in Zahlen unterscheiden und nicht in zwei auseinanderlaufenden Satz-Funktionen.
     */
    internal data class TypeSizes(
        val heading: Float,
        val continuation: Float,
        val eventLine: Float,
        val competitionLine: Float,
        val competitionName: Float,
        /** „Wertung: …" - die Angabe, nach der am Brett gesucht wird. */
        val ratingLabel: Float,
        /** Lauf- und Ehrungszeile im Kopf. */
        val raceInfo: Float,
        val rankNumber: Float,
        val clubLine: Float,
        val time: Float,
        val footer: Float,
        val scales: List<Scale>,
    )

    /** Die Maße des heutigen Siegerehrungsbogens - unverändert, das ist die Abwärtskompatibilität. */
    private val ceremonySizes = TypeSizes(
        heading = 18f,
        continuation = 12f,
        eventLine = 11f,
        competitionLine = 14f,
        competitionName = 12f,
        ratingLabel = 12f,
        raceInfo = 11f,
        rankNumber = 20f,
        clubLine = 14f,
        time = 14f,
        footer = 9f,
        scales = scales,
    )

    /** Die Aushang-Maße: die tragenden Angaben - Rennen, Platz, Verein, Zeit - deutlich größer. */
    private val postingSizes = TypeSizes(
        heading = 24f,
        continuation = 14f,
        eventLine = 12f,
        competitionLine = 19f,
        competitionName = 14f,
        ratingLabel = 15f,
        raceInfo = 12f,
        rankNumber = 26f,
        clubLine = 17f,
        time = 17f,
        footer = 10f,
        scales = postingScales,
    )

    internal fun sizesFor(size: ResultListSize): TypeSizes = when (size) {
        ResultListSize.POSTING -> postingSizes
        ResultListSize.CEREMONY -> ceremonySizes
    }

    /** Kurz und in Worten: eine Seitenzahl allein beantwortet nicht, ob dies ein neuer Bogen ist. */
    private const val CONTINUATION_MARK = "Fortsetzung"

    /** Ein Bogen, fertig aufgeteilt: die Stufe steht fest, [rankPages] hält die Ränge je Blatt. */
    private data class Layout(
        val sheet: AwardCeremonySheet,
        val scale: Scale,
        val rankPages: List<List<AwardCeremonyRank>>,
    )

    /** Der klassische Siegerehrungsbogen - nur noch ein Preset des einen Generators. */
    fun render(sheets: List<AwardCeremonySheet>): ByteArray = render(sheets, ResultListOptions.ceremony)

    fun render(sheets: List<AwardCeremonySheet>, options: ResultListOptions): ByteArray {
        // Ein PDF ohne Seiten öffnet kein gängiger Betrachter. Der aufrufende Service fängt die
        // leere Auswahl schon vorher mit einer eigenen Meldung ab; kommt sie trotzdem hier an, ist
        // das ein Programmierfehler und soll auffallen, statt als unbrauchbare Datei zum Drucker
        // zu gehen.
        require(sheets.isNotEmpty()) { "Ohne Abschnitte gibt es keine Ergebnisliste zu drucken." }

        val sizes = sizesFor(options.size)

        // Erst je Bogen Stufe und Aufteilung messen, dann alles in einem Zug setzen. Zwei
        // Durchgänge über dieselbe Layoutfunktion sind einfacher als das Zusammenführen mehrerer
        // Dokumente und liefern dasselbe Ergebnis.
        val layouts = sheets.map { layoutOf(it, options, sizes) }

        return document(format = PDRectangle.A4) {
            layouts.forEach { layout ->
                layout.rankPages.forEachIndexed { index, ranks ->
                    page { sheetPage(layout.sheet, layout.scale, ranks, continued = index > 0, options, sizes) }
                }
            }
        }.use { doc ->
            val out = ByteArrayOutputStream()
            doc.save(out)
            out.toByteArray()
        }
    }

    private fun layoutOf(sheet: AwardCeremonySheet, options: ResultListOptions, sizes: TypeSizes): Layout {
        val (scale, fitsOnOnePage) = fitFor(sheet, options, sizes)
        // Die Stufensuche hat den Bogen auf dieser Stufe bereits gesetzt und gezählt. Im Normalfall
        // passt er, und die Aufteilung ist damit schon beantwortet - eine zweite Messung derselben
        // Sache wäre je Bogen ein kompletter überflüssiger A4-Satz.
        val rankPages = if (fitsOnOnePage) listOf(sheet.ranks) else splitRankPages(sheet, scale, options, sizes)
        return Layout(sheet, scale, rankPages)
    }

    /** Die gewählte Stufe samt der Messung, die zu ihr geführt hat. */
    internal data class Fit(
        val scale: Scale,
        /** Auf dieser Stufe kommt der ganze Bogen mit einem Blatt aus. */
        val fitsOnOnePage: Boolean,
    )

    /**
     * Die erste Stufe, auf der der Bogen mit einem Blatt auskommt - sonst die unterste, der lesbare
     * Boden der Leiter.
     */
    internal fun fitFor(
        sheet: AwardCeremonySheet,
        options: ResultListOptions = ResultListOptions.ceremony,
        sizes: TypeSizes = sizesFor(options.size),
    ): Fit {
        sizes.scales.forEach { scale ->
            if (pagesOf(sheet, scale, sheet.ranks, continued = false, options, sizes) == 1) {
                return Fit(scale, true)
            }
        }
        return Fit(sizes.scales.last(), fitsOnOnePage = false)
    }

    /** Sichtbar bis zur Modulgrenze, damit der Test die Wahl der Stufe für sich prüfen kann. */
    internal fun scaleFor(
        sheet: AwardCeremonySheet,
        options: ResultListOptions = ResultListOptions.ceremony,
    ): Scale = fitFor(sheet, options).scale

    /**
     * Die Rangblöcke je Blatt für einen Bogen, der auch auf der untersten Stufe nicht auf ein Blatt
     * passt. Der Normalfall kommt hier gar nicht an - er ist mit der Stufensuche schon entschieden.
     */
    private fun splitRankPages(
        sheet: AwardCeremonySheet,
        scale: Scale,
        options: ResultListOptions,
        sizes: TypeSizes,
    ): List<List<AwardCeremonyRank>> {
        val pages = mutableListOf<List<AwardCeremonyRank>>()
        var rest = sheet.ranks
        while (rest.isNotEmpty()) {
            val continued = pages.isNotEmpty()
            // Je Blatt so viele Rangblöcke, wie darauf passen - aber mindestens einer, auch wenn
            // er allein überläuft. Sonst käme die Aufteilung nie voran.
            var take = rest.size
            while (take > 1 && pagesOf(sheet, scale, rest.take(take), continued, options, sizes) > 1) {
                take--
            }
            pages.add(rest.take(take))
            rest = rest.drop(take)
        }
        return pages
    }

    private fun pagesOf(
        sheet: AwardCeremonySheet,
        scale: Scale,
        ranks: List<AwardCeremonyRank>,
        continued: Boolean,
        options: ResultListOptions,
        sizes: TypeSizes,
    ): Int =
        document(format = PDRectangle.A4) {
            page { sheetPage(sheet, scale, ranks, continued, options, sizes) }
        }.use { it.numberOfPages }

    private fun BlockBuilder.sheetPage(
        sheet: AwardCeremonySheet,
        scale: Scale,
        ranks: List<AwardCeremonyRank>,
        continued: Boolean,
        options: ResultListOptions,
        sizes: TypeSizes,
    ) {
        // Nur wenn alle Ränge aus demselben Lauf stammen, gehört die Lauf-Angabe in den Kopf.
        // Bei A-/B-Finale oder Zeitläufen unterscheiden sie sich, und ein Kopf, der für alle
        // Boote den Lauf des Siegers behauptet, wäre schlechter als gar keine Angabe. `null` steht
        // hier bewusst für beides - „gar kein Lauf bekannt" und „die Läufe weichen ab": in beiden
        // Fällen ist die einzig richtige Kopfzeile keine.
        //
        // Gerechnet wird über alle Ränge des Bogens, nicht nur über die dieses Blatts: sonst
        // wanderte die Lauf-Angabe auf einer Fortsetzungsseite plötzlich in den Kopf.
        val commonRaceLine = sheet.ranks.map { it.team.raceLine }.distinct().singleOrNull()

        header(sheet, commonRaceLine, continued, options, sizes)
        ranks.forEachIndexed { index, rank ->
            rankBlock(rank, scale, ownRaceLine = commonRaceLine == null, firstOnPage = index == 0, options, sizes)
        }

        // Die Fußzeile steht mit auf jedem Blatt und damit in jeder Messung: ein Aushang, dessen
        // „Stand: …" auf eine ungemessene Folgeseite rutschte, wäre genau der Zettel, dem man
        // sein Alter nicht mehr ansieht.
        options.footerLine?.let {
            block(padding = Padding(top = 12f)) {
                text(fontSize = sizes.footer, centered = true) { it }
            }
        }
    }

    private fun BlockBuilder.header(
        sheet: AwardCeremonySheet,
        commonRaceLine: String?,
        continued: Boolean,
        options: ResultListOptions,
        sizes: TypeSizes,
    ) {
        block(padding = Padding(bottom = 18f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = sizes.heading, centered = true) { options.heading }
            if (continued) {
                text(fontStyle = FontStyle.BOLD, fontSize = sizes.continuation, centered = true) { CONTINUATION_MARK }
            }

            // Leerer Text zählt wie ein fehlender: sonst bliebe ein Trenner ohne Inhalt stehen
            // ("... · 15.-16. August 2026 · "), und ist am Ende nichts übrig, entfällt die ganze
            // Zeile - eine leere Textzeile rückt sonst trotzdem eine Zeilenhöhe vor.
            val eventLine = listOfNotNull(sheet.eventName, sheet.eventDate, sheet.eventLocation)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (eventLine.isNotBlank()) {
                text(fontSize = sizes.eventLine, centered = true) { eventLine }
            }
        }

        block(padding = Padding(bottom = 10f)) {
            val competitionLine = listOfNotNull(sheet.competitionIdentifier, sheet.competitionShortName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (competitionLine.isNotBlank()) {
                text(fontStyle = FontStyle.BOLD, fontSize = sizes.competitionLine) { competitionLine }
            }
            sheet.competitionName.takeIf { it.isNotBlank() }?.let {
                text(fontSize = sizes.competitionName) { it }
            }

            // Zweispaltig, weil das DSL nur zentriert oder linksbündig kann: links die Wertung,
            // rechts der Lauf. Beide Zellen dürfen leer bleiben.
            table(padding = Padding(top = 6f)) {
                column(0.5f)
                column(0.5f)
                row {
                    cell {
                        sheet.ratingCategoryName?.takeIf { it.isNotBlank() }?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = sizes.ratingLabel) { "Wertung: $it" }
                        }
                    }
                    cell {
                        commonRaceLine?.let {
                            text(fontSize = sizes.raceInfo, centered = true) { it }
                        }
                        sheet.ceremonyTime?.let {
                            text(fontSize = sizes.raceInfo, centered = true) { "Ehrung: ${it.format(ceremonyTimeFormat)}" }
                        }
                    }
                }
            }
        }
    }

    /**
     * @param ownRaceLine trägt der Kopf keine gemeinsame Lauf-Angabe, nennt jeder Block seine
     * eigene. Ränge ohne Angabe bleiben dabei ohne - ein Platzhalter wäre schlimmer als nichts.
     * @param firstOnPage dieser Block eröffnet das Blatt.
     */
    private fun BlockBuilder.rankBlock(
        entry: AwardCeremonyRank,
        scale: Scale,
        ownRaceLine: Boolean,
        firstOnPage: Boolean,
        options: ResultListOptions,
        sizes: TypeSizes,
    ) {
        block(keepTogether = true, padding = Padding(bottom = scale.gap)) {
            table {
                column(0.12f)
                column(0.63f)
                column(0.25f)

                row {
                    cell {
                        // Bei geteiltem Rang trägt nur das erste Boot die Zahl - zweimal
                        // dieselbe Zahl untereinander liest sich wie ein Fehler.
                        //
                        // Beginnt ein Blatt jedoch mitten in einem geteilten Rang, stünde der Rang
                        // dort nur noch im kleinen Vermerk. Ausgerechnet die Fortsetzungsseite, die
                        // den vollen Kopf wiederholt, um für sich allein zu taugen, hätte dann die
                        // eine Angabe nicht mehr, die aus der Distanz lesbar sein muss - deshalb
                        // eröffnet jedes Blatt mit seiner Rangzahl.
                        if (entry.first || firstOnPage) {
                            text(fontStyle = FontStyle.BOLD, fontSize = sizes.rankNumber) { "${entry.rank}." }
                        }
                    }
                    cell {
                        text(fontStyle = FontStyle.BOLD, fontSize = sizes.clubLine) { entry.team.clubLine }
                        if (entry.shared) {
                            text(fontSize = scale.metaSize) { "geteilter ${entry.rank}. Platz" }
                        }
                        if (ownRaceLine) {
                            entry.team.raceLine?.let {
                                text(fontSize = scale.metaSize) { it }
                            }
                        }
                    }
                    cell {
                        // Ohne Zeiten bleibt die Spalte bewusst ganz leer - auch ohne die Strafe:
                        // eine Zeitstrafe ohne die Zeit daneben sähe aus wie das Ergebnis selbst.
                        if (options.includeTimes) {
                            entry.team.time?.let {
                                text(fontStyle = FontStyle.BOLD, fontSize = sizes.time, centered = true) { it }
                            }
                            entry.team.penalty?.let {
                                text(fontSize = scale.metaSize, centered = true) { it }
                            }
                        }
                    }
                }
            }

            block(padding = Padding(left = 24f, top = 2f)) {
                text(fontSize = scale.metaSize) { entry.team.boatLine }
                entry.team.registeringClub?.let {
                    text(fontSize = scale.metaSize) { "Meldender Verein: $it" }
                }

                if (options.includeCrew) {
                    block(padding = Padding(top = 4f)) {
                        entry.team.athletes.forEach { athlete ->
                            text(fontSize = scale.nameSize) {
                                // Jahrgang hinter dem Namen: die Sprecherin liest ihn mit vor, und
                                // bei Altersklassen-Wertungen gehört er zur Ehrung dazu.
                                val name = "${athlete.name} (${athlete.year}, ${athlete.role})"
                                athlete.club?.let { "$name — $it" } ?: name
                            }
                        }
                    }
                }
            }
        }
    }
}

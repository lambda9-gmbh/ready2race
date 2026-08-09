package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyRank
import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonySheet
import de.lambda9.ready2race.backend.pdf.BlockBuilder
import de.lambda9.ready2race.backend.pdf.FontStyle
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.document
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Der Siegerehrungsbogen: je Wertungskategorie ein A4-Blatt, zum Vorlesen gesetzt.
 *
 * Die Passung auf ein Blatt ist *nicht* strukturell zugesichert: `page { }` legt bei Überlauf
 * still eine Folgeseite nach, und die trüge weder Veranstaltung noch Rennnummer noch Wertung -
 * ein Blatt, mit dem die Sprecherin nichts anfangen kann.
 *
 * Deshalb wird die Passung gemessen statt geschätzt. Jeder Bogen wird probeweise in [scales]
 * gesetzt, von der großzügigsten Stufe abwärts, und gedruckt wird die erste Stufe, die mit einer
 * Seite auskommt. Eine Schätzung über die Personenzahl könnte das nicht leisten: ob eine Zeile
 * umbricht, hängt an der Länge der Vereinsnamen, und über die Bootsgröße ist das Verhalten nicht
 * einmal monoton.
 *
 * Die Stufenleiter hat einen lesbaren Boden: unterhalb der letzten Stufe wird nicht weiter
 * geschrumpft, weil man kleinere Namenszeilen vom Pult aus nicht mehr abliest. Passt eine Ehrung
 * auch auf dieser Stufe nicht auf ein Blatt, bekommt sie mehrere. Der Umbruch läuft zwischen zwei
 * Rangblöcken - kein Boot wird von seiner Mannschaft getrennt -, jede Seite trägt den vollen Kopf,
 * und jede ab der zweiten zusätzlich [CONTINUATION_MARK], damit die Sprecherin sieht, dass das
 * Blatt zur selben Ehrung gehört. Auch diese Aufteilung wird gemessen, nicht geschätzt.
 */
object AwardCeremonyPdf {

    // Ohne Wochentag: dessen Abkürzung hängt an der CLDR-Fassung des JDK ("Sa" vs. "Sa.") und
    // machte die Ausgabe von der Java-Version abhängig - dieselbe Regel wie in AwardCeremonyLogic.
    private val ceremonyTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

    /**
     * Eine Schriftstufe des Rangblocks. Die Rangzahl steht bewusst nicht darin: sie muss auf jeder
     * Stufe vom Pult aus zu lesen sein, sonst sucht die Sprecherin beim Vorlesen den Platz.
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

    /** Kurz und in Worten: eine Seitenzahl allein beantwortet nicht, ob dies eine neue Ehrung ist. */
    private const val CONTINUATION_MARK = "Fortsetzung"

    /** Ein Bogen, fertig aufgeteilt: die Stufe steht fest, [rankPages] hält die Ränge je Blatt. */
    private data class Layout(
        val sheet: AwardCeremonySheet,
        val scale: Scale,
        val rankPages: List<List<AwardCeremonyRank>>,
    )

    fun render(sheets: List<AwardCeremonySheet>): ByteArray {
        // Ein PDF ohne Seiten öffnet kein gängiger Betrachter. Der aufrufende Service fängt die
        // leere Auswahl schon vorher mit einer eigenen Meldung ab; kommt sie trotzdem hier an, ist
        // das ein Programmierfehler und soll auffallen, statt als unbrauchbare Datei zum Drucker
        // zu gehen.
        require(sheets.isNotEmpty()) { "Ohne Ehrung gibt es keinen Siegerehrungsbogen zu drucken." }

        // Erst je Bogen Stufe und Aufteilung messen, dann alles in einem Zug setzen. Zwei
        // Durchgänge über dieselbe Layoutfunktion sind einfacher als das Zusammenführen mehrerer
        // Dokumente und liefern dasselbe Ergebnis.
        val layouts = sheets.map { layoutOf(it) }

        return document(format = PDRectangle.A4) {
            layouts.forEach { layout ->
                layout.rankPages.forEachIndexed { index, ranks ->
                    page { sheetPage(layout.sheet, layout.scale, ranks, continued = index > 0) }
                }
            }
        }.use { doc ->
            val out = ByteArrayOutputStream()
            doc.save(out)
            out.toByteArray()
        }
    }

    private fun layoutOf(sheet: AwardCeremonySheet): Layout {
        val scale = scaleFor(sheet)
        return Layout(sheet, scale, rankPagesOf(sheet, scale))
    }

    internal fun scaleFor(sheet: AwardCeremonySheet): Scale =
        scales.firstOrNull { pagesOf(sheet, it, sheet.ranks, continued = false) == 1 } ?: scales.last()

    /**
     * Die Rangblöcke je Blatt. Der Normalfall - alles passt auf ein Blatt - kostet genau eine
     * Messung und ergibt genau einen Eintrag.
     */
    private fun rankPagesOf(sheet: AwardCeremonySheet, scale: Scale): List<List<AwardCeremonyRank>> {
        if (pagesOf(sheet, scale, sheet.ranks, continued = false) == 1) {
            return listOf(sheet.ranks)
        }

        val pages = mutableListOf<List<AwardCeremonyRank>>()
        var rest = sheet.ranks
        while (rest.isNotEmpty()) {
            val continued = pages.isNotEmpty()
            // Je Blatt so viele Rangblöcke, wie darauf passen - aber mindestens einer, auch wenn
            // er allein überläuft. Sonst käme die Aufteilung nie voran.
            var take = rest.size
            while (take > 1 && pagesOf(sheet, scale, rest.take(take), continued) > 1) {
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
    ): Int =
        document(format = PDRectangle.A4) {
            page { sheetPage(sheet, scale, ranks, continued) }
        }.use { it.numberOfPages }

    private fun BlockBuilder.sheetPage(
        sheet: AwardCeremonySheet,
        scale: Scale,
        ranks: List<AwardCeremonyRank>,
        continued: Boolean,
    ) {
        // Nur wenn alle Ränge aus demselben Lauf stammen, gehört die Lauf-Angabe in den Kopf.
        // Bei A-/B-Finale oder Zeitläufen unterscheiden sie sich, und ein Kopf, der für alle drei
        // Boote den Lauf des Siegers behauptet, wäre schlechter als gar keine Angabe. `null` steht
        // hier bewusst für beides - „gar kein Lauf bekannt" und „die Läufe weichen ab": in beiden
        // Fällen ist die einzig richtige Kopfzeile keine.
        //
        // Gerechnet wird über alle Ränge des Bogens, nicht nur über die dieses Blatts: sonst
        // wanderte die Lauf-Angabe auf einer Fortsetzungsseite plötzlich in den Kopf.
        val commonRaceLine = sheet.ranks.map { it.team.raceLine }.distinct().singleOrNull()

        header(sheet, commonRaceLine, continued)
        ranks.forEach { rankBlock(it, scale, ownRaceLine = commonRaceLine == null) }
    }

    private fun BlockBuilder.header(sheet: AwardCeremonySheet, commonRaceLine: String?, continued: Boolean) {
        block(padding = Padding(bottom = 18f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 18f, centered = true) { "SIEGEREHRUNG" }
            if (continued) {
                text(fontStyle = FontStyle.BOLD, fontSize = 12f, centered = true) { CONTINUATION_MARK }
            }

            // Leerer Text zählt wie ein fehlender: sonst bliebe ein Trenner ohne Inhalt stehen
            // ("... · 15.-16. August 2026 · "), und ist am Ende nichts übrig, entfällt die ganze
            // Zeile - eine leere Textzeile rückt sonst trotzdem eine Zeilenhöhe vor.
            val eventLine = listOfNotNull(sheet.eventName, sheet.eventDate, sheet.eventLocation)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (eventLine.isNotBlank()) {
                text(fontSize = 11f, centered = true) { eventLine }
            }
        }

        block(padding = Padding(bottom = 10f)) {
            val competitionLine = listOfNotNull(sheet.competitionIdentifier, sheet.competitionShortName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (competitionLine.isNotBlank()) {
                text(fontStyle = FontStyle.BOLD, fontSize = 14f) { competitionLine }
            }
            sheet.competitionName.takeIf { it.isNotBlank() }?.let {
                text(fontSize = 12f) { it }
            }

            // Zweispaltig, weil das DSL nur zentriert oder linksbündig kann: links die Wertung,
            // rechts der Lauf. Beide Zellen dürfen leer bleiben.
            table(padding = Padding(top = 6f)) {
                column(0.5f)
                column(0.5f)
                row {
                    cell {
                        sheet.ratingCategoryName?.takeIf { it.isNotBlank() }?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = 12f) { "Wertung: $it" }
                        }
                    }
                    cell {
                        commonRaceLine?.let {
                            text(fontSize = 11f, centered = true) { it }
                        }
                        sheet.ceremonyTime?.let {
                            text(fontSize = 11f, centered = true) { "Ehrung: ${it.format(ceremonyTimeFormat)}" }
                        }
                    }
                }
            }
        }
    }

    /**
     * @param ownRaceLine trägt der Kopf keine gemeinsame Lauf-Angabe, nennt jeder Block seine
     * eigene. Ränge ohne Angabe bleiben dabei ohne - ein Platzhalter wäre schlimmer als nichts.
     */
    private fun BlockBuilder.rankBlock(entry: AwardCeremonyRank, scale: Scale, ownRaceLine: Boolean) {
        block(keepTogether = true, padding = Padding(bottom = scale.gap)) {
            table {
                column(0.12f)
                column(0.63f)
                column(0.25f)

                row {
                    cell {
                        // Bei geteiltem Rang trägt nur das erste Boot die Zahl - zweimal
                        // dieselbe Zahl untereinander liest sich wie ein Fehler.
                        if (entry.first) {
                            text(fontStyle = FontStyle.BOLD, fontSize = 20f) { "${entry.rank}." }
                        }
                    }
                    cell {
                        text(fontStyle = FontStyle.BOLD, fontSize = 14f) { entry.team.clubLine }
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
                        entry.team.time?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = 14f, centered = true) { it }
                        }
                        entry.team.penalty?.let {
                            text(fontSize = scale.metaSize, centered = true) { it }
                        }
                    }
                }
            }

            block(padding = Padding(left = 24f, top = 2f)) {
                text(fontSize = scale.metaSize) { entry.team.boatLine }
                entry.team.registeringClub?.let {
                    text(fontSize = scale.metaSize) { "Meldender Verein: $it" }
                }

                block(padding = Padding(top = 4f)) {
                    entry.team.athletes.forEach { athlete ->
                        text(fontSize = scale.nameSize) {
                            val name = "${athlete.name} (${athlete.role})"
                            athlete.club?.let { "$name — $it" } ?: name
                        }
                    }
                }
            }
        }
    }
}

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
 * Der Siegerehrungsbogen: je Wertungskategorie genau eine A4-Seite, zum Vorlesen gesetzt.
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
     * Von großzügig nach eng. Die erste Stufe ist die Wunschform, die letzte die Notform: passt
     * auch sie nicht, wird trotzdem in ihr gesetzt - ein zu eng gesetztes Blatt ist immer noch
     * besser als ein großzügig gesetztes, das auf zwei Blätter läuft.
     */
    internal val scales = listOf(
        Scale(nameSize = 12f, metaSize = 10f, gap = 14f),
        Scale(nameSize = 11f, metaSize = 9.5f, gap = 11f),
        Scale(nameSize = 10f, metaSize = 9f, gap = 8f),
        Scale(nameSize = 8.5f, metaSize = 8f, gap = 5f),
        Scale(nameSize = 7f, metaSize = 7f, gap = 3f),
    )

    fun render(sheets: List<AwardCeremonySheet>): ByteArray {
        // Erst je Bogen die Stufe messen, dann alle Bögen in einem Zug setzen. Zwei Durchgänge
        // über dieselbe Layoutfunktion sind einfacher als das Zusammenführen mehrerer Dokumente
        // und liefern dasselbe Ergebnis.
        val chosen = sheets.map { it to scaleFor(it) }

        return document(format = PDRectangle.A4) {
            chosen.forEach { (sheet, scale) ->
                page { sheetPage(sheet, scale) }
            }
        }.use { doc ->
            val out = ByteArrayOutputStream()
            doc.save(out)
            out.toByteArray()
        }
    }

    internal fun scaleFor(sheet: AwardCeremonySheet): Scale =
        scales.firstOrNull { pagesOf(sheet, it) == 1 } ?: scales.last()

    private fun pagesOf(sheet: AwardCeremonySheet, scale: Scale): Int =
        document(format = PDRectangle.A4) {
            page { sheetPage(sheet, scale) }
        }.use { it.numberOfPages }

    private fun BlockBuilder.sheetPage(sheet: AwardCeremonySheet, scale: Scale) {
        // Nur wenn alle Ränge aus demselben Lauf stammen, gehört die Lauf-Angabe in den Kopf.
        // Bei A-/B-Finale oder Zeitläufen unterscheiden sie sich, und ein Kopf, der für alle drei
        // Boote den Lauf des Siegers behauptet, wäre schlechter als gar keine Angabe.
        val commonRaceLine = sheet.ranks.map { it.team.raceLine }.distinct().singleOrNull()

        header(sheet, commonRaceLine)
        sheet.ranks.forEach { rankBlock(it, scale, ownRaceLine = commonRaceLine == null) }
    }

    private fun BlockBuilder.header(sheet: AwardCeremonySheet, commonRaceLine: String?) {
        block(padding = Padding(bottom = 18f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 18f, centered = true) { "SIEGEREHRUNG" }
            text(fontSize = 11f, centered = true) {
                // Leerer Text zählt wie ein fehlender: sonst bliebe ein Trenner ohne Inhalt
                // stehen ("... · 15.-16. August 2026 · ").
                listOfNotNull(sheet.eventName, sheet.eventDate, sheet.eventLocation)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            }
        }

        block(padding = Padding(bottom = 10f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 14f) {
                listOfNotNull(sheet.competitionIdentifier, sheet.competitionShortName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
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

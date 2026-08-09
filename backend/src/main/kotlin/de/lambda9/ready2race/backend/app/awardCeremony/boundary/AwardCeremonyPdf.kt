package de.lambda9.ready2race.backend.app.awardCeremony.boundary

import de.lambda9.ready2race.backend.app.awardCeremony.entity.AwardCeremonyDensity
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
 * Die Seitengrenze entsteht strukturell über `page { }` und nicht über eine Höhenrechnung -
 * damit kann eine Kategorie gar nicht auf zwei Blätter rutschen. Der Preis dafür ist die
 * Dichtestufe aus [AwardCeremonyLogic.densityFor]: ein sehr großes Feld wird enger gesetzt,
 * statt umzubrechen.
 */
object AwardCeremonyPdf {

    private val ceremonyTimeFormat = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy, HH:mm", Locale.GERMANY)

    fun render(sheets: List<AwardCeremonySheet>): ByteArray {
        val doc = document(format = PDRectangle.A4) {
            sheets.forEach { sheet ->
                page {
                    header(sheet)
                    sheet.ranks.forEach { rankBlock(it, sheet.density) }
                }
            }
        }

        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun BlockBuilder.header(sheet: AwardCeremonySheet) {
        block(padding = Padding(bottom = 18f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 18f, centered = true) { "SIEGEREHRUNG" }
            text(fontSize = 11f, centered = true) {
                listOfNotNull(sheet.eventName, sheet.eventDate.takeIf { it.isNotBlank() }, sheet.eventLocation)
                    .joinToString(" · ")
            }
        }

        block(padding = Padding(bottom = 10f)) {
            text(fontStyle = FontStyle.BOLD, fontSize = 14f) {
                listOfNotNull(sheet.competitionIdentifier, sheet.competitionShortName).joinToString(" · ")
            }
            text(fontSize = 12f) { sheet.competitionName }

            // Zweispaltig, weil das DSL nur zentriert oder linksbündig kann: links die Wertung,
            // rechts der Lauf. Beide Zellen dürfen leer bleiben.
            table(padding = Padding(top = 6f)) {
                column(0.5f)
                column(0.5f)
                row {
                    cell {
                        sheet.ratingCategoryName?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = 12f) { "Wertung: $it" }
                        }
                    }
                    cell {
                        // Die Lauf-Angabe der Ehrung ist die des ersten Rangs; alle Ränge einer
                        // Kategorie stammen in aller Regel aus demselben Lauf.
                        sheet.ranks.firstOrNull()?.team?.raceLine?.let {
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

    private fun BlockBuilder.rankBlock(entry: AwardCeremonyRank, density: AwardCeremonyDensity) {
        val nameSize = if (density == AwardCeremonyDensity.COMPACT) 10f else 12f
        val metaSize = if (density == AwardCeremonyDensity.COMPACT) 9f else 10f
        val gap = if (density == AwardCeremonyDensity.COMPACT) 8f else 14f

        block(keepTogether = true, padding = Padding(bottom = gap)) {
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
                            text(fontSize = metaSize) { "geteilter ${entry.rank}. Platz" }
                        }
                    }
                    cell {
                        entry.team.time?.let {
                            text(fontStyle = FontStyle.BOLD, fontSize = 14f, centered = true) { it }
                        }
                        entry.team.penalty?.let {
                            text(fontSize = metaSize, centered = true) { it }
                        }
                    }
                }
            }

            block(padding = Padding(left = 24f, top = 2f)) {
                text(fontSize = metaSize) { entry.team.boatLine }
                entry.team.registeringClub?.let {
                    text(fontSize = metaSize) { "Meldender Verein: $it" }
                }

                block(padding = Padding(top = 4f)) {
                    entry.team.athletes.forEach { athlete ->
                        text(fontSize = nameSize) {
                            val name = "${athlete.name} (${athlete.role})"
                            athlete.club?.let { "$name — $it" } ?: name
                        }
                    }
                }
            }
        }
    }
}

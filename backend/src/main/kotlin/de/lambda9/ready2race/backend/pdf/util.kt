package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font

const val POINTS_PER_INCH = 72f

const val POINTS_PER_MM = 1 / 25.4f * POINTS_PER_INCH

fun checkValidPdf(bytes: ByteArray): Boolean =
    try {
        Loader.loadPDF(bytes).use {
            true
        }
    } catch (e: Exception) {
        false
    }

/**
 * Prüft, ob PDFBox die Schrift-Bytes als eingebettbare Type0-Schrift laden kann — derselbe Aufruf,
 * mit dem die Schrift beim Erzeugen der Urkunden eingebettet wird (siehe `GapFonts.load`). Ein
 * kaputter Upload soll beim Speichern der Vorlage auffallen, nicht erst beim Generieren.
 */
fun checkValidFont(bytes: ByteArray): Boolean =
    try {
        PDDocument().use { doc ->
            PDType0Font.load(doc, bytes.inputStream())
        }
        true
    } catch (e: Exception) {
        false
    }

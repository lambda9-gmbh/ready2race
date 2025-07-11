package de.lambda9.ready2race.backend.pdf

import org.apache.pdfbox.Loader

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
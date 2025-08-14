package de.lambda9.ready2race.backend.xls

import org.apache.poi.ss.usermodel.WorkbookFactory

fun checkValidXls(bytes: ByteArray): Boolean =
    try {
        WorkbookFactory.create(bytes.inputStream())
        true
    } catch (e: Exception) {
        false
    }
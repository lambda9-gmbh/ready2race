package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateAssignmentRecord
import de.lambda9.ready2race.backend.pdf.PageMargin
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.tailwind.core.KIO

fun DocumentTemplateAssignmentRecord.toPdfTemplate(): App<Nothing, PageTemplate> = KIO.ok(
    PageTemplate(
        bytes = data!!,
        pageMargin = PageMargin(
            pageMarginTop = pageMarginTop?.toFloat() ?: PageMargin.default.pageMarginTop,
            pageMarginLeft = pageMarginLeft?.toFloat() ?: PageMargin.default.pageMarginLeft,
            pageMarginRight = pageMarginRight?.toFloat() ?: PageMargin.default.pageMarginRight,
            pageMarginBottom = pageMarginBottom?.toFloat() ?: PageMargin.default.pageMarginBottom,
        ),
    )
)
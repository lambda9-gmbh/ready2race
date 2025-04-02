package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateAssignmentRecord
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.tailwind.core.KIO

fun DocumentTemplateAssignmentRecord.toPdfTemplate(): App<Nothing, PageTemplate> = KIO.ok(
    PageTemplate(
        bytes = data!!,
        pagepadding = Padding(
            top = pagePaddingTop?.toFloat() ?: Padding.defaultPagePadding.top,
            left = pagePaddingLeft?.toFloat() ?: Padding.defaultPagePadding.left,
            right = pagePaddingRight?.toFloat() ?: Padding.defaultPagePadding.right,
            bottom = pagePaddingBottom?.toFloat() ?: Padding.defaultPagePadding.bottom,
        ),
    )
)
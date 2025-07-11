package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateRequest
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateAssignmentRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateRecord
import de.lambda9.ready2race.backend.pdf.POINTS_PER_MM
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.tailwind.core.KIO
import java.util.UUID

fun DocumentTemplateAssignmentRecord.toPdfTemplate(): App<Nothing, PageTemplate> = KIO.ok(
    PageTemplate(
        bytes = data!!,
        pagepadding = Padding.fromMillimetersOrDefault(
            top = pagePaddingTop?.toFloat(),
            left = pagePaddingLeft?.toFloat(),
            right = pagePaddingRight?.toFloat(),
            bottom = pagePaddingBottom?.toFloat(),
        ),
    )
)

fun DocumentTemplateRecord.toDto(): App<Nothing, DocumentTemplateDto> = KIO.ok(
    DocumentTemplateDto(
        id = id,
        name = name,
        pagePaddingTop = pagePaddingTop,
        pagePaddingLeft = pagePaddingLeft,
        pagePaddingRight = pagePaddingRight,
        pagePaddingBottom = pagePaddingBottom
    )
)

fun DocumentTemplateRequest.toRecord(name: String): App<Nothing, DocumentTemplateRecord> = KIO.ok(
    DocumentTemplateRecord(
        id = UUID.randomUUID(),
        name = name,
        pagePaddingTop = pagePaddingTop,
        pagePaddingLeft = pagePaddingLeft,
        pagePaddingRight = pagePaddingRight,
        pagePaddingBottom = pagePaddingBottom,
    )
)
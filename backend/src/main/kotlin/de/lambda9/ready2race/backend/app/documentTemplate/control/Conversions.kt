package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.DocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentPlaceholderType
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateDto
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentTemplateRequest
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapDocumentType
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateAssignmentRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.DocumentTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentPlaceholderRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateViewRecord
import de.lambda9.ready2race.backend.pdf.POINTS_PER_MM
import de.lambda9.ready2race.backend.pdf.Padding
import de.lambda9.ready2race.backend.pdf.PageTemplate
import de.lambda9.ready2race.backend.text.TextAlign
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

fun GapDocumentTemplateRequest.toRecord(name: String) =
    GapDocumentTemplateRecord(
        id = UUID.randomUUID(),
        name = name,
        type = type.name,
    )

fun GapDocumentPlaceholderRecord.toDto() =
    GapDocumentPlaceholderDto(
        id = id,
        name = name,
        type = GapDocumentPlaceholderType.valueOf(type),
        page = page,
        relLeft = relLeft,
        relTop = relTop,
        relWidth = relWidth,
        relHeight = relHeight,
        textAlign = TextAlign.valueOf(textAlign),
    )

fun GapDocumentTemplateViewRecord.toDto() =
    GapDocumentTemplateDto(
        id = id!!,
        name = name!!,
        type = GapDocumentType.valueOf(type!!),
        placeholders = placeholders!!.map { it!!.toDto() },
    )

fun GapDocumentPlaceholderRequest.toRecord(template: UUID) =
    GapDocumentPlaceholderRecord(
        id = UUID.randomUUID(),
        template = template,
        name = name,
        type = type.name,
        page = page,
        relLeft = relLeft,
        relTop = relTop,
        relWidth = relWidth,
        relHeight = relHeight,
        textAlign = textAlign.name,
    )
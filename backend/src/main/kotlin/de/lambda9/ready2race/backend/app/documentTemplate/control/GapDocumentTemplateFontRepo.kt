package de.lambda9.ready2race.backend.app.documentTemplate.control

import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.GapDocumentTemplateFontRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.GAP_DOCUMENT_TEMPLATE_FONT
import de.lambda9.ready2race.backend.database.insertReturning
import de.lambda9.ready2race.backend.database.selectOne
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.extensions.kio.onNull
import java.util.UUID

object GapDocumentTemplateFontRepo {

    fun get(template: UUID) = GAP_DOCUMENT_TEMPLATE_FONT.selectOne { TEMPLATE.eq(template) }

    fun upsert(record: GapDocumentTemplateFontRecord) =
        GAP_DOCUMENT_TEMPLATE_FONT.update(f = {
            fileName = record.fileName
            data = record.data
        }) { TEMPLATE.eq(record.template) }
            .onNull { GAP_DOCUMENT_TEMPLATE_FONT.insertReturning(record) }

    fun delete(template: UUID) = GAP_DOCUMENT_TEMPLATE_FONT.delete { TEMPLATE.eq(template) }
}

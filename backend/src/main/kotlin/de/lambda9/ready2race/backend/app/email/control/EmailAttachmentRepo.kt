package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.database.generated.tables.records.EmailAttachmentRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_ATTACHMENT
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import java.util.*

object EmailAttachmentRepo {

    fun create(records: List<EmailAttachmentRecord>) = EMAIL_ATTACHMENT.insert(records)

    fun getByEmail(
        emailId: UUID,
    ): JIO<List<EmailAttachmentRecord>> = Jooq.query {
        with(EMAIL_ATTACHMENT) {
            selectFrom(this)
                .where(EMAIL.eq(emailId))
                .fetch()
        }
    }
}
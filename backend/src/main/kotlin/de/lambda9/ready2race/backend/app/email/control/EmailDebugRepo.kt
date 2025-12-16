package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_ATTACHMENT
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object EmailDebugRepo {

    data class EmailWithAttachments(
        val id: UUID,
        val subject: String,
        val body: String,
        val bodyIsHtml: Boolean,
        val recipient: String,
        val sentAt: LocalDateTime?,
        val createdAt: LocalDateTime,
        val error: String?,
        val attachments: List<AttachmentInfo>
    )

    data class AttachmentInfo(
        val filename: String?,
    )

    data class EmailDebugResult(
        val emails: List<EmailWithAttachments>,
        val totalCount: Int
    )

    fun listEmailsWithAttachments(
        search: String?,
        page: Int,
        pageSize: Int
    ): JIO<EmailDebugResult> = Jooq.query {
        // Build the where condition
        val condition = search?.let {
            EMAIL.RECIPIENT.containsIgnoreCase(it)
        } ?: DSL.noCondition()

        // Get total count
        val totalCount = selectCount()
            .from(EMAIL)
            .where(condition)
            .fetchOne(0, Int::class.java) ?: 0

        // Fetch emails with pagination
        val emails = selectFrom(EMAIL)
            .where(condition)
            .orderBy(EMAIL.CREATED_AT.desc())
            .limit(pageSize)
            .offset((page - 1) * pageSize)
            .fetch()

        // For each email, fetch attachments
        val emailsWithAttachments = emails.map { emailRecord ->
            val attachments = select(
                EMAIL_ATTACHMENT.NAME,
            )
                .from(EMAIL_ATTACHMENT)
                .where(EMAIL_ATTACHMENT.EMAIL.eq(emailRecord.id))
                .fetch()
                .map {
                    AttachmentInfo(
                        filename = it[EMAIL_ATTACHMENT.NAME],
                    )
                }

            EmailWithAttachments(
                id = emailRecord.id,
                subject = emailRecord.subject,
                body = emailRecord.body,
                bodyIsHtml = emailRecord.bodyIsHtml,
                recipient = emailRecord.recipient,
                sentAt = emailRecord.sentAt,
                createdAt = emailRecord.createdAt,
                error = emailRecord.lastError,
                attachments = attachments
            )
        }

        EmailDebugResult(
            emails = emailsWithAttachments,
            totalCount = totalCount
        )
    }
}
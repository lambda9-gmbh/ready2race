package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailIndividualTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_INDIVIDUAL_TEMPLATE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq

object EmailIndividualTemplateRepo {

    fun create(record: EmailIndividualTemplateRecord) = EMAIL_INDIVIDUAL_TEMPLATE.insert(record)

    fun get(
        key: EmailTemplateKey,
        language: EmailLanguage,
    ): JIO<EmailIndividualTemplateRecord?> = Jooq.query {
        with(EMAIL_INDIVIDUAL_TEMPLATE) {
            selectFrom(this)
                .where(KEY.eq(key.name))
                .and(LANGUAGE.eq(language.name))
                .fetchOne()
        }
    }

    fun delete(
        key: EmailTemplateKey,
        language: EmailLanguage,
    ): JIO<Unit> = Jooq.query {
        with(EMAIL_INDIVIDUAL_TEMPLATE) {
            deleteFrom(this)
                .where(KEY.eq(key.name))
                .and(LANGUAGE.eq(language.name))
                .execute()
        }
    }
}
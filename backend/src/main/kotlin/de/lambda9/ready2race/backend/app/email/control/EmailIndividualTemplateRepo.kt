package de.lambda9.ready2race.backend.app.email.control

import de.lambda9.ready2race.backend.app.email.entity.EmailLanguage
import de.lambda9.ready2race.backend.app.email.entity.EmailTemplateKey
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.EmailIndividualTemplateRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EMAIL_INDIVIDUAL_TEMPLATE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL

object EmailIndividualTemplateRepo {

    fun create(record: EmailIndividualTemplateRecord) = EMAIL_INDIVIDUAL_TEMPLATE.insert(record)

    fun delete(key: EmailTemplateKey, language: EmailLanguage) = EMAIL_INDIVIDUAL_TEMPLATE.delete {
        DSL.and(
            KEY.eq(key.name),
            LANGUAGE.eq(language.name)
        )
    }

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
}
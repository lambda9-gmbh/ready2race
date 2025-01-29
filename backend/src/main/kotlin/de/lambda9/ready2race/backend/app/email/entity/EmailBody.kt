package de.lambda9.ready2race.backend.app.email.entity

sealed interface EmailBody {

    @JvmInline
    value class Text(val text: String): EmailBody

    @JvmInline
    value class Html(val html: String): EmailBody

    fun map(f: (String) -> String): EmailBody = when (this) {
        is Html -> Html(f(html))
        is Text -> Text(f(text))
    }
}
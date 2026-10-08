package de.lambda9.ready2race.backend.config

import java.net.URI

/** Exact browser origins only; no paths, credentials or wildcard hosts. */
fun parseCorsOrigin(value: String): URI = URI(value).also { uri ->
    require(uri.scheme in listOf("http", "https") && uri.host != null &&
        uri.rawUserInfo == null && uri.rawPath.isNullOrEmpty() &&
        uri.rawQuery == null && uri.rawFragment == null &&
        (uri.port == -1 || uri.port in 1..65535)) {
        "CORS origins must be HTTP(S) origins without a path: $value"
    }
}

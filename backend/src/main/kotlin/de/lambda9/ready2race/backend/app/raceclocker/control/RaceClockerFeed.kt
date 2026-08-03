package de.lambda9.ready2race.backend.app.raceclocker.control

import com.fasterxml.jackson.databind.JsonNode
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.time.LocalTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.UUID

/**
 * Reads the public results feed of a RaceClocker race.
 *
 * RaceClocker offers no import API, so start lists still travel as CSV by hand. The way back is
 * automated: appending `json=1` to any public results URL returns the raw timing data. The feed is a
 * flat array of participant objects followed by a single trailing `RaceInfo` object.
 *
 * The whole race is returned at once - every wave, every round. Callers narrow it down to a single
 * match themselves (see [RaceClockerFeedRow.wave]).
 */
object RaceClockerFeed {

    /**
     * RaceClocker publishes under both the apex and the www host. Restricting the host is what keeps
     * this endpoint from being an SSRF lever: the URL is operator-supplied per competition, so
     * without this check the backend could be pointed at internal services.
     */
    private val allowedHosts = setOf("raceclocker.com", "www.raceclocker.com")

    private const val TIMEOUT_MS = 10_000L
    private const val MAX_BYTES = 8 * 1024 * 1024

    /**
     * Time trial races have no waves; RaceClocker fills the field with a localised placeholder
     * instead of leaving it empty (`None` on English accounts, `Kein` on German ones).
     */
    private val noWaveValues = setOf("none", "kein", "-", "")

    /**
     * Normalises what a user pastes out of the browser into the URL this integration stores.
     *
     * A scheme-less input (`www.raceclocker.com/xxxx`) would be read as a *path* by [URLBuilder] and
     * then fail the host check, so the scheme is filled in. An explicitly typed `http://` is lifted to
     * HTTPS rather than rejected - what keeps this endpoint from being an SSRF lever is the host
     * allowlist, not the scheme.
     */
    fun normalizeUrl(raw: String): IO<RaceClockerError, Url> {
        val trimmed = raw.trim()
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"

        val url = try {
            URLBuilder(withScheme).apply {
                if (protocol != URLProtocol.HTTPS) {
                    // Drop a port that was only the old scheme's default, so lifting http keeps the
                    // URL free of an explicit ":80".
                    if (port == protocol.defaultPort) port = DEFAULT_PORT
                    protocol = URLProtocol.HTTPS
                }
            }.build()
        } catch (e: Exception) {
            return KIO.fail(RaceClockerError.UrlInvalid(raw))
        }

        if (url.host.lowercase() !in allowedHosts) return KIO.fail(RaceClockerError.UrlInvalid(raw))

        return KIO.ok(url)
    }

    /**
     * The JSON variant of a results URL. Works for both the short form (raceclocker.com/7c854955) and
     * the long one (Event_Result.php?EIDK=...), which already carries query parameters.
     */
    fun feedUrl(url: Url): Url = URLBuilder(url).apply { parameters["json"] = "1" }.build()

    suspend fun fetch(url: Url): IO<RaceClockerError, List<RaceClockerFeedRow>> {
        val body = try {
            HttpClient(CIO) {
                engine { requestTimeout = TIMEOUT_MS }
                expectSuccess = false
            }.use { client ->
                val response = client.get(url)
                if (!response.status.isSuccess()) {
                    return KIO.fail(RaceClockerError.Unreachable(url.toString(), "HTTP ${response.status.value}"))
                }
                val text = response.bodyAsText()
                if (text.length > MAX_BYTES) {
                    return KIO.fail(RaceClockerError.Unreachable(url.toString(), "response too large"))
                }
                text
            }
        } catch (e: Exception) {
            return KIO.fail(RaceClockerError.Unreachable(url.toString(), e.message ?: e::class.simpleName ?: "unknown"))
        }

        return parse(body)
    }

    fun parse(body: String): IO<RaceClockerError, List<RaceClockerFeedRow>> {
        val root = try {
            jsonMapper.readTree(body)
        } catch (e: Exception) {
            return KIO.fail(RaceClockerError.MalformedFeed(e.message ?: "not valid JSON"))
        }

        if (!root.isArray) return KIO.fail(RaceClockerError.MalformedFeed("expected a JSON array"))

        return KIO.ok(
            root.mapNotNull { node ->
                // The trailing RaceInfo object describes the race itself, not a participant.
                if (!node.isObject || node.has("RaceInfo")) null else node.toRow()
            }
        )
    }

    /**
     * RaceClocker localises this key (`Start` on English accounts, `Startzeit` on German ones), so
     * the field is looked up by name rather than by a fixed key, compared case-insensitively.
     */
    private val startKeys = setOf("start", "startzeit")

    /**
     * Matches the fixed shape RaceClocker writes times in - `H:mm:ss` with an optional fractional
     * second (`11:00:00.0`). The fraction is accepted with variable width since it is not needed for
     * the value we keep ([RaceClockerFeedRow.start] only distinguishes whole seconds).
     */
    private val timeOfDayFormat = DateTimeFormatterBuilder()
        .appendPattern("H:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .optionalEnd()
        .toFormatter()

    /**
     * Unparsable/missing values are `null`, not an error - this field is auxiliary data.
     *
     * RaceClocker liefert 00:00:00.0 als Platzhalter für Boote ohne Start (DNS/DNF/DQ) - Mitternacht
     * ist bei einer Regatta nie ein echter Start. Ohne diese Sonderbehandlung würde der Platzhalter
     * als [LocalTime.MIDNIGHT] geparst und über [RaceClockerFeedRow.earliestStart] den echten
     * `started_at`-Wert eines Laufs überschreiben, sobald ein Boot der Welle keinen Start hat.
     */
    private fun parseTimeOfDay(raw: String): LocalTime? =
        try {
            LocalTime.parse(raw, timeOfDayFormat).takeUnless { it == LocalTime.MIDNIGHT }
        } catch (e: DateTimeParseException) {
            null
        }

    private fun JsonNode.textForKeys(keys: Set<String>): String? {
        val key = fieldNames().asSequence().firstOrNull { it.lowercase() in keys } ?: return null
        return path(key).asText("").trim()
    }

    private fun JsonNode.toRow(): RaceClockerFeedRow {
        val wave = path("Wave").asText("").trim()
        return RaceClockerFeedRow(
            name = path("Name").asText("").trim(),
            bib = path("Bib number").asText("").trim().toIntOrNull(),
            wave = wave.takeUnless { it.lowercase() in noWaveValues },
            ids = extractIds(),
            result = path("Result").asText("").trim().takeIf { it.isNotBlank() },
            start = textForKeys(startKeys)?.takeIf { it.isNotBlank() }?.let { parseTimeOfDay(it) },
        )
    }

    /**
     * The r2r identifiers ride along in RaceClocker's "Extra info", which the feed returns as a list of
     * `[label, value]` pairs. We collect every value that parses as a UUID rather than looking for a
     * fixed label, so renaming the exported column in the start list config cannot break the round trip.
     *
     * Which kind of id a value is - match team or registration - is decided by the caller, which knows
     * the ids of the match it is pulling for. That keeps this parser free of any assumption about how
     * many id columns a start list config exports.
     */
    private fun JsonNode.extractIds(): List<UUID> {
        val extra = path("ExtraInfo")
        if (!extra.isArray) return emptyList()

        return extra.mapNotNull { pair ->
            if (!pair.isArray || pair.size() < 2) null
            else try {
                UUID.fromString(pair[1].asText("").trim())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

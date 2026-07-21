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

    fun validateUrl(raw: String): IO<RaceClockerError, Url> {
        val url = try {
            URLBuilder(raw.trim()).build()
        } catch (e: Exception) {
            return KIO.fail(RaceClockerError.UrlInvalid(raw))
        }

        if (url.protocol != URLProtocol.HTTPS) return KIO.fail(RaceClockerError.UrlInvalid(raw))
        if (url.host.lowercase() !in allowedHosts) return KIO.fail(RaceClockerError.UrlInvalid(raw))

        // Works for both the short form (raceclocker.com/7c854955) and the long one
        // (Event_Result.php?EIDK=...), which already carries query parameters.
        return KIO.ok(
            URLBuilder(url).apply { parameters["json"] = "1" }.build()
        )
    }

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

    private fun JsonNode.toRow(): RaceClockerFeedRow {
        val wave = path("Wave").asText("").trim()
        return RaceClockerFeedRow(
            name = path("Name").asText("").trim(),
            bib = path("Bib number").asText("").trim().toIntOrNull(),
            wave = wave.takeUnless { it.lowercase() in noWaveValues },
            registrationId = extractRegistrationId(),
            result = path("Result").asText("").trim().takeIf { it.isNotBlank() },
        )
    }

    /**
     * The registration id rides along in RaceClocker's "Extra info", which the feed returns as a list
     * of `[label, value]` pairs. We look for a value that parses as a UUID rather than for a fixed
     * label, so renaming the exported column in the start list config cannot break the round trip.
     */
    private fun JsonNode.extractRegistrationId(): UUID? {
        val extra = path("ExtraInfo")
        if (!extra.isArray) return null

        val ids = extra.mapNotNull { pair ->
            if (!pair.isArray || pair.size() < 2) null
            else try {
                UUID.fromString(pair[1].asText("").trim())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        // More than one UUID means we cannot tell which is ours - treat it as absent rather than guess.
        return ids.singleOrNull()
    }
}

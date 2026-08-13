package de.lambda9.ready2race.backend.app.matchStatus.control

import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object MatchByeRepo {

    /**
     * Je Team-Zeile aller Läufe einer Veranstaltung die Angaben, aus denen
     * [de.lambda9.ready2race.backend.app.matchStatus.boundary.MatchStatusLogic.deriveBye] ein
     * Freilos ableitet.
     *
     * Eine eigene Abfrage und nicht eine Erweiterung der bestehenden Team-Abfragen: die filtern
     * allesamt `out`-Zeilen heraus (`CompetitionExecutionService.getProgress`,
     * `LiveDashboardRepo.getTeams`), und genau in denen steckt der abgemeldete Gegner. Die
     * Nutzlast des Schiedsrichter-Dashboards - und damit sein ETag - bleibt so unberührt.
     */
    fun getByeInputs(eventId: UUID, competitionId: UUID? = null) = Jooq.query {
        select(
            COMPETITION_MATCH_TEAM.COMPETITION_MATCH.`as`("setup_match_id"),
            COMPETITION_SETUP_ROUND.REQUIRED.`as`("round_required"),
            COMPETITION_MATCH_TEAM.OUT.`as`("team_out"),
            CLUB.NAME.`as`("club_name"),
            COMPETITION_REGISTRATION.NAME.`as`("team_name"),
            COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.isNotNull.`as`("deregistered"),
            COMPETITION_DEREGISTRATION.REASON.`as`("deregistration_reason"),
            COMPETITION_MATCH.BYE_MUST_RACE.`as`("bye_must_race"),
            COMPETITION_SETUP_PARTICIPANT.SEED.`as`("team_seed"),
        )
            .from(COMPETITION_MATCH_TEAM)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            // Der Lauf zum Setup-Lauf: competition_match ist über competition_setup_match
            // Primärschlüssel-gejoint - existiert die Team-Zeile, existiert auch der Lauf.
            .join(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_MATCH_TEAM.COMPETITION_MATCH))
            // Der Setup-Platz dieser Mannschaft, für die Setzungszahl im Freilos-Label
            // ("Freilos 1"): `createNewRound` vergibt die Startnummer als `ranking` des
            // Setup-Platzes, dessen `seed` die Mannschaft belegt - die Rückrichtung ist also
            // genau dieser Join. Left join, weil nicht jeder Lauf Setup-Plätze hat (Erstrunden
            // werden über die Standard-Setzliste besetzt) und Startnummern nachträglich
            // umgetragen sein können (Bahnentausch) - dann bleibt die Zahl schlicht weg.
            .leftJoin(COMPETITION_SETUP_PARTICIPANT)
            .on(
                COMPETITION_SETUP_PARTICIPANT.COMPETITION_SETUP_MATCH.eq(COMPETITION_MATCH_TEAM.COMPETITION_MATCH)
                    .and(COMPETITION_SETUP_PARTICIPANT.RANKING.eq(COMPETITION_MATCH_TEAM.START_NUMBER))
            )
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(COMPETITION_REGISTRATION)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
            .leftJoin(CLUB).on(CLUB.ID.eq(COMPETITION_REGISTRATION.CLUB))
            // Bewusst OHNE Rundenbedingung, anders als in LiveDashboardRepo.getTeams: die
            // Abmeldung ist je Meldung eindeutig (unique index auf competition_registration).
            // Genau deshalb trägt sie auch für eine Zeile, die als `out` aus einer früheren Runde
            // mitgeführt wird - und das ist der Fall, den "Freilos wegen Abmeldung" meint.
            .leftJoin(COMPETITION_DEREGISTRATION)
            .on(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(competitionId?.let { COMPETITION.ID.eq(it) } ?: DSL.noCondition())
            // Deterministische Namensreihenfolge: das Schiedsrichter-Dashboard hasht das
            // serialisierte DTO für seinen ETag, aus demselben Grund sortiert LiveDashboardService
            // seine Teams vor der Serialisierung.
            .orderBy(
                COMPETITION_MATCH_TEAM.COMPETITION_MATCH,
                COMPETITION_MATCH_TEAM.START_NUMBER,
            )
            .fetch()
    }
}

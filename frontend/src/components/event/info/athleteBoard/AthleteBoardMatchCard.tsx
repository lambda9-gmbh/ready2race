import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch} from '@api/types.gen'
import {byeExplanation} from '@components/event/match/matchBye.ts'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {
    AthleteBoardBoatList,
    AthleteBoardBoatRow,
    AthleteBoardBoatStatus,
    AthleteBoardBoatSubline,
    AthleteBoardLapTimes,
} from './AthleteBoardBoatRow'
import {
    COUNTDOWN_MAX_SECONDS,
    finishComplete,
    formatClockTime,
    formatRemaining,
    formatShortDate,
    isSameDay,
    scaled,
    sortRunningTeams,
} from './common'
import PlaceOrdinal from '@components/PlaceOrdinal'

/**
 * "running": Karte im Block "Aktueller Lauf" — das Boot ist bereits in der Arena,
 * eine verstrichene Startzeit ist hier der Normalfall und wird nicht kommentiert.
 * "upcoming": Karte im Block "Nächster Lauf" — nur hier ergeben Countdown und der
 * Hinweis "erwartet" (Start verpasst) inhaltlich einen Sinn, siehe KDoc von
 * AthleteBoardStartState im Backend ("nur im Block upcoming aussagekräftig").
 */
type AthleteBoardMatchCardVariant = 'running' | 'upcoming'

interface AthleteBoardMatchCardProps {
    match: AthleteBoardMatch
    now: Date
    variant: AthleteBoardMatchCardVariant
    // Nur innerhalb variant="upcoming" relevant: ob zusätzlich zum "erwartet"-Hinweis
    // auch die genaue Countdown-Zahl gezeigt wird (Einstellung des Board-Elements).
    showCountdown?: boolean
    // Namenszeilen der Besatzung unter der Vereinskette — in kleinen Kacheln (6er-Board)
    // abschaltbar, damit die Bootszeile nur eine Textzeile trägt.
    showCrew?: boolean
    // Teilergebnisse (Platz/Zeit) im laufenden Lauf — je Board-Element abschaltbar.
    showTimes?: boolean
    // Sprecherinnen-Details: jede Athletin auf eigener Zeile mit Rolle und Heimatverein.
    // Die Daten kommen nur mit, wenn ein Board-Element sie anfordert (Backend).
    showCrewDetails?: boolean
    // Geburtsjahr je Athletin — nur zusammen mit showCrewDetails sichtbar.
    showBirthYears?: boolean
    // „Weiter kommen N Boote → Finale" unter dem Kopf des Laufs.
    showAdvancement?: boolean
    // Meldender Verein als kleine Zeile am Boot.
    showRegisteringClub?: boolean
}

const AthleteBoardMatchCard = ({
    match,
    now,
    variant,
    showCountdown = true,
    showCrew = true,
    showTimes = true,
    showCrewDetails = false,
    showBirthYears = false,
    showAdvancement = false,
    showRegisteringClub = false,
}: AthleteBoardMatchCardProps) => {
    const {t} = useTranslation()
    // Der Freilos-Schlüssel steht erst zur Laufzeit fest — dieselbe gelockerte Signatur wie in
    // Zeitplan und Schiedsrichter-Dashboard.
    const translate = t as (key: string, values?: Record<string, string | number>) => string

    // „Muss gefahren werden"-Freilos: die Zweitzeile erklärt der Besatzung am Steg, warum das
    // Boot allein fährt (Fairness) und dass die Zeit nicht fürs Weiterkommen zählt.
    const bye = byeExplanation(match.bye)

    const startsInSeconds = match.startTime
        ? (new Date(match.startTime).getTime() - now.getTime()) / 1000
        : null

    // Der Server liefert den Zustand beim Abruf; zwischen zwei Abrufen läuft die Uhr
    // lokal weiter, deshalb wird der Übergang zu "erwartet" hier noch einmal geprüft.
    // Beides trägt nur im Block "Nächster Lauf" Bedeutung (siehe KDoc von
    // AthleteBoardStartState im Backend) — deshalb hängt der Hinweis am variant-Schalter.
    const overdue =
        variant === 'upcoming' &&
        (match.startState === 'OVERDUE' || (startsInSeconds !== null && startsInSeconds <= 0))

    // Im Block "Aktueller Lauf" unterscheidet der Zustand: ein Lauf in Vorbereitung ist an den
    // Start gerufen, liegt aber noch am Steg. Der Zustand kommt vom Server (`match.state`, gefüllt
    // über dieselbe `deriveMatchState` wie in jeder anderen Oberfläche) — vorher stand hier eine
    // zweite Ableitung aus `actualStartTime`, die dasselbe behaupten sollte und dabei zwangsläufig
    // von den übrigen Anzeigen abweichen konnte. `actualStartTime` trägt jetzt nur noch die Uhrzeit
    // für "gestartet 14:32". Im Block "Nächster Lauf" ist sie immer leer (siehe KDoc von
    // AthleteBoardMatch im Backend).
    const renderRunningStart = () =>
        // Ein Programmpunkt (FREE-Platzhalter) startet nicht und wird nicht gestempelt.
        match.name ? null : match.state === 'PREPARING' ? (
            <Typography
                sx={{fontSize: scaled('0.75rem', '1.3vw', '1.6rem')}}
                color="text.secondary">
                {t('event.info.athleteBoard.preparing')}
            </Typography>
        ) : match.actualStartTime ? (
            <Typography
                sx={{fontSize: scaled('0.75rem', '1.3vw', '1.6rem')}}
                color="text.secondary">
                {t('event.info.athleteBoard.startedAt', {
                    time: formatClockTime(match.actualStartTime),
                })}
            </Typography>
        ) : null

    const renderTiming = () => {
        if (!match.startTime) {
            return (
                <Stack alignItems="flex-end">
                    <Typography
                        sx={{fontSize: scaled('0.8rem', '1.4vw', '1.6rem')}}
                        color="text.secondary">
                        {t('event.info.athleteBoard.unscheduled')}
                    </Typography>
                    {variant === 'running' && renderRunningStart()}
                </Stack>
            )
        }
        // Ein Start an einem anderen Kalendertag bekommt sein Datum dazu: "16:30" allein
        // liest sich sonst wie heute, auch wenn der Lauf erst nächste Woche stattfindet.
        const startsOnAnotherDay = !isSameDay(new Date(match.startTime), now)

        // Jenseits eines Tages ersetzt das Datum die Restzeit (siehe COUNTDOWN_MAX_SECONDS).
        const countdownFitsOnScreen =
            startsInSeconds !== null && startsInSeconds <= COUNTDOWN_MAX_SECONDS

        return (
            <Stack alignItems="flex-end">
                {startsOnAnotherDay && (
                    <Typography
                        sx={{fontSize: scaled('0.7rem', '1.2vw', '1.6rem')}}
                        color="text.secondary">
                        {formatShortDate(match.startTime)}
                    </Typography>
                )}
                <Typography
                    sx={{
                        fontSize: scaled('1.1rem', '2.4vw', '3.2rem'),
                        fontWeight: 700,
                        lineHeight: 1.1,
                    }}>
                    {formatClockTime(match.startTime)}
                </Typography>
                {variant === 'running' ? (
                    renderRunningStart()
                ) : overdue ? (
                    <Typography
                        sx={{fontSize: scaled('0.75rem', '1.3vw', '1.6rem')}}
                        color="text.secondary">
                        {t('event.info.athleteBoard.expected')}
                    </Typography>
                ) : (
                    variant === 'upcoming' &&
                    showCountdown &&
                    startsInSeconds !== null &&
                    countdownFitsOnScreen && (
                        <Typography
                            sx={{fontSize: scaled('0.75rem', '1.3vw', '1.6rem')}}
                            color="text.secondary">
                            {t('event.info.athleteBoard.startsIn', {
                                time: formatRemaining(startsInSeconds, t),
                            })}
                        </Typography>
                    )
                )}
            </Stack>
        )
    }

    // Abgesagter Lauf: Er bleibt an seiner geplanten Stelle stehen, statt spurlos zu verschwinden —
    // für eine Besatzung am Steg ist ein verschwundener Lauf nicht von einem Anzeigefehler zu
    // unterscheiden. Gezeigt wird nur noch, worum es ging und wann es hätte sein sollen; Mannschaften
    // zeigt diese Karte nicht — der Server liefert sie für einen abgesagten Lauf gar nicht erst mit.
    if (match.cancelled) {
        return (
            <>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="flex-start"
                    gap={1}
                    sx={{opacity: 0.6}}>
                    <Box sx={{minWidth: 0}}>
                        <Typography
                            sx={{
                                fontSize: scaled('1rem', '1.8vw', '2.6rem'),
                                fontWeight: 700,
                                textDecoration: 'line-through',
                            }}
                            color="text.secondary">
                            {[match.competitionName, match.roundName, match.matchName]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                        <Typography
                            sx={{fontSize: scaled('0.95rem', '1.6vw', '2.6rem'), fontWeight: 600}}
                            color="text.secondary">
                            {t('event.match.status.doesNotTakePlace')}
                        </Typography>
                    </Box>
                    {match.startTime && (
                        <Typography
                            sx={{
                                fontSize: scaled('1.1rem', '2.4vw', '3.2rem'),
                                fontWeight: 700,
                                lineHeight: 1.1,
                                textDecoration: 'line-through',
                            }}
                            color="text.secondary">
                            {formatClockTime(match.startTime)}
                        </Typography>
                    )}
                </Stack>
                {/* Kein Inhalt, sondern das zweite Kind: AthleteBoardColumnCard erwartet genau
                    zwei Elemente für ihr Kopf/Liste-Raster (siehe deren KDoc). */}
                <Box />
            </>
        )
    }

    // Nur im laufenden Lauf steht rechts eine Zeit. Im Block "Nächster Lauf" liefert der Server
    // Platz, Zeit und Strafe ohnehin nie — die Spalte entfällt dort strukturell, statt leer
    // mitzulaufen und Breite zu verbrauchen.
    const showLiveResult = variant === 'running' && showTimes

    // Nur wenn die Zwischenstände auch zu sehen sind, wird nach ihnen sortiert — eine
    // umsortierte Liste ohne sichtbare Zeiten wäre vom Steg aus nicht zu deuten.
    const teams = showLiveResult ? sortRunningTeams(match.teams) : match.teams

    // Zieleinlauf komplett, aber der Schiedsrichter hat den Lauf noch nicht beendet: die Karte
    // sagt das ausdrücklich, denn in „Letztes Ergebnis" taucht der Lauf bewusst erst mit dem
    // Beenden auf (BoardService, confirmedOnly) — ohne den Hinweis sähe der volle Zieleinlauf
    // hier wie ein hängengebliebenes Rennen aus. Client-seitig abgeleitet ([finishComplete]),
    // dieselbe Auslegung wie die Zustandsableitung des Backends für laufende Läufe.
    const awaitingReferee =
        variant === 'running' && !match.name && !match.pendingRound && finishComplete(match.teams)

    return (
        <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                <Box sx={{minWidth: 0}}>
                    {/* Programmpunkt (FREE-Slot, z.B. Mittagspause): schlanke, neutrale
                        Darstellung ohne Wettkampf-/Team-Bezug und ohne Interaktion. */}
                    {match.name ? (
                        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                            <Chip
                                label={t('event.info.freeSlot')}
                                size="small"
                                variant="outlined"
                            />
                            <Typography
                                sx={{fontSize: scaled('1rem', '1.8vw', '2.6rem'), fontWeight: 700}}
                                color="text.secondary">
                                {match.name}
                            </Typography>
                        </Stack>
                    ) : (
                        <>
                            <Typography
                                sx={{fontSize: scaled('1rem', '1.8vw', '2.6rem'), fontWeight: 700}}>
                                {match.competitionName}
                            </Typography>
                            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                                {match.roundName && (
                                    <Typography
                                        sx={{fontSize: scaled('0.75rem', '1.2vw', '1.6rem')}}
                                        color="text.secondary">
                                        {match.roundName}
                                    </Typography>
                                )}
                                {match.matchName && match.matchName !== match.roundName && (
                                    <Chip label={match.matchName} size="small" variant="outlined" />
                                )}
                                {match.categoryName && (
                                    <Chip
                                        label={match.categoryName}
                                        size="small"
                                        color="primary"
                                        variant="outlined"
                                    />
                                )}
                            </Stack>
                            {/* Deutlich sichtbares Band statt eines weiteren grauen Chips: die
                                Zeiten/Plätze stehen normal darunter, nur ihr Status („noch nicht
                                bestätigt") muss auffallen. Die Schrift läuft über scaled() mit
                                der Dichte-Skalierung mit und darf umbrechen — so sprengt das Band
                                auch kleine Kacheln nicht. */}
                            {awaitingReferee && (
                                <Box
                                    sx={{
                                        display: 'inline-block',
                                        mt: scaled('0.15rem', '0.25vw', '0.4rem'),
                                        px: scaled('0.35rem', '0.6vw', '0.9rem'),
                                        py: scaled('0.1rem', '0.15vw', '0.25rem'),
                                        borderRadius: 1,
                                        bgcolor: 'warning.main',
                                        color: 'warning.contrastText',
                                    }}>
                                    <Typography
                                        sx={{
                                            fontSize: scaled('0.7rem', '1.2vw', '1.6rem'),
                                            fontWeight: 700,
                                            lineHeight: 1.25,
                                        }}>
                                        {t('event.info.athleteBoard.awaitingReferee')}
                                    </Typography>
                                </Box>
                            )}
                            {/* Sprecherinnen-Zeile: worum geht es in diesem Lauf. Ohne
                                Platzzahl (Massenfeld-Folgerunde) nur die Runde. */}
                            {showAdvancement && match.nextRoundName && (
                                <Typography
                                    sx={{
                                        fontSize: scaled('0.75rem', '1.2vw', '1.6rem'),
                                        fontWeight: 600,
                                    }}
                                    color="primary">
                                    {match.advancingSeats != null
                                        ? t('event.info.athleteBoard.advancing', {
                                              count: match.advancingSeats,
                                              round: match.nextRoundName,
                                          })
                                        : t('event.info.athleteBoard.advancingUnsized', {
                                              round: match.nextRoundName,
                                          })}
                                </Typography>
                            )}
                            {/* Kleine Zweitzeile nur beim „muss gefahren werden"-Freilos:
                                Label („Freilos 1 …") plus die volle Begründung. Gewöhnliche
                                Freilose erscheinen in diesen Blöcken ohnehin nicht als
                                gefahrene Läufe und behalten ihre bisherige Darstellung. */}
                            {bye?.mustRace && (
                                <Typography
                                    sx={{fontSize: scaled('0.7rem', '1.2vw', '1.6rem')}}
                                    color="text.secondary">
                                    {`${translate(bye.key, bye.values)} – ${t('event.match.bye.mustRaceExplanation')}`}
                                </Typography>
                            )}
                        </>
                    )}
                </Box>
                {renderTiming()}
            </Stack>

            {match.name ? (
                // Kein Inhalt, sondern das zweite Kind: AthleteBoardColumnCard erwartet genau
                // zwei Elemente für ihr Kopf/Liste-Raster (siehe deren KDoc).
                <Box />
            ) : match.pendingRound ? (
                <Typography
                    sx={{fontSize: scaled('0.95rem', '1.6vw', '2.6rem')}}
                    color="text.secondary"
                    fontStyle="italic">
                    {t('event.info.pendingRound')}
                </Typography>
            ) : (
                <AthleteBoardBoatList rows={teams.map(() => 'boat')}>
                    {teams.map((team, index) => (
                        <AthleteBoardBoatRow
                            // Die Startnummer ist je Lauf eindeutig (Index
                            // `starting_position_unique_in_match`) und nie leer, trägt den
                            // Schlüssel also allein.
                            key={`${match.matchId}-${team.startNumber}`}
                            index={index}
                            leadNumber={team.startNumber}
                            trailing={
                                // Die Abmeldung steht VOR jedem Ergebnis und unabhängig von
                                // showLiveResult: sie ist kein Ergebnis, sondern die Feststellung,
                                // dass dieses Boot nicht fährt — und die gehört auch in einen
                                // anstehenden Lauf, in dem noch gar nichts gewertet sein kann.
                                team.deregistered ? (
                                    <AthleteBoardBoatStatus
                                        muted
                                        label={
                                            team.deregisteredReason
                                                ? `${t('event.info.athleteBoard.deregistered')} — ${team.deregisteredReason}`
                                                : t('event.info.athleteBoard.deregistered')
                                        }
                                    />
                                ) : // Teilergebnis: sobald die Zeitnahme dieses Boot gewertet hat,
                                // steht die Zeit hier — der Lauf läuft dabei weiter, bis die
                                // Organisation ihn beendet, und eine später ergänzte Zeitstrafe
                                // ändert die Zeile beim nächsten Abruf noch. Rundenzeiten allein
                                // (Boot zwischen zwei Marken, noch ohne Endzeit) öffnen die
                                // Spalte ebenfalls — sie gehören an die Zeit, nicht zur Crew.
                                showLiveResult &&
                                (team.failed ||
                                    team.timeString ||
                                    (team.laps ?? []).length > 0) ? (
                                    <>
                                        {(team.failed || team.timeString) && (
                                            <AthleteBoardBoatStatus
                                                muted={team.failed}
                                                label={
                                                    team.failed
                                                        ? (team.failedReason ??
                                                          t('event.info.athleteBoard.failed'))
                                                        // Als Ordnungszahl (Suffix hochgestellt),
                                                        // damit der Zwischenstand nicht wie eine
                                                        // zweite Startnummer liest.
                                                        : (
                                                              <>
                                                                  {team.place != null && (
                                                                      <>
                                                                          <PlaceOrdinal
                                                                              place={team.place}
                                                                          />{' '}
                                                                      </>
                                                                  )}
                                                                  {team.timeString}
                                                              </>
                                                          )
                                                }
                                            />
                                        )}
                                        <AthleteBoardPenaltyNote
                                            penaltySeconds={team.penaltySeconds}
                                            penaltyNote={team.penaltyNote}
                                        />
                                        {/* Rundenzeiten prominent unter der Zwischen-/Endzeit
                                            (12.08.2026) — vorher eine Crew-Subline links. */}
                                        <AthleteBoardLapTimes laps={team.laps} />
                                    </>
                                ) : undefined
                            }>
                            <AthleteBoardTeamLabel
                                team={team}
                                strikeThrough={team.deregistered}
                                color={team.deregistered ? 'text.secondary' : undefined}
                            />
                            {/* Sprecherinnen-Detail: jede Athletin auf eigener Zeile —
                                Boot für Boot vorstellbar, alle Namen lesbar. */}
                            {showCrewDetails && team.participants.length > 0 ? (
                                <>
                                    {team.participants.map((p, i) => (
                                        <AthleteBoardBoatSubline key={i}>
                                            {[
                                                p.role ? `${p.name} (${p.role})` : p.name,
                                                p.clubName,
                                                showBirthYears && p.year != null
                                                    ? t('event.info.athleteBoard.birthYear', {
                                                          year: p.year,
                                                      })
                                                    : null,
                                            ]
                                                .filter(Boolean)
                                                .join(' · ')}
                                        </AthleteBoardBoatSubline>
                                    ))}
                                </>
                            ) : (
                                showCrew &&
                                team.participants.length > 0 && (
                                    <AthleteBoardBoatSubline>
                                        {team.participants
                                            .map(p => (p.role ? `${p.name} (${p.role})` : p.name))
                                            .join(', ')}
                                    </AthleteBoardBoatSubline>
                                )
                            )}
                            {showRegisteringClub && team.registeringClub && (
                                <AthleteBoardBoatSubline>
                                    {t('event.info.athleteBoard.registeringClub', {
                                        club: team.registeringClub,
                                    })}
                                </AthleteBoardBoatSubline>
                            )}
                        </AthleteBoardBoatRow>
                    ))}
                </AthleteBoardBoatList>
            )}
        </>
    )
}

export default AthleteBoardMatchCard

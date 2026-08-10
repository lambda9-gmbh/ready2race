import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {
    AthleteBoardBoatList,
    AthleteBoardBoatRow,
    AthleteBoardBoatStatus,
    AthleteBoardBoatSubline,
} from './AthleteBoardBoatRow'
import {
    COUNTDOWN_MAX_SECONDS,
    formatClockTime,
    formatPlace,
    formatRemaining,
    formatShortDate,
    isSameDay,
    scaled,
} from './common'

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
}

const AthleteBoardMatchCard = ({
    match,
    now,
    variant,
    showCountdown = true,
    showCrew = true,
    showTimes = true,
}: AthleteBoardMatchCardProps) => {
    const {t} = useTranslation()

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

    return (
        <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                <Box sx={{minWidth: 0}}>
                    {/* Programmpunkt (FREE-Slot, z.B. Mittagspause): schlanke, neutrale
                        Darstellung ohne Wettkampf-/Team-Bezug und ohne Interaktion. */}
                    {match.name ? (
                        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                            <Chip label={t('event.info.freeSlot')} size="small" variant="outlined" />
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
                <AthleteBoardBoatList rows={match.teams.map(() => 'boat')}>
                    {match.teams.map((team, index) => (
                        <AthleteBoardBoatRow
                            // Die Startnummer ist je Lauf eindeutig (Index
                            // `starting_position_unique_in_match`) und nie leer, trägt den
                            // Schlüssel also allein.
                            key={`${match.matchId}-${team.startNumber}`}
                            index={index}
                            leadNumber={team.startNumber}
                            trailing={
                                // Teilergebnis: sobald die Zeitnahme dieses Boot gewertet hat,
                                // steht die Zeit hier — der Lauf läuft dabei weiter, bis die
                                // Organisation ihn beendet, und eine später ergänzte Zeitstrafe
                                // ändert die Zeile beim nächsten Abruf noch.
                                showLiveResult && (team.failed || team.timeString) ? (
                                    <>
                                        <AthleteBoardBoatStatus
                                            muted={team.failed}
                                            label={
                                                team.failed
                                                    ? (team.failedReason ??
                                                      t('event.info.athleteBoard.failed'))
                                                    // Als Ordnungszahl, damit der Zwischenstand
                                                    // nicht wie eine zweite Startnummer liest.
                                                    : `${team.place != null ? `${formatPlace(team.place, t)} ` : ''}${team.timeString}`
                                            }
                                        />
                                        <AthleteBoardPenaltyNote
                                            penaltySeconds={team.penaltySeconds}
                                            penaltyNote={team.penaltyNote}
                                        />
                                    </>
                                ) : undefined
                            }>
                            <AthleteBoardTeamLabel team={team} />
                            {showCrew && team.participants.length > 0 && (
                                <AthleteBoardBoatSubline>
                                    {team.participants
                                        .map(p => (p.role ? `${p.name} (${p.role})` : p.name))
                                        .join(', ')}
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

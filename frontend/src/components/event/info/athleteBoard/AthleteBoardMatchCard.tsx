import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import {formatClockTime, formatRemaining, teamLabel} from './common'

/**
 * "running": Karte im Block "Aktueller Lauf" — das Boot ist bereits auf dem Wasser,
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
    // auch die genaue Countdown-Zahl gezeigt wird (Einstellung der Veranstaltung).
    showCountdown?: boolean
}

const AthleteBoardMatchCard =({match, now, variant, showCountdown = true}: AthleteBoardMatchCardProps) => {
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

    // Im Block "Aktueller Lauf" trägt der tatsächliche Start die Aussage: ein Lauf ohne
    // Zeitstempel ist als aktuell gesetzt, liegt aber noch am Steg. Im Block "Nächster Lauf"
    // ist actualStartTime immer leer (siehe KDoc von AthleteBoardMatch im Backend).
    const renderRunningStart = () =>
        // Ein Programmpunkt (FREE-Platzhalter) startet nicht und wird nicht gestempelt.
        match.name ? null : match.actualStartTime ? (
            <Typography sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}} color="text.secondary">
                {t('event.info.athleteBoard.startedAt', {
                    time: formatClockTime(match.actualStartTime),
                })}
            </Typography>
        ) : (
            <Typography sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}} color="text.secondary">
                {t('event.info.athleteBoard.preparing')}
            </Typography>
        )

    const renderTiming = () => {
        if (!match.startTime) {
            return (
                <Stack alignItems="flex-end">
                    <Typography
                        sx={{fontSize: 'clamp(0.8rem, 1.4vw, 1.1rem)'}}
                        color="text.secondary">
                        {t('event.info.athleteBoard.unscheduled')}
                    </Typography>
                    {variant === 'running' && renderRunningStart()}
                </Stack>
            )
        }
        return (
            <Stack alignItems="flex-end">
                <Typography
                    sx={{fontSize: 'clamp(1.1rem, 2.4vw, 2rem)', fontWeight: 700, lineHeight: 1.1}}>
                    {formatClockTime(match.startTime)}
                </Typography>
                {variant === 'running' ? (
                    renderRunningStart()
                ) : overdue ? (
                    <Typography
                        sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
                        color="text.secondary">
                        {t('event.info.athleteBoard.expected')}
                    </Typography>
                ) : (
                    variant === 'upcoming' &&
                    showCountdown &&
                    startsInSeconds !== null && (
                        <Typography
                            sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
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

    return (
        <Card variant="outlined" sx={{mb: 1.5}}>
            <CardContent sx={{p: 'clamp(0.75rem, 1.2vw, 1.5rem)'}}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                    <Box sx={{minWidth: 0}}>
                        {/* Programmpunkt (FREE-Slot, z.B. Mittagspause): schlanke, neutrale
                            Darstellung ohne Wettkampf-/Team-Bezug und ohne Interaktion. */}
                        {match.name ? (
                            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                                <Chip label={t('event.info.freeSlot')} size="small" variant="outlined" />
                                <Typography
                                    sx={{fontSize: 'clamp(1rem, 1.8vw, 1.6rem)', fontWeight: 700}}
                                    color="text.secondary">
                                    {match.name}
                                </Typography>
                            </Stack>
                        ) : (
                            <>
                                <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 1.6rem)', fontWeight: 700}}>
                                    {match.competitionName}
                                </Typography>
                                <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                                    {match.roundName && (
                                        <Typography
                                            sx={{fontSize: 'clamp(0.75rem, 1.2vw, 1rem)'}}
                                            color="text.secondary">
                                            {match.roundName}
                                        </Typography>
                                    )}
                                    {match.matchName && match.matchName !== match.roundName && (
                                        <Chip label={match.matchName} size="small" variant="outlined" />
                                    )}
                                    {match.categoryName && (
                                        <Chip label={match.categoryName} size="small" color="primary" variant="outlined" />
                                    )}
                                </Stack>
                            </>
                        )}
                    </Box>
                    {renderTiming()}
                </Stack>

                {match.name ? null : match.pendingRound ? (
                    <Typography
                        sx={{fontSize: 'clamp(0.95rem, 1.6vw, 1.4rem)', mt: 1.5}}
                        color="text.secondary"
                        fontStyle="italic">
                        {t('event.info.pendingRound')}
                    </Typography>
                ) : (
                    <Stack sx={{mt: 1.5}} divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
                        {match.teams.map((team, index) => (
                            <Stack
                                key={`${match.matchId}-${team.lane ?? index}`}
                                direction="row"
                                alignItems="center"
                                gap={1.5}
                                sx={{py: 0.75}}>
                                <Typography
                                    sx={{
                                        fontSize: 'clamp(1.6rem, 3.4vw, 3rem)',
                                        fontWeight: 800,
                                        lineHeight: 1,
                                        minWidth: '1.8em',
                                        textAlign: 'center',
                                    }}>
                                    {team.lane ?? '–'}
                                </Typography>
                                <Box sx={{flex: 1, minWidth: 0}}>
                                    <Typography
                                        sx={{
                                            fontSize: 'clamp(0.95rem, 1.6vw, 1.4rem)',
                                            fontWeight: 600,
                                        }}>
                                        {teamLabel(team, t)}
                                    </Typography>
                                    {team.participants.length > 0 && (
                                        <Typography
                                            sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                            color="text.secondary">
                                            {team.participants
                                                .map(p =>
                                                    p.role ? `${p.name} (${p.role})` : p.name,
                                                )
                                                .join(', ')}
                                        </Typography>
                                    )}
                                </Box>
                                {/* Teilergebnis: sobald die Zeitnahme dieses Boot gewertet hat,
                                    steht die Zeit hier — der Lauf läuft dabei weiter, bis die
                                    Organisation ihn beendet, und eine später ergänzte Zeitstrafe
                                    ändert die Zeile beim nächsten Abruf noch. */}
                                {(team.failed || team.timeString) && (
                                    <Stack
                                        alignItems="flex-end"
                                        sx={{flexShrink: 0, maxWidth: '45%'}}>
                                        <Typography
                                            sx={{
                                                fontSize: 'clamp(0.9rem, 1.5vw, 1.3rem)',
                                                fontWeight: 600,
                                                textAlign: 'right',
                                            }}
                                            color={team.failed ? 'text.secondary' : 'text.primary'}>
                                            {team.failed
                                                ? (team.failedReason ??
                                                  t('event.info.athleteBoard.failed'))
                                                : `${team.place != null ? `${team.place}. ` : ''}${team.timeString}`}
                                        </Typography>
                                        <AthleteBoardPenaltyNote
                                            penaltySeconds={team.penaltySeconds}
                                            penaltyNote={team.penaltyNote}
                                        />
                                    </Stack>
                                )}
                            </Stack>
                        ))}
                    </Stack>
                )}
            </CardContent>
        </Card>
    )
}

export default AthleteBoardMatchCard

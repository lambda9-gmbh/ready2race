import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {formatClockTime, scaled} from './common'

interface AthleteBoardResultCardProps {
    result: AthleteBoardResult
}

const AthleteBoardResultCard = ({result}: AthleteBoardResultCardProps) => {
    const {t} = useTranslation()

    const teams = [...result.teams].sort((a, b) => {
        // Abgemeldete Mannschaften ganz ans Ende: sie sind nicht gefahren und stehen nur noch
        // als Erklärung in der Liste.
        if (a.deregistered !== b.deregistered) return a.deregistered ? 1 : -1
        // Platzierte zuerst, danach die ohne Platz (DNF und Konsorten).
        if (a.place == null && b.place == null) return a.startNumber - b.startNumber
        if (a.place == null) return 1
        if (b.place == null) return -1
        return a.place - b.place
    })

    const boats = teams.length

    return (
        <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                <Box sx={{minWidth: 0}}>
                    <Typography sx={{fontSize: scaled('1rem', '1.8vw', '2.6rem'), fontWeight: 700}}>
                        {result.competitionName}
                    </Typography>
                    <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                        {result.roundName && (
                            <Typography
                                sx={{fontSize: scaled('0.75rem', '1.2vw', '1.6rem')}}
                                color="text.secondary">
                                {result.roundName}
                            </Typography>
                        )}
                        {result.matchName && result.matchName !== result.roundName && (
                            <Chip label={result.matchName} size="small" variant="outlined" />
                        )}
                    </Stack>
                </Box>
                {/* Geplanter Start groß, darunter der tatsächliche — so ist eine Verschiebung
                    im Ergebnis noch nachvollziehbar, ohne den Zeitplan zu verstecken. */}
                {result.startTime && (
                    <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                        <Typography
                            sx={{
                                fontSize: scaled('1.1rem', '2.4vw', '3.2rem'),
                                fontWeight: 700,
                                lineHeight: 1.1,
                            }}>
                            {formatClockTime(result.startTime)}
                        </Typography>
                        {result.actualStartTime && (
                            <Typography
                                sx={{fontSize: scaled('0.75rem', '1.3vw', '1.6rem')}}
                                color="text.secondary">
                                {t('event.info.athleteBoard.startedAt', {
                                    time: formatClockTime(result.actualStartTime),
                                })}
                            </Typography>
                        )}
                    </Stack>
                )}
            </Stack>

            {/* Ab lg teilen sich die Bootszeilen die verbleibende Höhe zu gleichen Teilen, darunter
                bleibt die gestapelte Darstellung mit natürlicher Zeilenhöhe (siehe AthleteBoardMatchCard). */}
            <Box
                sx={{
                    minHeight: 0,
                    display: 'grid',
                    gridTemplateRows: {
                        // Math.max gegen ein leeres Feld: `repeat(0, …)` ist ungültiges CSS
                        // und ließe die ganze Deklaration ins Leere laufen.
                        xs: `repeat(${Math.max(boats, 1)}, auto)`,
                        lg: `repeat(${Math.max(boats, 1)}, minmax(0, 1fr))`,
                    },
                }}>
                {teams.map((team, index) => (
                    <Stack
                        key={`${result.matchId}-${team.startNumber}-${index}`}
                        direction="row"
                        alignItems="center"
                        gap={1.5}
                        sx={{
                            minWidth: 0,
                            minHeight: 0,
                            overflow: {xs: 'visible', lg: 'hidden'},
                            borderTop: index > 0 ? '1px solid' : 'none',
                            borderColor: 'divider',
                        }}>
                        {/* Die große Zahl ist der Platz, nicht die Startnummer: an dieser Stelle
                            erwartet eine Besatzung das Ergebnis. Die Startnummer steht klein
                            darunter, damit die Zeile dem Boot zuzuordnen bleibt. */}
                        <Typography
                            sx={{
                                fontSize: scaled('1.4rem', '2.8vw', '4.5rem'),
                                fontWeight: 800,
                                lineHeight: 1,
                                minWidth: '1.4em',
                                textAlign: 'center',
                                flexShrink: 0,
                            }}>
                            {team.place ?? '–'}
                        </Typography>
                        <Box sx={{flex: 1, minWidth: 0}}>
                            <AthleteBoardTeamLabel
                                team={team}
                                color={team.deregistered ? 'text.secondary' : 'text.primary'}
                            />
                            <Typography
                                noWrap
                                sx={{fontSize: scaled('0.7rem', '1.1vw', '1.5rem')}}
                                color="text.secondary">
                                {t('event.info.athleteBoard.startNumber')} {team.startNumber}
                            </Typography>
                        </Box>
                        {/* Ein langer DNF-Grund darf den Vereinsnamen nicht überlagern:
                            rechts bündig in der eigenen Hälfte umbrechen. */}
                        <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '35%'}}>
                            <Typography
                                sx={{
                                    fontSize: scaled('0.9rem', '1.5vw', '2.2rem'),
                                    fontWeight: 600,
                                    textAlign: 'right',
                                }}
                                color={
                                    team.failed || team.deregistered
                                        ? 'text.secondary'
                                        : 'text.primary'
                                }>
                                {team.deregistered
                                    ? [
                                          t('event.info.athleteBoard.deregistered'),
                                          team.deregisteredReason,
                                      ]
                                          .filter(Boolean)
                                          .join(' · ')
                                    : team.failed
                                      ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                                      : (team.timeString ?? '')}
                            </Typography>
                            {!team.deregistered && (
                                <AthleteBoardPenaltyNote
                                    penaltySeconds={team.penaltySeconds}
                                    penaltyNote={team.penaltyNote}
                                />
                            )}
                        </Stack>
                    </Stack>
                ))}
            </Box>
        </>
    )
}

export default AthleteBoardResultCard

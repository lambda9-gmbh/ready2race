import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {formatClockTime} from './common'

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
        if (a.place == null && b.place == null) return a.lane - b.lane
        if (a.place == null) return 1
        if (b.place == null) return -1
        return a.place - b.place
    })

    return (
        <Card variant="outlined" sx={{mb: 1.5}}>
            <CardContent sx={{p: 'clamp(0.75rem, 1.2vw, 1.5rem)'}}>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="flex-start"
                    gap={1}>
                    <Box sx={{minWidth: 0}}>
                        <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 1.6rem)', fontWeight: 700}}>
                            {result.competitionName}
                        </Typography>
                        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                            {result.roundName && (
                                <Typography
                                    sx={{fontSize: 'clamp(0.75rem, 1.2vw, 1rem)'}}
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
                                    fontSize: 'clamp(1.1rem, 2.4vw, 2rem)',
                                    fontWeight: 700,
                                    lineHeight: 1.1,
                                }}>
                                {formatClockTime(result.startTime)}
                            </Typography>
                            {result.actualStartTime && (
                                <Typography
                                    sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
                                    color="text.secondary">
                                    {t('event.info.athleteBoard.startedAt', {
                                        time: formatClockTime(result.actualStartTime),
                                    })}
                                </Typography>
                            )}
                        </Stack>
                    )}
                </Stack>

                <Stack sx={{mt: 1.5}} divider={<Box sx={{height: '1px', bgcolor: 'divider'}} />}>
                    {teams.map((team, index) => (
                        <Stack
                            key={`${result.matchId}-${team.lane}-${index}`}
                            direction="row"
                            alignItems="center"
                            gap={1.5}
                            sx={{py: 0.75}}>
                            <Typography
                                sx={{
                                    fontSize: 'clamp(1.4rem, 2.8vw, 2.4rem)',
                                    fontWeight: 800,
                                    lineHeight: 1,
                                    minWidth: '1.8em',
                                    textAlign: 'center',
                                }}>
                                {team.place ?? '–'}
                            </Typography>
                            <Box sx={{flex: 1, minWidth: 0}}>
                                <AthleteBoardTeamLabel
                                    team={team}
                                    color={team.deregistered ? 'text.secondary' : 'text.primary'}
                                />
                                <Typography
                                    sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                    color="text.secondary">
                                    {t('event.info.athleteBoard.lane')} {team.lane}
                                </Typography>
                            </Box>
                            {/* Ein langer DNF-Grund darf den Vereinsnamen nicht überlagern:
                                rechts bündig in der eigenen Hälfte umbrechen. */}
                            <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '45%'}}>
                                <Typography
                                    sx={{
                                        fontSize: 'clamp(0.9rem, 1.5vw, 1.3rem)',
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
                                          ? (team.failedReason ??
                                            t('event.info.athleteBoard.failed'))
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
                </Stack>
            </CardContent>
        </Card>
    )
}

export default AthleteBoardResultCard

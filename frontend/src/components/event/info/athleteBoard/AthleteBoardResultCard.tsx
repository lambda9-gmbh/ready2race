import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {
    AthleteBoardBoatList,
    AthleteBoardBoatRow,
    AthleteBoardBoatStatus,
    AthleteBoardBoatSubline,
} from './AthleteBoardBoatRow'
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

            <AthleteBoardBoatList boats={boats}>
                {teams.map((team, index) => (
                    <AthleteBoardBoatRow
                        key={`${result.matchId}-${team.startNumber}`}
                        index={index}
                        // Die große Zahl ist der Platz, nicht die Startnummer: an dieser Stelle
                        // erwartet eine Besatzung das Ergebnis. Die Startnummer steht klein
                        // darunter, damit die Zeile dem Boot zuzuordnen bleibt.
                        leadNumber={team.place ?? '–'}
                        trailing={
                            <>
                                <AthleteBoardBoatStatus
                                    muted={team.failed || team.deregistered}
                                    label={
                                        team.deregistered
                                            ? [
                                                  t('event.info.athleteBoard.deregistered'),
                                                  team.deregisteredReason,
                                              ]
                                                  .filter(Boolean)
                                                  .join(' · ')
                                            : team.failed
                                              ? (team.failedReason ??
                                                t('event.info.athleteBoard.failed'))
                                              : (team.timeString ?? '')
                                    }
                                />
                                {!team.deregistered && (
                                    <AthleteBoardPenaltyNote
                                        penaltySeconds={team.penaltySeconds}
                                        penaltyNote={team.penaltyNote}
                                    />
                                )}
                            </>
                        }>
                        <AthleteBoardTeamLabel
                            team={team}
                            color={team.deregistered ? 'text.secondary' : 'text.primary'}
                        />
                        <AthleteBoardBoatSubline>
                            {t('event.info.athleteBoard.startNumber')} {team.startNumber}
                        </AthleteBoardBoatSubline>
                    </AthleteBoardBoatRow>
                ))}
            </AthleteBoardBoatList>
        </>
    )
}

export default AthleteBoardResultCard

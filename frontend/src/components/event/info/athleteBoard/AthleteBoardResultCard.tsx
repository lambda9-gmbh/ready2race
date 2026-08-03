import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'

interface AthleteBoardResultCardProps {
    result: AthleteBoardResult
}

const AthleteBoardResultCard = ({result}: AthleteBoardResultCardProps) => {
    const {t} = useTranslation()

    const teams = [...result.teams].sort((a, b) => {
        // Platzierte zuerst, danach die ohne Platz (DNF und Konsorten).
        if (a.place == null && b.place == null) return a.lane - b.lane
        if (a.place == null) return 1
        if (b.place == null) return -1
        return a.place - b.place
    })

    return (
        <Card variant="outlined" sx={{mb: 1.5}}>
            <CardContent sx={{p: 'clamp(0.75rem, 1.2vw, 1.5rem)'}}>
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
                                <Typography
                                    sx={{fontSize: 'clamp(0.95rem, 1.6vw, 1.4rem)', fontWeight: 600}}>
                                    {team.clubName ?? ''}
                                    {team.teamName ? ` | ${team.teamName}` : ''}
                                </Typography>
                                <Typography
                                    sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                    color="text.secondary">
                                    {t('event.info.athleteBoard.lane')} {team.lane}
                                </Typography>
                            </Box>
                            <Typography
                                sx={{fontSize: 'clamp(0.9rem, 1.5vw, 1.3rem)', fontWeight: 600}}
                                color={team.failed ? 'text.secondary' : 'text.primary'}>
                                {team.failed
                                    ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                                    : (team.timeString ?? '')}
                            </Typography>
                        </Stack>
                    ))}
                </Stack>
            </CardContent>
        </Card>
    )
}

export default AthleteBoardResultCard

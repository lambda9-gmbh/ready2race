import {Box, Card, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardMatch} from '@api/types.gen'

interface AthleteBoardMatchCardProps {
    match: AthleteBoardMatch
    now: Date
    showCountdown: boolean
}

const formatTime = (value: string) =>
    new Date(value).toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})

const formatRemaining = (seconds: number) => {
    const total = Math.max(0, Math.floor(seconds))
    const minutes = Math.floor(total / 60)
    const rest = total % 60
    return minutes > 0 ? `${minutes} min` : `${rest} s`
}

const AthleteBoardMatchCard = ({match, now, showCountdown}: AthleteBoardMatchCardProps) => {
    const {t} = useTranslation()
    // Die Übersetzungsschlüssel unter event.info.athleteBoard.* werden erst mit der Seite
    // angelegt; bis dahin bräuchte t() hier eine noch nicht existierende Literal-Vereinigung.
    const translate = t as unknown as (key: string, options?: Record<string, unknown>) => string

    const startsInSeconds = match.startTime
        ? (new Date(match.startTime).getTime() - now.getTime()) / 1000
        : null

    // Der Server liefert den Zustand beim Abruf; zwischen zwei Abrufen läuft die Uhr
    // lokal weiter, deshalb wird der Übergang zu "erwartet" hier noch einmal geprüft.
    const overdue =
        match.startState === 'OVERDUE' || (startsInSeconds !== null && startsInSeconds <= 0)

    const renderTiming = () => {
        if (!match.startTime) {
            return (
                <Typography sx={{fontSize: 'clamp(0.8rem, 1.4vw, 1.1rem)'}} color="text.secondary">
                    {translate('event.info.athleteBoard.unscheduled')}
                </Typography>
            )
        }
        return (
            <Stack alignItems="flex-end">
                <Typography
                    sx={{fontSize: 'clamp(1.1rem, 2.4vw, 2rem)', fontWeight: 700, lineHeight: 1.1}}>
                    {formatTime(match.startTime)}
                </Typography>
                {overdue ? (
                    <Typography
                        sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
                        color="text.secondary">
                        {translate('event.info.athleteBoard.expected')}
                    </Typography>
                ) : (
                    showCountdown &&
                    startsInSeconds !== null && (
                        <Typography
                            sx={{fontSize: 'clamp(0.75rem, 1.3vw, 1rem)'}}
                            color="text.secondary">
                            {translate('event.info.athleteBoard.startsIn', {
                                time: formatRemaining(startsInSeconds),
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
                        <Typography
                            sx={{fontSize: 'clamp(1rem, 1.8vw, 1.6rem)', fontWeight: 700}}
                            noWrap>
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
                    </Box>
                    {renderTiming()}
                </Stack>

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
                            <Box sx={{minWidth: 0}}>
                                <Typography
                                    sx={{fontSize: 'clamp(0.95rem, 1.6vw, 1.4rem)', fontWeight: 600}}>
                                    {team.clubName ?? ''}
                                    {team.teamName ? ` | ${team.teamName}` : ''}
                                </Typography>
                                {team.participants.length > 0 && (
                                    <Typography
                                        sx={{fontSize: 'clamp(0.7rem, 1.1vw, 0.95rem)'}}
                                        color="text.secondary">
                                        {team.participants
                                            .map(p => (p.role ? `${p.name} (${p.role})` : p.name))
                                            .join(', ')}
                                    </Typography>
                                )}
                            </Box>
                        </Stack>
                    ))}
                </Stack>
            </CardContent>
        </Card>
    )
}

export default AthleteBoardMatchCard

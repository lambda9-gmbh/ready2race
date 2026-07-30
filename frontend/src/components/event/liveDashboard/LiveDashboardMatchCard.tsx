import {Box, Card, CardContent, Chip, List, ListItemButton, Stack, Typography} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto} from '@api/types.gen.ts'
import {formatMinutes, Severity, teamSeverity} from './common.ts'

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (matchId: string, teamId: string) => void
}

// One glanceable icon per team replaces the detail chips — everything else
// lives in the team dialog, one tap away.
const severityIcon = (severity: Severity) => {
    const sx = {fontSize: 20}
    switch (severity) {
        case 'ok':
            return <CheckCircleIcon color="success" sx={sx} />
        case 'warning':
            return <WarningAmberIcon color="warning" sx={sx} />
        case 'error':
            return <CancelIcon color="error" sx={sx} />
        case 'neutral':
            return <RadioButtonUncheckedIcon color="disabled" sx={sx} />
    }
}

const LiveDashboardMatchCard = ({match, onTeamClick}: Props) => {
    const {t} = useTranslation()

    const running = match.state === 'RUNNING'

    return (
        <Card
            variant="outlined"
            sx={{
                borderColor: running ? 'success.main' : undefined,
                borderWidth: running ? 2 : undefined,
                // Ohne minWidth wachsen Flex-Kinder mit ihrem Inhalt und sprengen die Breite
                minWidth: 0,
                overflow: 'hidden',
            }}>
            <CardContent sx={{p: 1.5, '&:last-child': {pb: 1.5}}}>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="baseline"
                    spacing={1}
                    sx={{minWidth: 0}}>
                    <Box sx={{minWidth: 0, overflow: 'hidden'}}>
                        <Typography variant="subtitle2" fontWeight={700} noWrap>
                            {match.matchName ?? match.roundName ?? match.competitionName}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" noWrap display="block">
                            {[match.competitionName, match.categoryName, match.roundName]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                    </Box>
                    <Stack alignItems="flex-end" flexShrink={0}>
                        <Typography
                            variant="subtitle2"
                            fontWeight={700}
                            color={running ? 'success.main' : 'text.primary'}>
                            {match.startTime
                                ? format(new Date(match.startTime), t('format.time'))
                                : '—'}
                        </Typography>
                        <Typography
                            variant="caption"
                            color={running ? 'success.main' : 'text.secondary'}>
                            {running && match.elapsedMinutes != null
                                ? t('event.liveDashboard.runningSince', {
                                      duration: formatMinutes(match.elapsedMinutes),
                                  })
                                : t(`event.liveDashboard.state.${match.state}`)}
                        </Typography>
                    </Stack>
                </Stack>
                <List dense disablePadding sx={{mt: 0.5}}>
                    {match.teams.map(team => {
                        const substituted = team.participants.some(p => p.substitutedFor)
                        const clubLine = team.actualClubName ?? team.clubName
                        // Teamnamen enthalten oft den Vereinsnamen — dann die zweite Zeile weglassen
                        const showClubLine =
                            team.teamName != null &&
                            clubLine != null &&
                            !team.teamName.includes(clubLine)
                        return (
                            <ListItemButton
                                key={team.teamId}
                                onClick={() => onTeamClick(match.matchId, team.teamId)}
                                sx={{px: 1, py: 0.5, borderRadius: 1}}>
                                <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="center"
                                    width="100%"
                                    sx={{minWidth: 0}}>
                                    <Typography
                                        variant="body2"
                                        color="text.secondary"
                                        fontWeight={600}
                                        sx={{minWidth: 20, textAlign: 'right'}}>
                                        {team.startNumber ?? '–'}
                                    </Typography>
                                    <Box sx={{minWidth: 0, overflow: 'hidden', flexGrow: 1}}>
                                        <Stack
                                            direction="row"
                                            spacing={0.5}
                                            alignItems="center"
                                            sx={{minWidth: 0}}>
                                            <Typography
                                                variant="body2"
                                                sx={{
                                                    display: '-webkit-box',
                                                    WebkitLineClamp: 2,
                                                    WebkitBoxOrient: 'vertical',
                                                    overflow: 'hidden',
                                                }}>
                                                {team.teamName ?? team.clubName ?? ''}
                                            </Typography>
                                            {substituted && (
                                                <SwapHorizIcon
                                                    color="info"
                                                    sx={{fontSize: 16, flexShrink: 0}}
                                                    titleAccess={t(
                                                        'event.liveDashboard.substitution.short',
                                                    )}
                                                />
                                            )}
                                        </Stack>
                                        {showClubLine && (
                                            <Typography
                                                variant="caption"
                                                color="text.secondary"
                                                noWrap
                                                display="block">
                                                {clubLine}
                                            </Typography>
                                        )}
                                    </Box>
                                    {/* Result first and biggest — that is what a referee scans for. */}
                                    {team.time && (
                                        <Typography
                                            variant="body1"
                                            fontWeight={700}
                                            fontFamily="ui-monospace, SFMono-Regular, Menlo, monospace"
                                            sx={{flexShrink: 0, letterSpacing: '-0.03em'}}>
                                            {team.time}
                                        </Typography>
                                    )}
                                    {team.place != null && (
                                        <Chip
                                            size="small"
                                            color="primary"
                                            label={`${team.place}.`}
                                            sx={{fontWeight: 700, flexShrink: 0}}
                                        />
                                    )}
                                    {team.failed && (
                                        <Chip
                                            size="small"
                                            color="warning"
                                            variant="outlined"
                                            label={t('event.liveDashboard.team.failed')}
                                            sx={{flexShrink: 0}}
                                        />
                                    )}
                                    <Box display="flex" flexShrink={0}>
                                        {severityIcon(teamSeverity(team))}
                                    </Box>
                                </Stack>
                            </ListItemButton>
                        )
                    })}
                </List>
            </CardContent>
        </Card>
    )
}

export default LiveDashboardMatchCard

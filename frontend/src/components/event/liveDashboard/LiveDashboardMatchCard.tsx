import {Box, Card, CardContent, Chip, List, ListItemButton, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {formatMinutes, severityChipColor, teamSeverity} from './common.ts'

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (team: LiveDashboardTeamDto) => void
}

const LiveDashboardMatchCard = ({match, onTeamClick}: Props) => {
    const {t} = useTranslation()

    return (
        <Card
            sx={{
                borderColor: match.state === 'RUNNING' ? 'success.main' : undefined,
                borderWidth: match.state === 'RUNNING' ? 2 : undefined,
                borderStyle: match.state === 'RUNNING' ? 'solid' : undefined,
            }}>
            <CardContent sx={{p: 2, '&:last-child': {pb: 2}}}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                    <Box>
                        <Typography variant="subtitle1" fontWeight={600}>
                            {match.competitionName}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                            {[match.categoryName, match.roundName, match.matchName]
                                .filter(Boolean)
                                .join(' · ')}
                        </Typography>
                    </Box>
                    <Stack alignItems="flex-end" spacing={0.5}>
                        <Chip
                            size="small"
                            label={t(`event.liveDashboard.state.${match.state}`)}
                            color={
                                match.state === 'RUNNING'
                                    ? 'success'
                                    : match.state === 'FINISHED'
                                      ? 'default'
                                      : match.state === 'UPCOMING'
                                        ? 'primary'
                                        : 'default'
                            }
                        />
                        {match.startTime ? (
                            <Typography variant="caption" color="text.secondary">
                                {format(new Date(match.startTime), t('format.datetime'))}
                            </Typography>
                        ) : (
                            <Typography variant="caption" color="text.secondary">
                                {t('event.liveDashboard.noStartTime')}
                            </Typography>
                        )}
                        {match.state === 'RUNNING' && match.elapsedMinutes != null && (
                            <Typography variant="caption" color="success.main">
                                {t('event.liveDashboard.runningSince', {
                                    duration: formatMinutes(match.elapsedMinutes),
                                })}
                            </Typography>
                        )}
                    </Stack>
                </Stack>
                <List dense disablePadding sx={{mt: 1}}>
                    {match.teams.map(team => {
                        const severity = teamSeverity(team)
                        const checkedCount = team.participants
                            .flatMap(p => p.requirements)
                            .filter(r => r.checked).length
                        const totalCount = team.participants.flatMap(p => p.requirements).length
                        return (
                            <ListItemButton
                                key={team.teamId}
                                onClick={() => onTeamClick(team)}
                                sx={{px: 1, borderRadius: 1}}>
                                <Stack
                                    direction="row"
                                    spacing={1}
                                    alignItems="center"
                                    width="100%"
                                    justifyContent="space-between">
                                    <Stack direction="row" spacing={1} alignItems="center" minWidth={0}>
                                        {team.startNumber != null && (
                                            <Chip size="small" label={`#${team.startNumber}`} />
                                        )}
                                        <Box minWidth={0}>
                                            <Typography variant="body2" noWrap>
                                                {team.teamName ?? team.clubName ?? ''}
                                            </Typography>
                                            {team.teamName && team.clubName && (
                                                <Typography
                                                    variant="caption"
                                                    color="text.secondary"
                                                    noWrap
                                                    display="block">
                                                    {team.clubName}
                                                </Typography>
                                            )}
                                        </Box>
                                    </Stack>
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                        {team.place != null && (
                                            <Chip
                                                size="small"
                                                color="primary"
                                                label={t('event.liveDashboard.team.place', {
                                                    place: team.place,
                                                })}
                                            />
                                        )}
                                        {team.time && <Chip size="small" label={team.time} />}
                                        {team.invoiceState === 'OPEN' && (
                                            <Chip
                                                size="small"
                                                color="error"
                                                label={t('event.liveDashboard.invoice.OPEN')}
                                            />
                                        )}
                                        {totalCount > 0 && (
                                            <Chip
                                                size="small"
                                                color={severityChipColor[severity]}
                                                label={`${checkedCount}/${totalCount}`}
                                            />
                                        )}
                                    </Stack>
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

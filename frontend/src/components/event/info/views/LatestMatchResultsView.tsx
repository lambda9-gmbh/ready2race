import React from 'react'
import {
    Box,
    Card,
    CardContent,
    Chip,
    Paper,
    Skeleton,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {formatDistanceToNow} from 'date-fns'
import {de, enUS} from 'date-fns/locale'
import {useFetch} from '@utils/hooks'
import {getLatestMatchResults} from '@api/sdk.gen'
import {EmojiEvents} from '@mui/icons-material'

interface LatestMatchResultsViewProps {
    eventId: string
    limit: number
    filters?: any
}

export const LatestMatchResultsView: React.FC<LatestMatchResultsViewProps> = ({eventId, limit}) => {
    const {t, i18n} = useTranslation()
    const locale = i18n.language === 'de' ? de : enUS

    const {data, pending} = useFetch(
        signal =>
            getLatestMatchResults({
                signal,
                path: {eventId},
                query: {limit},
            }),
        {deps: [eventId, limit]},
    )

    if (pending) {
        return (
            <Box sx={{p: 3}}>
                <Typography variant="h4" gutterBottom>
                    {t('event.info.viewTypes.latestMatchResults')}
                </Typography>
                <Stack spacing={2}>
                    {[...Array(3)].map((_, i) => (
                        <Skeleton variant="rectangular" height={180} key={i} />
                    ))}
                </Stack>
            </Box>
        )
    }

    if (!data || data.length === 0) {
        return (
            <Box sx={{textAlign: 'center', py: 4}}>
                <Typography variant="h6" color="text.secondary">
                    {t('event.info.noLatestMatchResults')}
                </Typography>
            </Box>
        )
    }

    return (
        <Box sx={{p: 3}}>
            <Stack direction="row" spacing={2} alignItems={'center'} sx={{mb: 3}}>
                <EmojiEvents fontSize={'large'} color={'info'} />
                <Typography variant="h4" gutterBottom>
                    {t('event.info.viewTypes.latestMatchResults')}
                </Typography>
            </Stack>
            <Stack spacing={3}>
                {data.map(match => (
                    <Card key={match.matchId} sx={{mb: 2}}>
                        <CardContent>
                            <Stack
                                direction="row"
                                justifyContent="space-between"
                                alignItems="center"
                                mb={2}>
                                <Stack>
                                    <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                                        {match.matchName && (
                                            <Typography
                                                variant="subtitle1"
                                                fontWeight="bold"
                                                color="primary">
                                                {match.matchName}
                                            </Typography>
                                        )}
                                        <Typography variant="h6">
                                            {match.competitionName}
                                        </Typography>
                                        {match.categoryName && (
                                            <Chip
                                                label={match.categoryName}
                                                size="small"
                                                color="primary"
                                                variant="outlined"
                                            />
                                        )}
                                    </Box>
                                    {match.roundName && match.roundName !== match.matchName && (
                                        <Typography variant="body2" color="text.secondary">
                                            {match.roundName}
                                        </Typography>
                                    )}
                                </Stack>
                                <Chip
                                    label={formatDistanceToNow(new Date(match.updatedAt), {
                                        addSuffix: true,
                                        locale,
                                    })}
                                    size="small"
                                    color="primary"
                                    variant="outlined"
                                />
                            </Stack>

                            <TableContainer component={Paper} variant="outlined">
                                <Table size="small">
                                    <TableHead>
                                        <TableRow>
                                            <TableCell align="center" sx={{width: 60}}>
                                                {t('event.info.place')}
                                            </TableCell>
                                            <TableCell>{t('event.info.team')}</TableCell>
                                            <TableCell>{t('club.club')}</TableCell>
                                            <TableCell>{t('event.info.participants')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {match.teams
                                            .sort((a, b) => a.place - b.place)
                                            .map(team => (
                                                <TableRow key={team.teamId}>
                                                    <TableCell align="center">
                                                        <Typography
                                                            variant="h6"
                                                            sx={{
                                                                fontWeight:
                                                                    team.place === 1
                                                                        ? 'bold'
                                                                        : team.place <= 3
                                                                          ? 'medium'
                                                                          : 'normal',
                                                                color:
                                                                    team.place === 1
                                                                        ? 'gold'
                                                                        : team.place === 2
                                                                          ? 'silver'
                                                                          : team.place === 3
                                                                            ? '#CD7F32'
                                                                            : 'inherit',
                                                            }}>
                                                            {team.place}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography variant="body2">
                                                            {team.teamName || '-'}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography
                                                            variant="body2"
                                                            color="text.secondary">
                                                            {team.clubName || '-'}
                                                        </Typography>
                                                    </TableCell>
                                                    <TableCell>
                                                        <Typography variant="body2">
                                                            {team.participants
                                                                .map(
                                                                    p =>
                                                                        `${p.firstName} ${p.lastName}${
                                                                            p.namedRole
                                                                                ? ` (${p.namedRole})`
                                                                                : ''
                                                                        }`,
                                                                )
                                                                .join(', ')}
                                                        </Typography>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </CardContent>
                    </Card>
                ))}
            </Stack>
        </Box>
    )
}

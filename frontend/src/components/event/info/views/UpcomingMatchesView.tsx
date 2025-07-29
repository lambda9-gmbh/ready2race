import {Avatar, Box, Card, CardContent, Chip, Skeleton, Stack, Typography} from '@mui/material'
import {
    AccessTime as AccessTimeIcon,
    AccessTimeFilled,
    EmojiEvents as EmojiEventsIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFetch} from '@utils/hooks'
import {getUpcomingMatches} from '@api/sdk.gen'
import {UpcomingCompetitionMatchInfo, UpcomingMatchTeamInfo} from '@api/types.gen'
import {format} from 'date-fns'

interface UpcomingMatchesViewProps {
    eventId: string
    limit: number
    filters?: any
}

const UpcomingMatchesView = ({eventId, limit}: UpcomingMatchesViewProps) => {
    const {t} = useTranslation()

    const {data, pending} = useFetch(
        signal =>
            getUpcomingMatches({
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
                    {t('event.info.viewTypes.upcomingMatches')}
                </Typography>
                <Stack spacing={2}>
                    {[...Array(3)].map((_, i) => (
                        <Skeleton variant="rectangular" height={120} key={i} />
                    ))}
                </Stack>
            </Box>
        )
    }

    if (!data || data.length === 0) {
        return (
            <Box
                sx={{
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                <Typography variant="h5" color="text.secondary">
                    {t('event.info.noUpcomingMatches')}
                </Typography>
            </Box>
        )
    }

    return (
        <Box sx={{p: 3, height: '100%', overflow: 'auto'}}>
            <Stack direction="row" spacing={2} alignItems={'center'} sx={{mb: 3}}>
                <AccessTimeFilled fontSize={'large'} color={'info'} />
                <Typography variant="h4" gutterBottom>
                    {t('event.info.viewTypes.upcomingMatches')}
                </Typography>
            </Stack>

            <Stack spacing={2}>
                {data.map((match: UpcomingCompetitionMatchInfo) => (
                    <Card key={match.matchId}>
                        <CardContent>
                            <Box sx={{display: 'flex', alignItems: 'center', gap: 3}}>
                                {/* Prominent Start Time */}
                                {match.scheduledStartTime && (
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            flexDirection: 'column',
                                            alignItems: 'center',
                                            minWidth: 120,
                                            p: 2,
                                            bgcolor: 'primary.main',
                                            color: 'primary.contrastText',
                                            borderRadius: 2,
                                        }}>
                                        <AccessTimeIcon sx={{mb: 1}} />
                                        <Typography variant="h6" fontWeight="bold">
                                            {format(
                                                new Date(match.scheduledStartTime),
                                                t('format.time'),
                                            )}
                                        </Typography>
                                        <Typography variant="caption">
                                            {format(
                                                new Date(match.scheduledStartTime),
                                                t('format.date'),
                                            )}
                                        </Typography>
                                    </Box>
                                )}

                                {/* Match Details */}
                                <Box sx={{flex: 1}}>
                                    <Stack mb={1}>
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: 1,
                                            }}>
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
                                            <Typography
                                                variant="subtitle2"
                                                color="text.secondary"
                                                sx={{mr: 1}}>
                                                {match.roundName}
                                            </Typography>
                                        )}
                                    </Stack>
                                    {/* Teams */}
                                    {match.teams.length > 0 && (
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                flexDirection:
                                                    match.teams.length > 2 ? 'column' : 'row',
                                                gap: match.teams.length > 2 ? 1 : 2,
                                                flexWrap: 'wrap',
                                                alignItems:
                                                    match.teams.length > 2 ? 'stretch' : 'center',
                                            }}>
                                            {match.teams.map(
                                                (team: UpcomingMatchTeamInfo, index) => (
                                                    <Box key={team.teamId} display={'flex'}>
                                                        <Box
                                                            sx={{
                                                                display: 'flex',
                                                                flex: 1,
                                                                alignItems: 'center',
                                                                gap: 1,
                                                                p: match.teams.length > 2 ? 1.5 : 0,
                                                                bgcolor:
                                                                    match.teams.length > 2
                                                                        ? 'action.hover'
                                                                        : 'transparent',
                                                                borderRadius:
                                                                    match.teams.length > 2 ? 1 : 0,
                                                                border:
                                                                    match.teams.length > 2
                                                                        ? '1px solid'
                                                                        : 'none',
                                                                borderColor:
                                                                    match.teams.length > 2
                                                                        ? 'divider'
                                                                        : 'transparent',
                                                            }}>
                                                            {team.startNumber && (
                                                                <Avatar
                                                                    sx={{
                                                                        width: 32,
                                                                        height: 32,
                                                                        fontSize: '0.875rem',
                                                                    }}>
                                                                    {team.startNumber}
                                                                </Avatar>
                                                            )}
                                                            <Box sx={{flex: 1}}>
                                                                {team.participants.length === 1 ? (
                                                                    <>
                                                                        <Typography
                                                                            variant="body1"
                                                                            fontWeight="bold">
                                                                            {`${team.participants[0].firstName} ${team.participants[0].lastName}`}
                                                                        </Typography>
                                                                        <Typography
                                                                            variant="caption"
                                                                            color="text.secondary">
                                                                            {team.teamName ||
                                                                                team.clubName}
                                                                        </Typography>
                                                                    </>
                                                                ) : (
                                                                    <>
                                                                        <Typography
                                                                            variant="body1"
                                                                            fontWeight="medium">
                                                                            {team.teamName ||
                                                                                team.clubName ||
                                                                                t(
                                                                                    'event.info.unnamed',
                                                                                )}
                                                                        </Typography>
                                                                        {team.participants.length >
                                                                            0 && (
                                                                            <Box
                                                                                sx={{
                                                                                    display: 'flex',
                                                                                    gap: 0.5,
                                                                                    mt: 0.5,
                                                                                    flexWrap:
                                                                                        'wrap',
                                                                                }}>
                                                                                {team.participants.map(
                                                                                    (
                                                                                        participant,
                                                                                        pIndex,
                                                                                    ) => (
                                                                                        <Chip
                                                                                            key={
                                                                                                pIndex
                                                                                            }
                                                                                            label={`${participant.firstName} ${participant.lastName}`}
                                                                                            size="small"
                                                                                            variant="outlined"
                                                                                            sx={{
                                                                                                height: 20,
                                                                                                fontSize:
                                                                                                    '0.7rem',
                                                                                                bgcolor:
                                                                                                    'background.paper',
                                                                                            }}
                                                                                        />
                                                                                    ),
                                                                                )}
                                                                            </Box>
                                                                        )}
                                                                    </>
                                                                )}
                                                            </Box>
                                                        </Box>
                                                        {match.teams.length <= 2 &&
                                                            index < match.teams.length - 1 && (
                                                                <Typography
                                                                    variant="h6"
                                                                    sx={{mx: 1}}
                                                                    color="text.secondary">
                                                                    vs
                                                                </Typography>
                                                            )}
                                                    </Box>
                                                ),
                                            )}
                                        </Box>
                                    )}

                                    {/* Additional Info */}
                                    <Box
                                        sx={{display: 'flex', gap: 2, mt: 1, alignItems: 'center'}}>
                                        {match.placeName && (
                                            <Box
                                                sx={{
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: 0.5,
                                                }}>
                                                <EmojiEventsIcon fontSize="small" color="action" />
                                                <Typography
                                                    variant="caption"
                                                    color="text.secondary">
                                                    {match.placeName}
                                                </Typography>
                                            </Box>
                                        )}
                                    </Box>
                                </Box>
                            </Box>
                        </CardContent>
                    </Card>
                ))}
            </Stack>
        </Box>
    )
}

export default UpcomingMatchesView

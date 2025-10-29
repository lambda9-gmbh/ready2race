import {
    Box,
    Card,
    Chip,
    Stack,
    Typography,
    Pagination,
    Collapse,
    useTheme,
    CardActionArea,
} from '@mui/material'
import {useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks'
import {getChallengeClubResults, getCompetitions, getRatingCategories} from '@api/sdk.gen'
import Throbber from '@components/Throbber'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {useTranslation} from 'react-i18next'
import {toSnakeCase} from '@utils/ApiUtils'
import {EventDto} from '@api/types.gen.ts'
import ResultsFilterBar from '@components/results/ResultsFilterBar'

type Props = {
    eventData: EventDto
    totalRanking: boolean
}

type FilterOption = {
    id: string
    label: string
}

const ResultsClubRanking = ({eventData, ...props}: Props) => {
    const {t} = useTranslation()
    const theme = useTheme()
    const feedback = useFeedback()

    const [page, setPage] = useState(1)
    const [selectedCompetition, setSelectedCompetition] = useState<FilterOption | null>(null)
    const [selectedRatingCategory, setSelectedRatingCategory] = useState<FilterOption | null>(null)
    const [expandedClubs, setExpandedClubs] = useState<Set<string>>(new Set())
    const pageSize = 10

    const toggleClubExpansion = (clubId: string) => {
        setExpandedClubs(prev => {
            const newSet = new Set(prev)
            if (newSet.has(clubId)) {
                newSet.delete(clubId)
            } else {
                newSet.add(clubId)
            }
            return newSet
        })
    }

    const {data, pending, error} = useFetch(
        signal =>
            getChallengeClubResults({
                signal,
                path: {eventId: eventData.id},
                query: {
                    limit: pageSize,
                    offset: (page - 1) * pageSize,
                    sort: JSON.stringify([
                        {
                            field: toSnakeCase(
                                props.totalRanking ? 'totalRank' : 'relativeRank',
                            ).toUpperCase(),
                            direction: 'ASC',
                        },
                    ]),
                    competition: selectedCompetition?.id,
                    ratingCategory: selectedRatingCategory?.id,
                },
            }),
        {
            deps: [eventData, page, selectedCompetition, selectedRatingCategory],
        },
    )

    const {data: ratingCategories} = useFetch(signal => getRatingCategories({signal}), {
        mapData: data =>
            data.data.map(dto => ({
                id: dto.id,
                label: dto.name,
            })),
        onResponse: ({error}) =>
            error &&
            feedback.error(
                t('common.load.error.multiple.short', {
                    entity: t('configuration.ratingCategory.ratingCategories'),
                }),
            ),
    })

    const {data: competitions} = useFetch(
        signal => getCompetitions({signal, path: {eventId: eventData.id}}),
        {
            mapData: data =>
                data.data.map(dto => ({
                    id: dto.id,
                    label: `${dto.properties.identifier} | ${dto.properties.name}`,
                })),
            onResponse: ({error}) =>
                error &&
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.competition.competitions'),
                    }),
                ),
        },
    )

    const handlePageChange = (_event: React.ChangeEvent<unknown>, value: number) => {
        setPage(value)
    }

    const handleCompetitionChange = (value: FilterOption | null) => {
        setSelectedCompetition(value)
        setPage(1)
    }

    const handleRatingCategoryChange = (value: FilterOption | null) => {
        setSelectedRatingCategory(value)
        setPage(1)
    }

    if (pending || !data) {
        return (
            <Box sx={{display: 'flex', justifyContent: 'center', p: 4}}>
                <Throbber />
            </Box>
        )
    }

    if (error) {
        return (
            <Box sx={{p: 2}}>
                <Typography color="error">{t('results.clubRanking.error')}</Typography>
            </Box>
        )
    }

    const totalPages = Math.ceil(data.pagination.total / pageSize)

    const resultSuffix = eventData.challengeResultType === 'DISTANCE' ? 'm' : ''

    return (
        <Box>
            <ResultsFilterBar
                competitions={competitions}
                selectedCompetition={selectedCompetition}
                onCompetitionChange={handleCompetitionChange}
                ratingCategories={ratingCategories}
                selectedRatingCategory={selectedRatingCategory}
                onRatingCategoryChange={handleRatingCategoryChange}
            />

            {data.data.length === 0 ? (
                <Box sx={{p: 4, textAlign: 'center'}}>
                    <Typography color="text.secondary">{t('results.noResults')}</Typography>
                </Box>
            ) : (
                <Stack spacing={2}>
                    {data.data.map(club => {
                        const isExpanded = expandedClubs.has(club.id)

                        return (
                            <Card key={club.id} sx={{width: '100%'}}>
                                <CardActionArea
                                    onClick={() => toggleClubExpansion(club.id)}
                                    sx={{p: 2, cursor: 'pointer'}}>
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            gap: 1,
                                            justifyContent: 'space-between',
                                            flexDirection: 'row',
                                            alignItems: 'center',
                                        }}>
                                        <Box
                                            sx={{
                                                flex: 1,
                                                display: 'flex',
                                                gap: 1,
                                                justifyContent: 'space-between',
                                                [theme.breakpoints.down('md')]: {
                                                    flexDirection: 'column',
                                                    justifyContent: 'space-between',
                                                },
                                            }}>
                                            <Stack
                                                direction={'row'}
                                                spacing={2}
                                                alignItems={'center'}
                                                sx={{flex: 1}}>
                                                <Chip
                                                    label={`#${props.totalRanking ? club.totalRank : club.relativeRank}`}
                                                    color="primary"
                                                    size="small"
                                                />
                                                <Typography variant="h6" fontWeight="bold">
                                                    {club.clubName}
                                                </Typography>
                                            </Stack>
                                            <Stack
                                                direction={'row'}
                                                spacing={2}
                                                alignItems={'center'}>
                                                <Typography variant="h5" fontWeight="bold">
                                                    {props.totalRanking
                                                        ? club.totalResult.toFixed(2)
                                                        : club.relativeResult.toFixed(2)}
                                                    {` ${resultSuffix}`}
                                                </Typography>
                                            </Stack>
                                        </Box>
                                        <ExpandMoreIcon
                                            sx={{
                                                transform: isExpanded
                                                    ? 'rotate(180deg)'
                                                    : 'rotate(0deg)',
                                                transition: 'transform 0.3s',
                                            }}
                                        />
                                    </Box>

                                    <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                                        <Box sx={{p: 2}}>
                                            <Stack spacing={1}>
                                                {club.teams.map(team => {
                                                    const allParticipants =
                                                        team.namedParticipants?.flatMap(
                                                            np => np.participants,
                                                        ) || []
                                                    const singleParticipant =
                                                        allParticipants.length === 1
                                                            ? allParticipants[0]
                                                            : null

                                                    return (
                                                        <Box
                                                            key={team.competitionRegistrationId}
                                                            sx={{
                                                                p: 1.5,
                                                                border: '1px solid',
                                                                borderColor: 'divider',
                                                                borderRadius: 1,
                                                            }}>
                                                            <Box
                                                                sx={{
                                                                    display: 'flex',
                                                                    flexWrap: 'wrap',
                                                                    gap: 1,
                                                                    justifyContent: 'space-between',
                                                                }}>
                                                                {!singleParticipant ? (
                                                                    <Typography variant="body2">
                                                                        {team.competitionRegistrationName ||
                                                                            'Unnamed Team'}
                                                                    </Typography>
                                                                ) : (
                                                                    <Typography
                                                                        component="span"
                                                                        variant="body2">
                                                                        {
                                                                            singleParticipant.firstName
                                                                        }{' '}
                                                                        {singleParticipant.lastName}
                                                                    </Typography>
                                                                )}
                                                                <Typography
                                                                    variant="body2"
                                                                    color="text.secondary">
                                                                    {team.result.toFixed(2)}
                                                                    {` ${resultSuffix}`}
                                                                </Typography>
                                                            </Box>
                                                            {team.namedParticipants &&
                                                                team.namedParticipants.length > 0 &&
                                                                !singleParticipant && (
                                                                    <Box sx={{mt: 1}}>
                                                                        <Stack spacing={0.5}>
                                                                            {team.namedParticipants.map(
                                                                                namedParticipant => (
                                                                                    <Box
                                                                                        key={
                                                                                            namedParticipant.id
                                                                                        }>
                                                                                        {namedParticipant.name && (
                                                                                            <Typography
                                                                                                variant="caption"
                                                                                                fontWeight="medium"
                                                                                                color="text.secondary">
                                                                                                {
                                                                                                    namedParticipant.name
                                                                                                }
                                                                                                :
                                                                                            </Typography>
                                                                                        )}
                                                                                        {namedParticipant.participants.map(
                                                                                            (
                                                                                                participant,
                                                                                                idx,
                                                                                            ) => (
                                                                                                <Typography
                                                                                                    key={
                                                                                                        participant.id
                                                                                                    }
                                                                                                    variant="caption"
                                                                                                    color="text.secondary"
                                                                                                    sx={{
                                                                                                        ml: namedParticipant.name
                                                                                                            ? 0.5
                                                                                                            : 0,
                                                                                                    }}>
                                                                                                    {
                                                                                                        participant.firstName
                                                                                                    }{' '}
                                                                                                    {
                                                                                                        participant.lastName
                                                                                                    }
                                                                                                    {idx <
                                                                                                    namedParticipant
                                                                                                        .participants
                                                                                                        .length -
                                                                                                        1
                                                                                                        ? ', '
                                                                                                        : ''}
                                                                                                </Typography>
                                                                                            ),
                                                                                        )}
                                                                                    </Box>
                                                                                ),
                                                                            )}
                                                                        </Stack>
                                                                    </Box>
                                                                )}
                                                        </Box>
                                                    )
                                                })}
                                            </Stack>
                                        </Box>
                                    </Collapse>
                                </CardActionArea>
                            </Card>
                        )
                    })}
                </Stack>
            )}

            {totalPages > 1 && (
                <Box sx={{display: 'flex', justifyContent: 'center', mt: 3}}>
                    <Pagination
                        count={totalPages}
                        page={page}
                        onChange={handlePageChange}
                        color="primary"
                        showFirstButton
                        showLastButton
                    />
                </Box>
            )}
        </Box>
    )
}

export default ResultsClubRanking

import {
    Box,
    Card,
    Chip,
    Stack,
    Typography,
    Pagination,
    Collapse,
    CardActionArea,
    useTheme,
} from '@mui/material'
import {useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks'
import {getChallengeParticipantResults, getCompetitions, getRatingCategories} from '@api/sdk.gen'
import Throbber from '@components/Throbber'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import {useTranslation} from 'react-i18next'
import {toSnakeCase} from '@utils/ApiUtils'
import {EventDto} from '@api/types.gen.ts'
import ResultsFilterBar from '@components/results/ResultsFilterBar'

type Props = {
    eventData: EventDto
}

type FilterOption = {
    id: string
    label: string
}

const ResultsIndividualRanking = ({eventData}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()

    const [page, setPage] = useState(1)
    const [selectedCompetition, setSelectedCompetition] = useState<FilterOption | null>(null)
    const [selectedRatingCategory, setSelectedRatingCategory] = useState<FilterOption | null>(null)
    const [expandedParticipants, setExpandedParticipants] = useState<Set<string>>(new Set())
    const pageSize = 10

    const toggleParticipantExpansion = (participantId: string) => {
        setExpandedParticipants(prev => {
            const newSet = new Set(prev)
            if (newSet.has(participantId)) {
                newSet.delete(participantId)
            } else {
                newSet.add(participantId)
            }
            return newSet
        })
    }

    const {data, pending, error} = useFetch(
        signal =>
            getChallengeParticipantResults({
                signal,
                path: {eventId: eventData.id},
                query: {
                    limit: pageSize,
                    offset: (page - 1) * pageSize,
                    sort: JSON.stringify([
                        {
                            field: toSnakeCase('rank').toUpperCase(),
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
                <Typography color="error">{t('results.individualRanking.error')}</Typography>
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
                    {data.data.map(participant => {
                        const isExpanded = expandedParticipants.has(participant.id)

                        return (
                            <Card key={participant.id} sx={{width: '100%'}}>
                                <CardActionArea
                                    onClick={() => toggleParticipantExpansion(participant.id)}
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
                                                alignItems: 'center',
                                                [theme.breakpoints.down('md')]: {
                                                    flexDirection: 'column',
                                                    alignItems: 'start',
                                                },
                                            }}>
                                            <Stack
                                                direction={'row'}
                                                spacing={2}
                                                alignItems={'center'}
                                                sx={{flex: 1}}>
                                                <Chip
                                                    label={`#${participant.rank}`}
                                                    color="primary"
                                                    size="small"
                                                />
                                                <Box>
                                                    <Typography variant="h6" fontWeight="bold">
                                                        {participant.firstName}{' '}
                                                        {participant.lastName}
                                                    </Typography>
                                                    <Typography
                                                        variant="body2"
                                                        color="text.secondary">
                                                        {participant.clubName}
                                                    </Typography>
                                                </Box>
                                            </Stack>
                                            <Typography variant="h5" fontWeight="bold">
                                                {participant.result.toFixed(2)}
                                                {` ${resultSuffix}`}
                                            </Typography>
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
                                                {participant.teams.map(team => (
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
                                                                [theme.breakpoints.down('md')]: {
                                                                    flexDirection: 'column',
                                                                },
                                                            }}>
                                                            <Box>
                                                                <Typography variant="body2">
                                                                    {team.competitionIdentifier} |{' '}
                                                                    {team.competitionName}
                                                                </Typography>
                                                                {team.ratingCategoryDto && (
                                                                    <Typography
                                                                        variant="caption"
                                                                        color="text.secondary">
                                                                        {
                                                                            team.ratingCategoryDto
                                                                                .name
                                                                        }
                                                                    </Typography>
                                                                )}
                                                            </Box>
                                                            <Typography
                                                                variant="body2"
                                                                color="text.secondary">
                                                                {team.result.toFixed(2)}
                                                                {` ${resultSuffix}`}
                                                            </Typography>
                                                        </Box>
                                                    </Box>
                                                ))}
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

export default ResultsIndividualRanking

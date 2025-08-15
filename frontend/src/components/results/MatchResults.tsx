import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCompetitions, getLatestMatchResults} from '@api/sdk.gen.ts'
import {Alert, Box, Card, CardActionArea, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {useState} from 'react'
import {CompetitionDto, LatestMatchResultInfo} from '@api/types.gen.ts'
import ResultsMatchDialog from '@components/results/ResultsMatchDialog.tsx'
import ResultsMatchCard from '@components/results/ResultsMatchCard.tsx'

type Props = {
    eventId: string
    competitionSelected: CompetitionDto | null
    setCompetitionSelected: (value: CompetitionDto | null) => void
}

const MatchResults = ({eventId, competitionSelected, setCompetitionSelected}: Props) => {
    const matchesLimit = 100 // todo

    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data: competitionsData, pending: competitionsPending} = useFetch(
        signal =>
            getCompetitions({
                signal,
                path: {eventId},
            }),
        {
            onResponse: response => {
                if (response.error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                }
            },
            deps: [eventId],
        },
    )

    const onClickCompetition = (competition: CompetitionDto) => {
        setCompetitionSelected(competition)
    }

    const {data: matchResultsData, pending: matchResultsPending} = useFetch(
        signal =>
            getLatestMatchResults({
                signal,
                path: {eventId},
                query: {
                    limit: matchesLimit,
                    competitionId: competitionSelected?.id,
                },
            }),
        {
            preCondition: () => competitionSelected !== null,
            onResponse: response => {
                if (response.error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('results.matchResults.matchResults'),
                        }),
                    )
                }
            },
            deps: [eventId, competitionSelected, competitionsData, matchesLimit],
        },
    )

    // todo: reload data once in a while

    const [dialogOpen, setDialogOpen] = useState(false)
    const [matchSelected, setMatchSelected] = useState<LatestMatchResultInfo | null>(null)
    const onClickMatch = (match: LatestMatchResultInfo) => {
        setDialogOpen(true)
        setMatchSelected(match)
    }
    const closeDialog = () => {
        setDialogOpen(false)
        setMatchSelected(null)
    }

    return (
        <>
            <Stack spacing={2} sx={{alignItems: 'center', p: 2}}>
                {competitionsPending || (competitionSelected && matchResultsPending) ? (
                    <Throbber />
                ) : !competitionSelected ? (
                    competitionsData?.data.length === 0 ? (
                        <Alert severity={'info'}>
                            {t('results.matchResults.noCompetitions')}
                        </Alert>
                    ) : (
                        competitionsData?.data.map(competition => (
                            <Card sx={{flex: 1, width: 1}} key={competition.id}>
                                <CardActionArea onClick={() => onClickCompetition(competition)}>
                                    <CardContent>
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                gap: 1,
                                                justifyContent: 'space-between',
                                                alignItems: 'center',
                                            }}>
                                            <Box>
                                                <Typography variant={'h6'}>
                                                    {competition.properties.identifier} |{' '}
                                                    {competition.properties.name}
                                                </Typography>
                                            </Box>
                                            {competition.properties.competitionCategory && (
                                                <Chip
                                                    label={
                                                        competition.properties.competitionCategory
                                                            .name
                                                    }
                                                    color="primary"
                                                    variant="outlined"
                                                />
                                            )}
                                        </Box>
                                    </CardContent>
                                </CardActionArea>
                            </Card>
                        ))
                    )
                ) : matchResultsData?.length === 0 ? (
                    <Alert severity={'info'}>
                        {t('results.matchResults.noResults')}
                    </Alert>
                ) : (
                    matchResultsData
                        ?.sort((a, b) =>
                            (a.startTime ?? a.eventDayDate ?? '') >
                            (b.startTime ?? b.eventDayDate ?? '')
                                ? -1
                                : 1,
                        )
                        .map(match => (
                            <ResultsMatchCard
                                match={match}
                                selectMatch={onClickMatch}
                                key={match.matchId}
                            />
                        ))
                )}
            </Stack>
            <ResultsMatchDialog
                match={matchSelected}
                dialogOpen={dialogOpen}
                closeDialog={closeDialog}
            />
        </>
    )
}

export default MatchResults

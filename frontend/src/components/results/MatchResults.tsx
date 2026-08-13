import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCompetitionsHavingResults, getLatestMatchResults} from '@api/sdk.gen.ts'
import {Alert, Box, Card, CardActionArea, CardContent, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {useState} from 'react'
import {CompetitionChoiceDto, EventNoticeDto, LatestMatchResultInfo} from '@api/types.gen.ts'
import ResultsMatchDialog from '@components/results/ResultsMatchDialog.tsx'
import ResultsMatchCard from '@components/results/ResultsMatchCard.tsx'
import EventNoticeBanner from '@components/eventNotice/EventNoticeBanner.tsx'

type Props = {
    eventId: string
    competitionSelected: CompetitionChoiceDto | null
    setCompetitionSelected: (value: CompetitionChoiceDto | null) => void
    /**
     * Der veranstaltungsweite Hinweis aus dem EventDto der Seite. Dieser Tab pollt nicht —
     * der Banner ist so aktuell wie der Rest des Tabs (Stand des Seitenaufrufs); live
     * nachgezogen wird er auf dem Live-Tab und in "Mein Event".
     */
    notice?: EventNoticeDto | null
}

const MatchResults = ({eventId, competitionSelected, setCompetitionSelected, notice}: Props) => {
    const matchesLimit = 100 // todo

    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data: competitionsData, pending: competitionsPending} = useFetch(
        signal =>
            getCompetitionsHavingResults({
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

    const onClickCompetition = (competition: CompetitionChoiceDto) => {
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
            <Stack spacing={2} sx={{p: 2}}>
                <EventNoticeBanner notice={notice} />
                {competitionsPending || (competitionSelected && matchResultsPending) ? (
                    <Throbber />
                ) : !competitionSelected ? (
                    competitionsData?.data.length === 0 ? (
                        <Alert severity={'info'}>{t('results.matchResults.noResults')}</Alert>
                    ) : (
                        competitionsData?.data.map(competition => (
                            <Card sx={{flex: 1, width: 1}} key={competition.id}>
                                <CardActionArea onClick={() => onClickCompetition(competition)}>
                                    <CardContent>
                                        {/* Umbrechende Zeile statt Name-links/Chip-rechts: der
                                            Chip drückte auf schmalen Bildschirmen den Namen in
                                            eine Ein-Wort-Spalte und lief selbst aus der Karte. */}
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                columnGap: 1,
                                                rowGap: 0.5,
                                                flexWrap: 'wrap',
                                                alignItems: 'center',
                                            }}>
                                            <Typography variant={'h6'} sx={{minWidth: 0}}>
                                                {competition.identifier} | {competition.name}
                                            </Typography>
                                            {competition.category && (
                                                <Chip
                                                    label={competition.category}
                                                    color="primary"
                                                    variant="outlined"
                                                    size="small"
                                                />
                                            )}
                                        </Box>
                                    </CardContent>
                                </CardActionArea>
                            </Card>
                        ))
                    )
                ) : matchResultsData?.length === 0 ? (
                    <Alert severity={'info'}>{t('results.matchResults.noResults')}</Alert>
                ) : (
                    <>
                        {/* Überschrift des gewählten Wettkampfs: vorher ein Chip, der auf dem
                            Telefon nicht umbrach und lange Namen abschnitt. */}
                        <Box
                            sx={{
                                display: 'flex',
                                columnGap: 1,
                                rowGap: 0.5,
                                flexWrap: 'wrap',
                                alignItems: 'center',
                                mb: 1,
                            }}>
                            <Typography
                                fontWeight={'bold'}
                                color={'primary'}
                                sx={{minWidth: 0, overflowWrap: 'anywhere'}}>
                                {competitionSelected.identifier} | {competitionSelected.name}
                            </Typography>
                            {competitionSelected.category && (
                                <Chip
                                    label={competitionSelected.category}
                                    color="primary"
                                    variant="outlined"
                                    size="small"
                                />
                            )}
                        </Box>
                        {matchResultsData
                            ?.sort((a, b) => ((a.startTime ?? '') > (b.startTime ?? '') ? -1 : 1))
                            .map(match => (
                                <ResultsMatchCard
                                    match={match}
                                    selectMatch={onClickMatch}
                                    key={match.matchId}
                                />
                            ))}
                    </>
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

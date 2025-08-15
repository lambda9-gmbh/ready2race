import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCompetitionsHavingResults, getLatestMatchResults} from '@api/sdk.gen.ts'
import {
    Alert,
    Box,
    Button,
    Card,
    CardActionArea,
    CardContent,
    Chip,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Grid2,
    ListItemText,
    Stack,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {useState} from 'react'
import BaseDialog from '@components/BaseDialog.tsx'
import {CompetitionChoiceDto, LatestMatchResultInfo} from '@api/types.gen.ts'
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined'
import {format} from 'date-fns'
import {sortByPlaces} from '@utils/helpers.ts'

type Props = {
    eventId: string
    competitionSelected: CompetitionChoiceDto | null
    setCompetitionSelected: (value: CompetitionChoiceDto | null) => void
}

const MatchResults = ({eventId, competitionSelected, setCompetitionSelected}: Props) => {
    const resultsLimit = 100 // todo

    const {t} = useTranslation()
    const theme = useTheme()
    const feedback = useFeedback()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

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
                    limit: resultsLimit,
                    competitionId: competitionSelected?.id,
                },
            }),
        {
            preCondition: () => competitionSelected !== null,
            onResponse: response => {
                if (response.error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.info.viewTypes.latestMatchResults'),
                        }),
                    )
                }
            },
            deps: [eventId, competitionSelected, competitionsData, resultsLimit],
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

    console.log(matchResultsPending)

    return (
        <>
            <Box sx={{flex: 1, p: 2}}>
                <Stack spacing={2} sx={{alignItems: 'center'}}>
                    {competitionsPending || (competitionSelected && matchResultsPending) ? (
                        <Throbber />
                    ) : !competitionSelected ? (
                        competitionsData?.data.length === 0 ? (
                            <Alert severity={'info'}>
                                {'There are no competitions for this event'}
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
                                                        {competition.identifier} |{' '}
                                                        {competition.name}
                                                    </Typography>
                                                </Box>
                                                {competition.category && (
                                                    <Chip
                                                        label={competition.category}
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
                            {'There are no results for this competition yet'}
                        </Alert>
                    ) : (
                        <>
                            {matchResultsData
                                ?.sort((a, b) =>
                                    (a.startTime ?? a.eventDayDate ?? '') >
                                    (b.startTime ?? b.eventDayDate ?? '')
                                        ? -1
                                        : 1,
                                )
                                .map(match => (
                                    <Card sx={{flex: 1, width: 1}} key={match.matchId}>
                                        <CardActionArea onClick={() => onClickMatch(match)}>
                                            <CardContent>
                                                <Box
                                                    sx={{
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                    }}>
                                                    <Box>
                                                        <Typography>{match.roundName}</Typography>
                                                        <Box>
                                                            {match.matchName && (
                                                                <Typography variant={'h6'}>
                                                                    {match.matchName}
                                                                </Typography>
                                                            )}
                                                        </Box>
                                                    </Box>
                                                    {(match.startTime || match.eventDayDate) && (
                                                        <Typography>
                                                            {match.startTime
                                                                ? format(
                                                                      new Date(match.startTime),
                                                                      t('format.datetime'),
                                                                  )
                                                                : format(
                                                                      new Date(match.eventDayDate!),
                                                                      t('format.date'),
                                                                  )}
                                                        </Typography>
                                                    )}
                                                </Box>
                                            </CardContent>
                                        </CardActionArea>
                                    </Card>
                                ))}
                        </>
                    )}
                </Stack>
            </Box>
            <BaseDialog
                open={dialogOpen}
                onClose={closeDialog}
                fullScreen={smallScreenLayout}
                maxWidth={!smallScreenLayout ? 'md' : undefined}>
                {matchSelected && (
                    <>
                        <DialogTitle>
                            <Stack>
                                <Typography variant={matchSelected.matchName ? 'body2' : 'h6'}>
                                    {matchSelected.competitionName} - {matchSelected.roundName}
                                </Typography>
                                {matchSelected.matchName ?? ''}
                                <Box>
                                    <Chip
                                        label={matchSelected.categoryName}
                                        variant={'outlined'}
                                        color={'primary'}
                                        size={'small'}
                                    />
                                </Box>
                            </Stack>
                        </DialogTitle>
                        <DialogContent>
                            <Stack spacing={2}>
                                {(matchSelected.startTime || matchSelected.eventDayDate) && (
                                    <Stack direction={'row'} spacing={1}>
                                        <ScheduleOutlinedIcon color={'primary'} />
                                        <Typography>
                                            {matchSelected.startTime
                                                ? format(
                                                      new Date(matchSelected.startTime),
                                                      t('format.datetime'),
                                                  )
                                                : format(
                                                      new Date(matchSelected.eventDayDate!),
                                                      t('format.date'),
                                                  ) +
                                                  (matchSelected.eventDayName
                                                      ? ` (${matchSelected.eventDayName})`
                                                      : '')}
                                        </Typography>
                                    </Stack>
                                )}
                                {sortByPlaces(matchSelected.teams).map(team => (
                                    <Card key={team.teamId}>
                                        <CardContent>
                                            <Stack
                                                direction={'row'}
                                                sx={{
                                                    alignItems: 'center',
                                                    justifyContent: 'space-between',
                                                }}>
                                                <Typography variant={'h5'}>
                                                    {team.place}.
                                                </Typography>
                                                <Typography>
                                                    {team.clubName +
                                                        (team.teamName ? ` ${team.teamName}` : '')}
                                                </Typography>
                                            </Stack>
                                            <Divider sx={{my: 1}} />
                                            <Grid2 container>
                                                {team.participants
                                                    .sort((a, b) =>
                                                        a.namedRole === b.namedRole
                                                            ? a.firstName === b.firstName
                                                                ? a.lastName > b.lastName
                                                                    ? 1
                                                                    : -1
                                                                : a.firstName > b.firstName
                                                                  ? 1
                                                                  : -1
                                                            : (a.namedRole ?? '') >
                                                                (b.namedRole ?? '')
                                                              ? 1
                                                              : -1,
                                                    )
                                                    .map(participant => (
                                                        <Grid2
                                                            size={6}
                                                            key={participant.participantId}>
                                                            <ListItemText
                                                                primary={
                                                                    participant.firstName +
                                                                    ' ' +
                                                                    participant.lastName
                                                                }
                                                                secondary={participant.namedRole}
                                                            />
                                                        </Grid2>
                                                    ))}
                                            </Grid2>
                                        </CardContent>
                                    </Card>
                                ))}
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={closeDialog}>{t('common.close')}</Button>
                        </DialogActions>
                    </>
                )}
            </BaseDialog>
        </>
    )
}

export default MatchResults

import {useFetch} from '@utils/hooks.ts'
import {getLatestMatchResults} from '@api/sdk.gen.ts'
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
import {LatestMatchResultInfo} from '@api/types.gen.ts'
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined'
import {format} from 'date-fns'

type Props = {
    eventId: string
}

const MatchResults = ({eventId}: Props) => {
    const resultsLimit = 100 // todo

    const {t} = useTranslation()
    const theme = useTheme()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    const {data, pending} = useFetch(
        signal =>
            getLatestMatchResults({
                signal,
                path: {eventId},
                query: {limit: resultsLimit},
            }),
        {deps: [eventId, resultsLimit]},
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
            <Box sx={{flex: 1, p: 2}}>
                <Stack spacing={2} sx={{alignItems: 'center'}}>
                    {pending ? (
                        <Throbber />
                    ) : data?.length === 0 ? (
                        <Alert severity={'info'}>{'There are no results for this event'}</Alert>
                    ) : (
                        data
                            ?.sort((a, b) => (a.updatedAt > b.updatedAt ? -1 : 1))
                            .map(match => (
                                <Card sx={{flex: 1, width: 1}} key={match.matchId}>
                                    <CardActionArea onClick={() => onClickMatch(match)}>
                                        <CardContent>
                                            <Box
                                                sx={{
                                                    display: 'flex',
                                                    gap: 1,
                                                    justifyContent: 'space-between',
                                                    alignItems: 'center',
                                                }}>
                                                <Box>
                                                    {match.matchName && (
                                                        <Typography variant={'h6'}>
                                                            {match.matchName}
                                                        </Typography>
                                                    )}
                                                </Box>
                                                <Chip
                                                    label={
                                                        match.competitionName +
                                                        (match.categoryName
                                                            ? ' - ' + match.categoryName
                                                            : '')
                                                    }
                                                    color="primary"
                                                    variant="outlined"
                                                />
                                            </Box>
                                            <Typography variant={'body2'}>
                                                {match.roundName}
                                            </Typography>
                                        </CardContent>
                                    </CardActionArea>
                                </Card>
                            ))
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
                                {matchSelected.teams
                                    .sort((a, b) => a.place - b.place)
                                    .sort((a, b) => a.place - b.place)
                                    .map(team => (
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
                                                            (team.teamName
                                                                ? ` ${team.teamName}`
                                                                : '')}
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
                                                                    secondary={
                                                                        participant.namedRole
                                                                    }
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

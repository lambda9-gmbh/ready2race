import {
    Box,
    Button,
    Card,
    CardContent,
    Divider,
    Grid2,
    Link,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {downloadCompetitionPlacesCsv, getCompetitionPlaces} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {getFilename} from '@utils/helpers.ts'
import {useRef} from 'react'

const CompetitionPlaces = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()
    const {data: placesData, pending: placesPending} = useFetch(
        signal =>
            getCompetitionPlaces({
                signal,
                path: {eventId: eventId, competitionId: competitionId},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.places.places'),
                        }),
                    )
                }
            },
            deps: [eventId, competitionId],
        },
    )

    const downloadRef = useRef<HTMLAnchorElement>(null)
    const handleDownloadCompetitionPlacesCSV = async () => {
        const {data, error, response} = await downloadCompetitionPlacesCsv({
            path: {
                eventId,
                competitionId,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.competition.execution.startList.error.missingStartTime'))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (data !== undefined && anchor) {
            // need Blob constructor for text/csv
            anchor.href = URL.createObjectURL(new Blob([data])) // TODO: @Memory: revokeObjectURL() when done
            anchor.download =
                getFilename(response) ?? `Places-${competitionId}.${'CSV'.toLowerCase()}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    return placesData ? (
        placesData.length > 0 ? (
            <>
                <Link ref={downloadRef} display={'none'}></Link>
                <Stack spacing={2}>
                    <Button
                        variant="contained"
                        sx={{alignSelf: 'flex-end', display: 'flex'}}
                        onClick={() => handleDownloadCompetitionPlacesCSV()}>
                        {t('common.file.downloadCsv')}
                    </Button>
                    {placesData.map(team => (
                        <Card key={team.teamNumber}>
                            <CardContent>
                                <Stack
                                    spacing={4}
                                    direction={'row'}
                                    sx={{
                                        justifyContent: 'space-between',
                                    }}>
                                    <Typography variant={team.place ? 'h5' : 'body1'}>
                                        {team.place}
                                    </Typography>
                                    <Box>
                                        <Typography textAlign={'right'}>
                                            {team.actualClubName ?? team.clubName}
                                        </Typography>
                                        <Typography
                                            color={'textSecondary'}
                                            variant={'body2'}
                                            textAlign={'right'}>
                                            {`${t('club.registeredBy')} ` +
                                                team.clubName +
                                                ` | ${team.teamName}`}
                                        </Typography>
                                    </Box>
                                </Stack>
                                <Divider sx={{my: 1}} />
                                <Grid2 container>
                                    {team.namedParticipants
                                        .flatMap(it => it.participants)
                                        .sort((a, b) =>
                                            a.namedParticipantName === b.namedParticipantName
                                                ? a.firstName === b.firstName
                                                    ? a.lastName > b.lastName
                                                        ? 1
                                                        : -1
                                                    : a.firstName > b.firstName
                                                      ? 1
                                                      : -1
                                                : (a.namedParticipantName ?? '') >
                                                    (b.namedParticipantName ?? '')
                                                  ? 1
                                                  : -1,
                                        )
                                        .map(participant => (
                                            <Grid2 size={6} key={participant.participantId}>
                                                <ListItemText
                                                    primary={
                                                        participant.firstName +
                                                        ' ' +
                                                        participant.lastName
                                                    }
                                                    secondary={
                                                        <>
                                                            <Typography
                                                                variant="body2"
                                                                color="text.secondary">
                                                                {participant.namedParticipantName}
                                                            </Typography>
                                                            <Typography
                                                                variant="body2"
                                                                color="text.secondary">
                                                                {participant.externalClubName ??
                                                                    team.clubName}
                                                            </Typography>
                                                        </>
                                                    }
                                                />
                                            </Grid2>
                                        ))}
                                </Grid2>
                            </CardContent>
                        </Card>
                    ))}
                </Stack>
            </>
        ) : (
            <Typography>{t('event.competition.places.noPlaces')}</Typography>
        )
    ) : (
        placesPending && <Throbber />
    )
}

export default CompetitionPlaces

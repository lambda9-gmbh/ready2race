import {
    Box,
    Button,
    Card,
    CardContent,
    Divider,
    Grid2,
    IconButton,
    Link,
    ListItemText,
    Stack,
    Tooltip,
    Typography,
} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {downloadCompetitionPlacesCsv, getCompetitionPlaces} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {getFilename} from '@utils/helpers.ts'
import {useRef, useState} from 'react'
import {useUser} from '@contexts/user/UserContext.ts'
import {readEventGlobal} from '@authorization/privileges.ts'
import WorkspacePremium from '@mui/icons-material/WorkspacePremium'
import EmojiEvents from '@mui/icons-material/EmojiEvents'
import AwardCertificateDialog from '@components/awardCertificate/AwardCertificateDialog.tsx'
import AwardCeremonyDialog from '@components/awardCeremony/AwardCeremonyDialog.tsx'

const CompetitionPlaces = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()
    const user = useUser()
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

    const [awardCertificateDialogOpen, setAwardCertificateDialogOpen] = useState(false)
    const [awardCertificateRegistrationId, setAwardCertificateRegistrationId] = useState<
        string | undefined
    >(undefined)

    const openAwardCertificateDialog = (registrationId?: string) => {
        setAwardCertificateRegistrationId(registrationId)
        setAwardCertificateDialogOpen(true)
    }

    const [awardCeremonyDialogOpen, setAwardCeremonyDialogOpen] = useState(false)

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
                    <Stack
                        direction={'row'}
                        spacing={2}
                        sx={{alignSelf: 'flex-end', display: 'flex'}}>
                        {user.checkPrivilege(readEventGlobal) && (
                            <Button
                                variant="contained"
                                startIcon={<WorkspacePremium />}
                                onClick={() => openAwardCertificateDialog()}>
                                {t('awardCertificate.download.button')}
                            </Button>
                        )}
                        {user.checkPrivilege(readEventGlobal) && (
                            <Button
                                variant="contained"
                                startIcon={<EmojiEvents />}
                                onClick={() => setAwardCeremonyDialogOpen(true)}>
                                {t('awardCeremony.download.button')}
                            </Button>
                        )}
                        {user.loggedIn && (
                            <Button
                                variant="contained"
                                onClick={() => handleDownloadCompetitionPlacesCSV()}>
                                {t('common.file.downloadCsv')}
                            </Button>
                        )}
                    </Stack>
                    {placesData.map(team => (
                        <Card key={team.teamNumber}>
                            <CardContent>
                                <Stack
                                    spacing={4}
                                    direction={'row'}
                                    sx={{
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
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
                                    {/* Teams ohne Urkunde (DNF, DSQ, abgemeldet) zeigen das Download-Icon
                                    nicht - der Download würde sonst nur mit NoResults fehlschlagen. Dieselbe
                                    Ausschlussregel wie im Urkundengenerator (AwardCertificateService.excluded). */}
                                    {user.checkPrivilege(readEventGlobal) && !team.excluded && (
                                        <Tooltip title={t('awardCertificate.download.buttonSingle')}>
                                            <IconButton
                                                onClick={() =>
                                                    openAwardCertificateDialog(
                                                        team.competitionRegistrationId,
                                                    )
                                                }>
                                                <WorkspacePremium />
                                            </IconButton>
                                        </Tooltip>
                                    )}
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
                <AwardCertificateDialog
                    open={awardCertificateDialogOpen}
                    onClose={() => setAwardCertificateDialogOpen(false)}
                    eventId={eventId}
                    competitionId={competitionId}
                    registrationId={awardCertificateRegistrationId}
                />
                <AwardCeremonyDialog
                    open={awardCeremonyDialogOpen}
                    onClose={() => setAwardCeremonyDialogOpen(false)}
                    eventId={eventId}
                    competitionId={competitionId}
                />
            </>
        ) : (
            <Typography>{t('event.competition.places.noPlaces')}</Typography>
        )
    ) : (
        placesPending && <Throbber />
    )
}

export default CompetitionPlaces

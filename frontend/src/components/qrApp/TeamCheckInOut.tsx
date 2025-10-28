import {Stack, Typography, Card, CardContent, Box, Chip, Alert, Divider} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {checkInOutParticipant, getTeamsByParticipantQrCode} from '@api/sdk.gen.ts'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import {useState} from 'react'
import Throbber from '@components/Throbber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'

export const TeamCheckInOut = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const {navigateTo, qr, eventId} = useAppSession()

    const {data: teamsData, pending: teamsPending} = useFetch(
        signal =>
            getTeamsByParticipantQrCode({
                signal,
                path: {
                    eventId,
                    qrCode: qr.qrCodeId!,
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('club.participant.tracking.teams'),
                        }),
                    )
                }
            },
            preCondition: () => qr.qrCodeId !== null,
            deps: [eventId, qr],
        },
    )

    const selectedParticipant = teamsData
        ?.flatMap(team => team.participants)
        .find(participant => participant.participantId === qr.response?.id)

    const handleCheckInOut = async (checkIn: boolean) => {
        setSubmitting(true)
        if (!selectedParticipant) return

        const {error} = await checkInOutParticipant({
            path: {
                eventId,
                participantId: selectedParticipant.participantId,
            },
            query: {
                checkIn: checkIn,
            },
        })

        setSubmitting(false)
        if (error) {
            feedback.error(
                checkIn
                    ? t('club.participant.tracking.checkIn.error')
                    : t('club.participant.tracking.checkOut.error'),
            )
        } else {
            feedback.success(
                checkIn
                    ? t('club.participant.tracking.checkIn.success')
                    : t('club.participant.tracking.checkOut.success'),
            )
        }
        navigateTo('APP_Scanner')
    }

    return (
        <Stack spacing={2} sx={{width: 1, flex: 1}}>
            {teamsPending ? (
                <Throbber />
            ) : !teamsData || selectedParticipant === undefined ? (
                <Alert severity="error">{t('club.participant.tracking.participantNotFound')}</Alert>
            ) : teamsData.length === 0 ? (
                <Alert severity="info">{t('club.participant.tracking.noTeamsFound')}</Alert>
            ) : (
                <Stack spacing={2} sx={{justifyContent: 'space-between', flex: 1}}>
                    <Box>
                        <Typography variant="h6">{t('club.participant.tracking.teams')}</Typography>
                        <Stack spacing={2}>
                            {teamsData
                                .sort((a, b) =>
                                    a.competitionIdentifier > b.competitionIdentifier ? 1 : -1,
                                )
                                .map(team => (
                                    <Card key={team.competitionRegistrationId} variant="outlined">
                                        <CardContent>
                                            <Stack
                                                direction="row"
                                                justifyContent="space-between"
                                                alignItems="center">
                                                <Box>
                                                    <Typography variant="h6" gutterBottom>
                                                        {team.competitionIdentifier} |{' '}
                                                        {team.competitionName}
                                                    </Typography>
                                                    <Typography>
                                                        {team.clubName +
                                                            (team.teamName
                                                                ? ' ' + team.teamName
                                                                : '')}
                                                    </Typography>
                                                </Box>
                                            </Stack>
                                            <Divider sx={{my: 1}} />
                                            <Stack spacing={1}>
                                                {team.participants.map(participant => (
                                                    <Stack
                                                        key={
                                                            participant.participantId +
                                                            team.competitionRegistrationId
                                                        }
                                                        direction={'row'}
                                                        spacing={2}
                                                        sx={{justifyContent: 'space-between'}}>
                                                        <Typography sx={{flex: 2}}>
                                                            {participant.firstName}{' '}
                                                            {participant.lastName}
                                                        </Typography>
                                                        {participant.currentStatus && (
                                                            <Chip
                                                                sx={{flex: 1}}
                                                                label={
                                                                    participant.currentStatus ===
                                                                    'ENTRY'
                                                                        ? t(
                                                                              'club.participant.tracking.in',
                                                                          )
                                                                        : t(
                                                                              'club.participant.tracking.out',
                                                                          )
                                                                }
                                                                color={
                                                                    participant.currentStatus ===
                                                                    'ENTRY'
                                                                        ? 'success'
                                                                        : 'default'
                                                                }
                                                                size="small"
                                                            />
                                                        )}
                                                    </Stack>
                                                ))}
                                            </Stack>
                                        </CardContent>
                                    </Card>
                                ))}
                        </Stack>
                    </Box>
                    <Box
                        sx={{
                            position: 'sticky',
                            bottom: 0,
                            width: 1,
                            display: 'flex',
                            justifyContent: 'center',
                            py: 1,
                        }}
                        bgcolor={'background.default'}>
                        <LoadingButton
                            pending={submitting || teamsPending}
                            variant={'contained'}
                            onClick={() =>
                                handleCheckInOut(selectedParticipant.currentStatus !== 'ENTRY')
                            }>
                            {selectedParticipant.currentStatus === 'ENTRY'
                                ? t('club.participant.tracking.checkOutText')
                                : t('club.participant.tracking.checkInText')}
                        </LoadingButton>
                    </Box>
                </Stack>
            )}
        </Stack>
    )
}

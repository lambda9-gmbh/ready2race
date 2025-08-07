import {Stack, Typography, Card, CardContent, Box, Chip, Alert} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {checkInOutParticipant, getTeamsByParticipantQrCode} from '@api/sdk.gen.ts'
import {qrEventRoute} from '@routes'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import {useState} from 'react'
import Throbber from '@components/Throbber.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'

export const TeamCheckInOut = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const {qr} = useAppSession()
    const {eventId} = qrEventRoute.useParams()

    const [reloadTeams, setReloadTeams] = useState(false)

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
                            entity: t('team.teams'),
                        }),
                    )
                }
            },
            preCondition: () => qr.qrCodeId !== null,
            deps: [eventId, qr, reloadTeams],
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
            feedback.error(checkIn ? t('team.checkIn.error') : t('team.checkOut.error'))
        } else {
            feedback.success(checkIn ? t('team.checkIn.success') : t('team.checkOut.success'))
        }
        setReloadTeams(prev => !prev)
    }

    return (
        <Stack spacing={2} sx={{width: '100%'}}>
            {teamsPending ? (
                <Throbber />
            ) : !teamsData || selectedParticipant === undefined ? (
                <Alert severity="error">{t('team.noTeamsFound')}</Alert>
            ) : teamsData.length === 0 ? (
                <Alert severity="info">{t('team.noTeamsFound')}</Alert>
            ) : (
                <>
                    <Typography variant="h6">{t('team.teams')}</Typography>
                    {teamsData.map(team => (
                        <Card key={team.competitionRegistrationId} variant="outlined">
                            <CardContent>
                                <Stack
                                    direction="row"
                                    justifyContent="space-between"
                                    alignItems="center">
                                    <Box>
                                        <Typography variant="h6">
                                            {team.competitionIdentifier} | {team.competitionName}
                                        </Typography>
                                        <Typography>
                                            {team.clubName +
                                                (team.teamName ? ' ' + team.teamName : '')}
                                        </Typography>
                                    </Box>
                                </Stack>
                                {team.participants.map(participant => (
                                    <Stack spacing={'row'}>
                                        <Typography>
                                            {participant.firstName} {participant.lastName}
                                        </Typography>
                                        {participant.currentStatus !== undefined && (
                                            <>
                                                <Chip
                                                    label={
                                                        participant.currentStatus === 'ENTRY'
                                                            ? t('team.status.in')
                                                            : t('team.status.out')
                                                    }
                                                    color={
                                                        participant.currentStatus === 'ENTRY'
                                                            ? 'success'
                                                            : 'default'
                                                    }
                                                    size="small"
                                                />
                                                {participant.lastScanAt && (
                                                    <Typography
                                                        variant="caption"
                                                        color="text.secondary">
                                                        {t('team.lastScan')}:{' '}
                                                        {new Date(
                                                            participant.lastScanAt,
                                                        ).toLocaleString()}
                                                    </Typography>
                                                )}
                                            </>
                                        )}
                                    </Stack>
                                ))}
                            </CardContent>
                        </Card>
                    ))}
                    <Box>
                        <LoadingButton
                            pending={submitting || teamsPending}
                            variant={'contained'}
                            onClick={() =>
                                handleCheckInOut(selectedParticipant.currentStatus !== 'ENTRY')
                            }>
                            {selectedParticipant.currentStatus === 'ENTRY'
                                ? t('team.checkOutText')
                                : t('team.checkInText')}
                        </LoadingButton>
                    </Box>
                </>
            )}
            )
        </Stack>
    )
}

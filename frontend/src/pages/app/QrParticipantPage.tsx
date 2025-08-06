import {Alert, Button, Stack, Typography} from '@mui/material'
import {useEffect, useState} from 'react'
import {qrEventRoute} from '@routes'
import {
    approveParticipantRequirementsForEvent,
    checkInOutTeam,
    deleteQrCode,
    getParticipantRequirementsForEvent,
    getParticipantsForEvent,
    getTeamsByParticipantQrCode,
} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {
    updateAppCatererGlobal,
    updateAppCompetitionCheckGlobal,
    updateAppEventRequirementGlobal,
    updateAppQrManagementGlobal,
} from '@authorization/privileges'
import {TeamStatusWithParticipantsDto} from '@api/types.gen.ts'
import {Cancel} from '@mui/icons-material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {QrAssignmentInfo} from '@components/qrApp/QrAssignmentInfo'
import {QrDeleteDialog} from '@components/qrApp/QrDeleteDialog'
import {TeamCheckInOut} from '@components/qrApp/TeamCheckInOut'
import {RequirementsChecklist} from '@components/qrApp/RequirementsChecklist'

const QrParticipantPage = () => {
    const {t} = useTranslation()
    const {qr, appFunction} = useAppSession()
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false)
    const [deleteQrCodeError, setDeleteQrCodeError] = useState<string | null>(null)
    const [checkedRequirements, setCheckedRequirements] = useState<string[]>([])
    const [participantRequirementsPending, setParticipantRequirementsPending] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [reloadTeams, setReloadTeams] = useState(false)
    const feedback = useFeedback()

    useEffect(() => {
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId])

    const {data: participantRequirementsData} = useFetch(
        signal => {
            setParticipantRequirementsPending(true)
            return getParticipantRequirementsForEvent({
                signal,
                path: {eventId, participantId: qr.response?.id},
            })
        },
        {
            onResponse: async ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('participantRequirement.participantRequirements'),
                        }),
                    )
                } else {
                    const {data: participantsData} = await getParticipantsForEvent({
                        path: {eventId},
                    })
                    if (participantsData) {
                        const participant = participantsData.data.find(
                            p => p.id === qr.response?.id,
                        )
                        setCheckedRequirements(
                            Array.isArray(participant?.participantRequirementsChecked)
                                ? participant.participantRequirementsChecked
                                      .map(r => r.id)
                                      .filter((id: string | undefined): id is string => !!id)
                                : [],
                        )
                    } else {
                        feedback.error(
                            t('common.load.error.multiple.short', {
                                entity: t('event.participants'),
                            }),
                        )
                    }
                }
                setParticipantRequirementsPending(false)
            },
            preCondition: () => appFunction === 'APP_EVENT_REQUIREMENT' && qr.qrCodeId !== null,
            deps: [eventId, qr],
        },
    )

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
            preCondition: () => appFunction === 'APP_COMPETITION_CHECK' && qr.qrCodeId !== null,
            deps: [eventId, qr, reloadTeams],
        },
    )

    const handleRequirementChange = async (requirementId: string, checked: boolean) => {
        if (!qr.response?.id) return
        setSubmitting(true)
        await approveParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                requirementId,
                approvedParticipants: checked ? [qr.response.id] : [],
            },
            throwOnError: true,
        })
        // Nach Änderung neu laden
        const {data: partData} = await getParticipantsForEvent({path: {eventId}})
        const participant = (partData?.data || []).find(p => p.id === qr.response?.id)
        setCheckedRequirements(
            Array.isArray(participant?.participantRequirementsChecked)
                ? participant.participantRequirementsChecked
                      .map(r => r.id)
                      .filter((id: string | undefined): id is string => !!id)
                : [],
        )
        setSubmitting(false)
    }

    const allowed =
        appFunction !== null &&
        [
            updateAppCompetitionCheckGlobal.resource,
            updateAppQrManagementGlobal.resource,
            updateAppEventRequirementGlobal.resource,
            updateAppCatererGlobal.resource,
        ].includes(appFunction)
    const canCheck = appFunction === updateAppCompetitionCheckGlobal.resource
    const canRemove = appFunction === updateAppQrManagementGlobal.resource
    const canEditRequirements = appFunction === updateAppEventRequirementGlobal.resource
    const isCaterer = appFunction === updateAppCatererGlobal.resource

    const handleDelete = async () => {
        setSubmitting(true)
        setDeleteQrCodeError(null)
        const {error} = await deleteQrCode({
            path: {qrCodeId: qr.qrCodeId!},
        })
        if (error) {
            setDeleteQrCodeError(t('qrParticipant.deleteError'))
        }
        setDialogOpen(false)
        qr.reset(eventId)
        setSubmitting(false)
    }

    const handleTeamCheckInOut = async (team: TeamStatusWithParticipantsDto, checkIn: boolean) => {
        setSubmitting(true)
        const {error} = await checkInOutTeam({
            path: {
                eventId,
                competitionRegistrationId: team.competitionRegistrationId,
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
        <Stack
            spacing={2}
            alignItems="center"
            justifyContent="center"
            sx={{width: '100%', maxWidth: 600}}>
            <Typography variant="h4" textAlign="center" gutterBottom>
                {t('qrParticipant.title')}
            </Typography>

            {/* Caterer food restriction message */}
            {isCaterer && qr.response && (
                <Alert severity="error" icon={<Cancel />} sx={{mb: 2, width: '100%'}}>
                    {t('club.participant.foodNotAllowed')}
                </Alert>
            )}

            {/* QR Code Assignment Info Box */}
            {qr.response && allowed && !isCaterer && <QrAssignmentInfo response={qr.response} />}

            {!allowed && <Alert severity="warning">{t('qrParticipant.noRight')}</Alert>}

            {canCheck && (
                <TeamCheckInOut
                    teams={teamsData ?? []}
                    loading={teamsPending}
                    teamActionLoading={submitting}
                    onCheckIn={team => handleTeamCheckInOut(team, true)}
                    onCheckOut={team => handleTeamCheckInOut(team, false)}
                />
            )}

            {canEditRequirements && (
                <RequirementsChecklist
                    requirements={participantRequirementsData?.data ?? []}
                    checkedRequirements={checkedRequirements}
                    pending={participantRequirementsPending}
                    onRequirementChange={handleRequirementChange}
                />
            )}

            {canRemove && (
                <Button
                    color="error"
                    variant="contained"
                    fullWidth
                    onClick={() => setDialogOpen(true)}>
                    {t('qrParticipant.removeAssignment')}
                </Button>
            )}

            <Button variant={'outlined'} onClick={() => qr.reset(eventId)} fullWidth>
                {t('common.back')}
            </Button>

            {/*TODO useConfirmation()*/}
            <QrDeleteDialog
                open={dialogOpen}
                onClose={() => setDialogOpen(false)}
                onDelete={handleDelete}
                loading={submitting}
                error={deleteQrCodeError}
                type="participant"
            />
        </Stack>
    )
}

export default QrParticipantPage

import {Alert, Button, IconButton, Stack, Typography} from '@mui/material'
import {useEffect, useState} from 'react'
import {qrEventRoute} from '@routes'
import {
    approveParticipantRequirementsForEvent,
    deleteQrCode,
    getParticipantRequirementsForEvent,
    getParticipantsForEvent,
} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {
    updateAppCatererGlobal,
    updateAppCompetitionCheckGlobal,
    updateAppEventRequirementGlobal,
    updateAppQrManagementGlobal,
} from '@authorization/privileges'
import {Cancel} from '@mui/icons-material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {QrAssignmentInfo} from '@components/qrApp/QrAssignmentInfo'
import {QrDeleteDialog} from '@components/qrApp/QrDeleteDialog'
import {TeamCheckInOut} from '@components/qrApp/TeamCheckInOut'
import {RequirementsChecklist} from '@components/qrApp/RequirementsChecklist'
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

const QrParticipantPage = () => {
    const {t} = useTranslation()
    const {qr, appFunction} = useAppSession()
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false)
    const [deleteQrCodeError, setDeleteQrCodeError] = useState<string | null>(null)
    const [checkedRequirements, setCheckedRequirements] = useState<string[]>([])
    const [participantRequirementsPending, setParticipantRequirementsPending] = useState(false)
    const [submitting, setSubmitting] = useState(false)
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

    return (
        <Stack
            spacing={2}
            alignItems="center"
            justifyContent="center"
            sx={{maxWidth: 600, flex: 1, justifyContent: 'start'}}>
            <Stack direction={'row'} sx={{width: 1, justifyContent: 'space-between', mb: 1}}>

                    <IconButton onClick={() => qr.reset(eventId)}>
                        <ArrowBackIcon/>
                    </IconButton>
            <Typography variant="h4" textAlign="center">
                {t('qrParticipant.title')}
            </Typography>
            </Stack>
            {/* Caterer food restriction message */}
            {isCaterer && qr.response && (
                <Alert severity="error" icon={<Cancel />} sx={{mb: 2, width: '100%'}}>
                    {t('club.participant.foodNotAllowed')}
                </Alert>
            )}

            {/* QR Code Assignment Info Box */}
            {qr.response && allowed && !isCaterer && <QrAssignmentInfo response={qr.response} />}

            {!allowed && <Alert severity="warning">{t('qrParticipant.noRight')}</Alert>}

            {canCheck && <TeamCheckInOut />}

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

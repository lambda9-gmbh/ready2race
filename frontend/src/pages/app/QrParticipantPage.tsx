import {Alert, Box, Button, Stack} from '@mui/material'
import {useEffect, useState} from 'react'
import {qrEventRoute} from '@routes'
import {
    approveParticipantRequirementsForEvent,
    deleteQrCode,
    getParticipantRequirementsForParticipant,
    getParticipantsForEventInApp,
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
import AppTopTitle from '@components/qrApp/AppTopTitle.tsx'
import {CheckedParticipantRequirement} from "@api/types.gen.ts";

const QrParticipantPage = () => {
    const {t} = useTranslation()
    const {qr, appFunction} = useAppSession()
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false)
    const [checkedRequirements, setCheckedRequirements] = useState<CheckedParticipantRequirement[]>([])
    const [participantRoles, setParticipantRoles] = useState<string[]>([])
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
            return getParticipantRequirementsForParticipant({
                signal,
                path: {eventId, participantId: qr.response?.id ?? ""},
                query: {onlyForApp: true},
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
                    const {data: participantsData} = await getParticipantsForEventInApp({
                        path: {eventId},
                    })
                    if (participantsData) {
                        const participant = participantsData.data.find(
                            p => p.id === qr.response?.id,
                        )
                        setCheckedRequirements(
                            Array.isArray(participant?.participantRequirementsChecked)
                                ? participant.participantRequirementsChecked
                                : [],
                        )
                        setParticipantRoles(participant?.namedParticipantIds ?? [])
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

    const handleRequirementChange = async (
        requirementId: string,
        checked: boolean | string,
        namedParticipantId?: string,
    ) => {
        if (!qr.response?.id) return
        setSubmitting(true)
        await approveParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                requirementId,
                approvedParticipants: checked !== false ? [{id: qr.response.id, note: typeof checked === 'string' ? checked : undefined}] : [],
                namedParticipantId: namedParticipantId,
            },
        })

        // Nach Änderung neu laden
        const {data: partData} = await getParticipantsForEventInApp({path: {eventId}})
        const participant = (partData?.data || []).find(p => p.id === qr.response?.id)
        setCheckedRequirements(
            Array.isArray(participant?.participantRequirementsChecked)
                ? participant.participantRequirementsChecked
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
        const {error} = await deleteQrCode({
            path: {qrCodeId: qr.qrCodeId!},
        })
        if (error) {
            feedback.error( t('qrParticipant.deleteError'))
        } else{
            feedback.success(t('qrAssign.deleteSuccess'))
        }
        setDialogOpen(false)
        setSubmitting(false)
        qr.reset(eventId)
    }

    return (
        <Stack
            spacing={2}
            alignItems="center"
            justifyContent="center"
            sx={{flex: 1, justifyContent: 'start'}}>
            <AppTopTitle title={t('qrParticipant.title')} />
            {isCaterer && qr.response !== undefined && qr.response !== null && (
                <Box
                    sx={{
                        display: 'flex',
                        width: 1,
                        flex: 1,
                        justifyContent: 'center',
                        alignItems: 'center',
                    }}>
                    <Alert
                        severity="error"
                        icon={<Cancel />}
                        sx={{mb: 2, width: '100%', flex: 1, p: 2}}>
                        {t('club.participant.cateringNotAllowed')}
                    </Alert>
                </Box>
            )}

            {qr.response !== undefined && qr.response !== null && allowed && !isCaterer && (
                <QrAssignmentInfo response={qr.response} />
            )}

            {!allowed && <Alert severity="warning">{t('qrParticipant.noRight')}</Alert>}

            {canCheck && <TeamCheckInOut />}

            {canEditRequirements && (
                <RequirementsChecklist
                    requirements={participantRequirementsData ?? []}
                    checkedRequirements={checkedRequirements}
                    pending={participantRequirementsPending}
                    onRequirementChange={handleRequirementChange}
                    namedParticipantIds={participantRoles}
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

            <QrDeleteDialog
                open={dialogOpen}
                onClose={() => setDialogOpen(false)}
                onDelete={handleDelete}
                loading={submitting}
                type="participant"
            />
        </Stack>
    )
}

export default QrParticipantPage

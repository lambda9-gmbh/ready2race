import {
    Alert,
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Stack,
    Typography
} from "@mui/material";
import ButtonGroup from "@mui/material/ButtonGroup";
import {useEffect, useState} from "react";
import {qrEventRoute, router} from "@routes";
import {
    approveParticipantRequirementsForEvent,
    deleteQrCode,
    getParticipantRequirementsForEvent,
    getParticipantsForEvent
} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import { updateAppQrManagementGlobal, updateAppCompetitionCheckGlobal, updateAppEventRequirementGlobal } from '@authorization/privileges';

const QrParticipantPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [requirements, setRequirements] = useState<any[]>([]);
    const [checkedRequirements, setCheckedRequirements] = useState<string[]>([]);
    const [pending, setPending] = useState(false);
    const navigate = router.navigate

    useEffect(() => {
        if (!appFunction) {
            navigate({to: "/app/function"})
            return;
        }
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId])

    useEffect(() => {
        const load = async () => {
            if (appFunction === 'APP_EVENT_REQUIREMENT' && qr.response?.id && eventId) {
                setPending(true);
                const [{data: reqData}, {data: partData}] = await Promise.all([
                    getParticipantRequirementsForEvent({path: {eventId, participantId: qr.response?.id}}),
                    getParticipantsForEvent({path: {eventId}})
                ]);
                setRequirements((reqData?.data || []).filter((r: any) => r.checkInApp));
                const participant = (partData?.data || []).find((p: any) => p.id === qr.response?.id);
                setCheckedRequirements(Array.isArray(participant?.participantRequirementsChecked)
                    ? participant.participantRequirementsChecked.map((r: any) => r.id).filter((id: string | undefined): id is string => !!id)
                    : []);
                setPending(false);
            }
        };
        load();
    }, [appFunction, qr.response?.id, eventId]);

    const handleRequirementChange = async (requirementId: string, checked: boolean) => {
        if (!qr.response?.id) return;
        setPending(true);
        await approveParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                requirementId,
                approvedParticipants: checked ? [qr.response.id] : [],
            },
        });
        // Nach Änderung neu laden
        const {data: partData} = await getParticipantsForEvent({path: {eventId}});
        const participant = (partData?.data || []).find((p: any) => p.id === qr.response?.id);
        setCheckedRequirements(Array.isArray(participant?.participantRequirementsChecked)
            ? participant.participantRequirementsChecked.map((r: any) => r.id).filter((id: string | undefined): id is string => !!id)
            : []);
        setPending(false);
    };

    const allowed = appFunction !== null && [
        updateAppCompetitionCheckGlobal.resource,
        updateAppQrManagementGlobal.resource,
        updateAppEventRequirementGlobal.resource
    ].includes(appFunction);
    const canCheck = appFunction === updateAppCompetitionCheckGlobal.resource;
    const canRemove = appFunction === updateAppQrManagementGlobal.resource;
    const canEditRequirements = appFunction === updateAppEventRequirementGlobal.resource;

    const handleDelete = async () => {
        setLoading(true);
        setError(null);
        try {
            await deleteQrCode({path: {qrCodeId: qr.qrCodeId!!}});
            setDialogOpen(false);
            qr.reset(eventId);
        } catch (e: any) {
            setError(e?.message || t('qrParticipant.deleteError'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" textAlign="center">
                {t('qrParticipant.title')}
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            {!allowed && (
                <Alert severity="warning">{t('qrParticipant.noRight') as string}</Alert>
            )}
            {canCheck && (
                <ButtonGroup disableElevation variant={"contained"} orientation={"vertical"} fullWidth>
                    <Button>{t('qrParticipant.enterArea')}</Button>
                    <Button>{t('qrParticipant.leaveArea')}</Button>
                </ButtonGroup>
            )}
            {canEditRequirements && (
                <Stack spacing={1}>
                    <Typography variant="h6">{t('participantRequirement.participantRequirements')}</Typography>
                    {pending && <Typography>{t('qrParticipant.loading') as string}</Typography>}
                    {requirements.length === 0 && !pending &&
                        <Typography>{t('qrParticipant.noRequirements') as string}</Typography>}
                    {requirements.map(req => (
                        <FormControlLabel
                            key={req.id}
                            control={
                                <Checkbox
                                    checked={checkedRequirements.includes(req.id)}
                                    onChange={e => handleRequirementChange(req.id, e.target.checked)}
                                    disabled={pending}
                                />
                            }
                            label={req.name}
                        />
                    ))}
                </Stack>
            )}
            <Button onClick={() => qr.reset(eventId)}>{t('common.back')}</Button>
            {canRemove && (
                <Button
                    color="error"
                    variant="contained"
                    fullWidth
                    onClick={() => setDialogOpen(true)}
                >
                    {t('qrParticipant.removeAssignment')}
                </Button>
            )}
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>{t('qrParticipant.removeAssignmentTitle')}</DialogTitle>
                <DialogContent>
                    <Typography>{t('qrParticipant.removeAssignmentConfirm')}</Typography>
                    {error && <Alert severity="error" sx={{mt: 2}}>{error}</Alert>}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialogOpen(false)} disabled={loading}>{t('common.cancel')}</Button>
                    <Button onClick={handleDelete} color="error" variant="contained" disabled={loading}>
                        {t('common.delete')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default QrParticipantPage
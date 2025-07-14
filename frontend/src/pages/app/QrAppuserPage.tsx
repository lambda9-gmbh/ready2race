import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography} from "@mui/material";
import {useEffect, useState} from "react";
import {qrEventRoute, router} from "@routes";
import {deleteQrCode} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";
import {useAppSession} from '@contexts/app/AppSessionContext';
import { updateAppQrManagementGlobal, updateAppCompetitionCheckGlobal, updateAppEventRequirementGlobal } from '@authorization/privileges';

const QrAppuserPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
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

    const handleDelete = async () => {
        setLoading(true);
        setError(null);
        try {
            await deleteQrCode({path: {qrCodeId: qr.qrCodeId!!}});
            setDialogOpen(false);
            qr.reset(eventId);
        } catch (e: any) {
            setError(e?.message || t('qrAppuser.deleteError'));
        } finally {
            setLoading(false);
        }
    };

    const allowed = [
        updateAppQrManagementGlobal,
        updateAppCompetitionCheckGlobal,
        updateAppEventRequirementGlobal
    ].some(priv => appFunction === priv.resource);
    const canRemove = appFunction === updateAppQrManagementGlobal.resource;

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" textAlign="center">
                {t('qrAppuser.title')}
            </Typography>
            <Typography>{t('qrAppuser.user')}: {qr.qrCodeId}</Typography>
            {!allowed && (
                <Alert severity="warning">Du hast für diesen QR-Code-Typ keine Berechtigung.</Alert>
            )}
            {allowed && <Alert severity={"error"} variant={"filled"}>{t('qrAppuser.underConstruction')}</Alert>}
            <Button onClick={() => qr.reset(eventId)}>{t('common.back')}</Button>
            {canRemove && (
                <Button
                    color="error"
                    variant="contained"
                    fullWidth
                    onClick={() => setDialogOpen(true)}
                >
                    {t('qrAppuser.removeAssignment')}
                </Button>
            )}
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>{t('qrAppuser.removeAssignmentTitle')}</DialogTitle>
                <DialogContent>
                    <Typography>{t('qrAppuser.removeAssignmentConfirm')}</Typography>
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

export default QrAppuserPage
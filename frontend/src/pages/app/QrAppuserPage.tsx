import {Alert, Button, Stack, Typography, Dialog, DialogTitle, DialogContent, DialogActions} from "@mui/material";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import {qrEventRoute} from "@routes";
import {useEffect, useState} from "react";
import {deleteQrCode} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";
import {useApp} from '@contexts/app/AppContext';

const QrAppuserPage = () => {
    const { t } = useTranslation();
    const qr = UseReceivedQr()
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const { appFunction } = useApp();

    useEffect(() => {
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr])

    const handleDelete = async () => {
        setLoading(true);
        setError(null);
        try {
            await deleteQrCode({ path: { qrCodeId: qr.qrCodeId } });
            setDialogOpen(false);
            qr.reset(eventId);
        } catch (e: any) {
            setError(e?.message || t('qrAppuser.deleteError'));
        } finally {
            setLoading(false);
        }
    };

    const allowed = appFunction === 'APP_QR_MANAGEMENT' || appFunction === 'APP_COMPETITION_CHECK';
    const canRemove = appFunction === 'APP_QR_MANAGEMENT';

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
                    {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
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
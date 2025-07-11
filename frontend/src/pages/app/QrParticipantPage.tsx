import {Button, Stack, Typography, Dialog, DialogTitle, DialogContent, DialogActions, Alert} from "@mui/material";
import ButtonGroup from "@mui/material/ButtonGroup";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import {useEffect, useState} from "react";
import {qrEventRoute} from "@routes";
import {deleteQrCode} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";

const QrParticipantPage = () => {
    const { t } = useTranslation();
    const qr = UseReceivedQr()
    const {eventId} = qrEventRoute.useParams()
    const [dialogOpen, setDialogOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

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
            <ButtonGroup disableElevation variant={"contained"} orientation={"vertical"} fullWidth>
                <Button>{t('qrParticipant.enterArea')}</Button>
                <Button>{t('qrParticipant.leaveArea')}</Button>
            </ButtonGroup>
            <Button onClick={() => qr.reset(eventId)}>{t('common.back')}</Button>
            <Button
                color="error"
                variant="contained"
                fullWidth
                onClick={() => setDialogOpen(true)}
            >
                {t('qrParticipant.removeAssignment')}
            </Button>
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>{t('qrParticipant.removeAssignmentTitle')}</DialogTitle>
                <DialogContent>
                    <Typography>{t('qrParticipant.removeAssignmentConfirm')}</Typography>
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

export default QrParticipantPage
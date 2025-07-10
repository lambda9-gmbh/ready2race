import {Button, Stack, Typography, Dialog, DialogTitle, DialogContent, DialogActions, Alert} from "@mui/material";
import ButtonGroup from "@mui/material/ButtonGroup";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import {useEffect, useState} from "react";
import {qrEventRoute} from "@routes";
import {deleteQrCode} from "@api/sdk.gen.ts";

const QrParticipantPage = () => {
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
            setError(e?.message || "Fehler beim Löschen");
        } finally {
            setLoading(false);
        }
    };

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" fontSize="2rem" textAlign="center">
                Teilnehmer QR
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            <ButtonGroup disableElevation variant={"contained"} orientation={"vertical"} sx={{ minHeight: 60, fontSize: '1.2rem', py: 2, borderRadius: 2 }} fullWidth>
                <Button>Betritt Wettkampfbereich</Button>
                <Button>Verlässt Wettkampfbereich</Button>
            </ButtonGroup>
            <Button onClick={() => qr.reset(eventId)}>Zurück</Button>
            <Button
                color="error"
                variant="contained"
                fullWidth
                onClick={() => setDialogOpen(true)}
            >
                Qr Code Zuweisung aufheben
            </Button>
            <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
                <DialogTitle>QR-Code-Zuweisung aufheben</DialogTitle>
                <DialogContent>
                    <Typography>Möchten Sie die QR-Code-Zuweisung wirklich aufheben?</Typography>
                    {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialogOpen(false)} disabled={loading}>Abbrechen</Button>
                    <Button onClick={handleDelete} color="error" variant="contained" disabled={loading}>
                        Löschen
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default QrParticipantPage
import {Alert, Button, Stack, Typography, Dialog, DialogTitle, DialogContent, DialogActions} from "@mui/material";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import {qrEventRoute} from "@routes";
import {useEffect, useState} from "react";
import {deleteQrCode} from "@api/sdk.gen.ts";

const QrAppuserPage = () => {
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
                QR-Appuser
            </Typography>
            <Typography>User: {qr.qrCodeId}</Typography>
            <Alert severity={"error"} variant={"filled"}>Site under construction</Alert>
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

export default QrAppuserPage
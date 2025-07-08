import {Alert, Button, Stack, Typography} from "@mui/material";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import {qrEventRoute} from "@routes";
import {useEffect} from "react";

const QrAppuserPage = () => {
    const qr = UseReceivedQr()
    const {eventId} = qrEventRoute.useParams()

    useEffect(() => {
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr])

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" fontSize="2rem" textAlign="center">
                QR-Appuser
            </Typography>
            <Typography>User: {qr.qrCodeId}</Typography>
            <Alert severity={"error"} variant={"filled"}>Site under construction</Alert>
            <Button onClick={() => qr.reset(eventId)}>Zurück</Button>
        </Stack>
    )
}

export default QrAppuserPage
import {Button, Stack, Typography} from "@mui/material";
import ButtonGroup from "@mui/material/ButtonGroup";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import {useEffect} from "react";
import {qrEventRoute} from "@routes";

const QrParticipantPage = () => {
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
                Teilnehmer QR
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            <ButtonGroup disableElevation variant={"contained"} orientation={"vertical"} sx={{ minHeight: 60, fontSize: '1.2rem', py: 2, borderRadius: 2 }} fullWidth>
                <Button>Betritt Wettkampfbereich</Button>
                <Button>Verlässt Wettkampfbereich</Button>
            </ButtonGroup>
            <Button onClick={() => qr.reset(eventId)}>Zurück</Button>
        </Stack>
    )
}

export default QrParticipantPage
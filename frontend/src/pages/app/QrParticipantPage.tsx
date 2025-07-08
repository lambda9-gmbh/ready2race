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
        <Stack>
            <Typography textAlign={"center"}>Participant</Typography>
            <Typography>{qr.qrCodeId}</Typography>
            <ButtonGroup disableElevation variant={"contained"} orientation={"vertical"}>
                <Button>Betritt Wettkampfbereich</Button>
                <Button>Verlässt Wettkampfbereich</Button>
            </ButtonGroup>
            <Button onClick={() => qr.reset(eventId)}>Zurück</Button>
        </Stack>
    )
}

export default QrParticipantPage
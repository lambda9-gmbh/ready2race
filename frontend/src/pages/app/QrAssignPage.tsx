import {Button, Stack, ToggleButton, ToggleButtonGroup, Typography} from "@mui/material";
import {UseReceivedQr} from "@contexts/qr/QrContext.ts";
import React, {useEffect, useState} from "react";
import {useFetch} from "@utils/hooks.ts";
import {
    getClubNames,
    getClubParticipants,
    getUsers,
    updateQrCodeAppuser,
    updateQrCodeParticipant
} from "@api/sdk.gen.ts";
import {qrEventRoute} from "@routes";

type UserTyp = "User" | "Participant"
const QrAssignPage = () => {
    const qr = UseReceivedQr()
    const [userTyp, setUserTyp] = useState<UserTyp>("User")
    const [club, setClub] = useState<string>()
    const {eventId} = qrEventRoute.useParams()

    useEffect(() => {
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr])

    const onChange = (_: React.MouseEvent<HTMLElement>, nextView: UserTyp) => {
        setUserTyp(nextView)
    }

    const clubs = useFetch(signal => getClubNames({signal, query: {eventId: eventId}}), {
        deps: [userTyp],
        preCondition: () => userTyp === "Participant"
    }).data

    const participants = useFetch(signal => getClubParticipants({signal, path: {clubId: club!!}}), {
        deps: [club],
        preCondition: () => userTyp === "Participant" && club !== undefined
    }).data

    const users = useFetch(signal => getUsers({signal}), {
        deps: [userTyp],
        preCondition: () => userTyp === "User"
    }).data

    const selectParticipant = async (id: string) => {
        await updateQrCodeParticipant({
            body: {
                qrCodeId: qr.qrCodeId,
                eventId: eventId,
                id: id
            }
        })
        qr.reset(eventId)
    }

    const selectUser = async (id: string) => {
        await updateQrCodeAppuser({
            body: {
                qrCodeId: qr.qrCodeId,
                eventId: eventId,
                id: id
            }
        })
        qr.reset(eventId)
    }

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" fontSize="2rem" textAlign="center">
                QR-Code zuweisen
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            <ToggleButtonGroup value={userTyp} exclusive onChange={onChange}>
                <ToggleButton value={"Participant"}>Participant</ToggleButton>
                <ToggleButton value={"User"}>User</ToggleButton>
            </ToggleButtonGroup>
            {clubs && userTyp === "Participant" && <Stack>
                <Typography>Clubs</Typography>
                <ToggleButtonGroup exclusive orientation={"vertical"} value={club}
                                   onChange={(_, club: string) => setClub(club)}>
                    {clubs?.data.map(club => <ToggleButton value={club.id}
                                                           key={club.id}>{club.name}</ToggleButton>) ??
                        <Typography>No Data</Typography>}
                </ToggleButtonGroup>
            </Stack>}
            {participants && userTyp === "Participant" && <Stack>
                <Typography>Participants</Typography>
                {participants?.data.map(participant =>
                        <Button onClick={() => selectParticipant(participant.id)}
                                key={participant.id} sx={{ minHeight: 60, fontSize: '1.2rem', py: 2, borderRadius: 2 }} fullWidth>
                            {participant.firstname} {participant.lastname}
                        </Button>) ??
                    <Typography>No Data</Typography>}
            </Stack>}
            {users && userTyp === "User" && <Stack>
                <Typography>User</Typography>
                {users?.data.map(user => <Button onClick={() => selectUser(user.id)}
                                                 key={user.id} sx={{ minHeight: 60, fontSize: '1.2rem', py: 2, borderRadius: 2 }} fullWidth>
                    {user.firstname} {user.lastname}
                </Button>) ??
                    <Typography>No Data</Typography>}
            </Stack>}

            <Button onClick={() => qr.reset(eventId)} sx={{ minHeight: 60, fontSize: '1.2rem', py: 2, borderRadius: 2 }} fullWidth>Zurück</Button>

        </Stack>
    )
}

export default QrAssignPage
import {Button, Stack, ToggleButton, ToggleButtonGroup, Typography, Alert} from "@mui/material";
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
import {useTranslation} from "react-i18next";
import {useApp} from '@contexts/app/AppContext';

type UserTyp = "User" | "Participant"
const QrAssignPage = () => {
    const { t } = useTranslation();
    const qr = UseReceivedQr()
    const [userTyp, setUserTyp] = useState<UserTyp>("User")
    const [club, setClub] = useState<string>()
    const {eventId} = qrEventRoute.useParams()
    const { appFunction } = useApp();

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

    // Nur Nutzer mit APP_QR_MANAGEMENT dürfen zuordnen
    const canAssign = appFunction === 'APP_QR_MANAGEMENT';

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" textAlign="center">
                {t('qrAssign.title')}
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            {!canAssign && (
                <Alert severity="warning">Du hast keine Berechtigung, diesen QR-Code zuzuordnen.</Alert>
            )}
            {canAssign && <>
                <ToggleButtonGroup value={userTyp} exclusive onChange={onChange}>
                    <ToggleButton value={"Participant"}>{t('qrAssign.participant')}</ToggleButton>
                    <ToggleButton value={"User"}>{t('qrAssign.user')}</ToggleButton>
                </ToggleButtonGroup>
                {clubs && userTyp === "Participant" && <Stack>
                    <Typography>{t('qrAssign.clubs')}</Typography>
                    <ToggleButtonGroup exclusive orientation={"vertical"} value={club}
                                       onChange={(_, club: string) => setClub(club)}>
                        {clubs?.data.map(club => <ToggleButton value={club.id}
                                                               key={club.id}>{club.name}</ToggleButton>) ??
                            <Typography>{t('qrAssign.noData')}</Typography>}
                    </ToggleButtonGroup>
                </Stack>}
                {participants && userTyp === "Participant" && <Stack>
                    <Typography>{t('qrAssign.participants')}</Typography>
                    {participants?.data.map(participant =>
                            <Button onClick={() => selectParticipant(participant.id)}
                                    key={participant.id} fullWidth>
                                {participant.firstname} {participant.lastname}
                            </Button>) ??
                        <Typography>{t('qrAssign.noData')}</Typography>}
                </Stack>}
                {users && userTyp === "User" && <Stack>
                    <Typography>{t('qrAssign.users')}</Typography>
                    {users?.data.map(user => <Button onClick={() => selectUser(user.id)}
                                                     key={user.id} fullWidth>
                        {user.firstname} {user.lastname}
                    </Button>) ??
                        <Typography>{t('qrAssign.noData')}</Typography>}
                </Stack>}
            </>}
            <Button onClick={() => qr.reset(eventId)} fullWidth>{t('common.back')}</Button>
        </Stack>
    )
}

export default QrAssignPage
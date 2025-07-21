import {Button, Stack, ToggleButton, ToggleButtonGroup, Typography, Alert, Box, Paper} from "@mui/material";
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
import {useAppSession} from '@contexts/app/AppSessionContext';
import QrNimiqScanner from "@components/qrApp/QrNimiqScanner.tsx";
import {CheckQrCodeResponse} from "@api/types.gen.ts";

type UserTyp = "User" | "Participant" | "SystemUser"
const QrAssignPage = () => {
    const { t } = useTranslation();
    const { qr, appFunction } = useAppSession();
    const [userTyp, setUserTyp] = useState<UserTyp>("User")
    const [club, setClub] = useState<string>()
    const [scanningSystemUser, setScanningSystemUser] = useState(false)
    const [systemUserId, setSystemUserId] = useState<string | null>(null)
    const {eventId} = qrEventRoute.useParams()

    useEffect(() => {
        if (!appFunction) {
            window.location.href = '/app/function';
            return;
        }
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId])

    const onChange = (_: React.MouseEvent<HTMLElement>, nextView: UserTyp) => {
        setUserTyp(nextView)
        if (nextView === "SystemUser") {
            setScanningSystemUser(true)
            setSystemUserId(null)
        } else {
            setScanningSystemUser(false)
            setSystemUserId(null)
        }
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
                qrCodeId: qr.qrCodeId!,
                eventId: eventId,
                id: id
            }
        })
        qr.reset(eventId)
    }

    const selectUser = async (id: string) => {
        await updateQrCodeAppuser({
            body: {
                qrCodeId: qr.qrCodeId!,
                eventId: eventId,
                id: id
            }
        })
        qr.reset(eventId)
    }

    const handleSystemUserScan = (qrCodeId: string, _response: CheckQrCodeResponse | null) => {
        try {
            const data = JSON.parse(qrCodeId)
            if (data.appUserId) {
                setSystemUserId(data.appUserId)
                setScanningSystemUser(false)
                selectUser(data.appUserId)
            } else {
                alert(t('qrAssign.invalidUserQr'))
            }
        } catch {
            alert(t('qrAssign.invalidQrFormat'))
        }
    }

    const canAssign = appFunction === 'APP_QR_MANAGEMENT';

    return (
        <Stack spacing={2} p={2} alignItems="center" justifyContent="center">
            <Typography variant="h2" textAlign="center">
                {t('qrAssign.title')}
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            {!canAssign && (
                <Alert severity="warning">{t('qrAssign.noPermission')}</Alert>
            )}
            {canAssign && <>
                <ToggleButtonGroup value={userTyp} exclusive onChange={onChange}>
                    <ToggleButton value={"Participant"}>{t('qrAssign.participant')}</ToggleButton>
                    <ToggleButton value={"User"}>{t('qrAssign.user')}</ToggleButton>
                    <ToggleButton value={"SystemUser"}>{t('qrAssign.scanSystemUser')}</ToggleButton>
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
                {userTyp === "SystemUser" && scanningSystemUser && (
                    <Box sx={{ width: '100%', mt: 2 }}>
                        <Paper elevation={3} sx={{ p: 2 }}>
                            <Typography variant="h6" gutterBottom>
                                {t('qrAssign.scanUserQr')}
                            </Typography>
                            <QrNimiqScanner callback={handleSystemUserScan} />
                            <Button 
                                onClick={() => setScanningSystemUser(false)} 
                                fullWidth 
                                sx={{ mt: 2 }}
                            >
                                {t('common.cancel')}
                            </Button>
                        </Paper>
                    </Box>
                )}
                {userTyp === "SystemUser" && systemUserId && (
                    <Alert severity="success">
                        {t('qrAssign.userAssigned', { userId: systemUserId })}
                    </Alert>
                )}
            </>}
            <Button onClick={() => qr.reset(eventId)} fullWidth>{t('common.back')}</Button>
        </Stack>
    )
}

export default QrAssignPage
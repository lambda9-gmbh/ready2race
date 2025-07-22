import {Alert, Box, Button, FormControlLabel, Radio, RadioGroup, Stack, Typography} from "@mui/material";
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

type UserTyp = "User" | "Participant" | "SystemUser"
const QrAssignPage = () => {
    const {t} = useTranslation();
    const {qr, appFunction} = useAppSession();
    const [userTyp, setUserTyp] = useState<UserTyp>("User")
    const [club, setClub] = useState<string>()
    const [scanningSystemUser, setScanningSystemUser] = useState(false)
    const [successMessage, setSuccessMessage] = useState<string | null>(null)
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

    const onChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const nextView = event.target.value as UserTyp;
        setUserTyp(nextView)
        if (nextView === "SystemUser") {
            setScanningSystemUser(true)
        } else {
            setScanningSystemUser(false)
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
        setSuccessMessage(t('qrAssign.successParticipant'))
        setTimeout(() => {
            qr.reset(eventId)
        }, 2000)
    }

    const selectUser = async (id: string) => {
        await updateQrCodeAppuser({
            body: {
                qrCodeId: qr.qrCodeId!,
                eventId: eventId,
                id: id
            }
        })
        setSuccessMessage(t('qrAssign.successUser'))
        setTimeout(() => {
            qr.reset(eventId)
        }, 2000)
    }

    const handleSystemUserScan = async (qrCodeContent: string) => {
        try {
            const data = JSON.parse(qrCodeContent)
            console.log(data)
            if (data.appUserId) {
                setScanningSystemUser(false)
                await selectUser(data.appUserId)
            } else if (data.participantId) {
                setScanningSystemUser(false)
                await selectParticipant(data.participantId)
            } else {
                alert(t('qrAssign.invalidUserQr'))
            }
        } catch {
            alert(t('qrAssign.invalidQrFormat'))
        }
    }

    const canAssign = appFunction === 'APP_QR_MANAGEMENT';

    return (
        <Stack
            spacing={2}
            alignItems="center"
            justifyContent="center"
            sx={{width: '100%', maxWidth: 600}}
        >
            <Typography variant="h4" textAlign="center" gutterBottom>
                {t('qrAssign.title')}
            </Typography>
            <Typography>{qr.qrCodeId}</Typography>
            {successMessage && (
                <Alert severity="success" sx={{width: '100%'}}>
                    {successMessage}
                </Alert>
            )}
            {!canAssign && (
                <Alert severity="warning">{t('qrAssign.noPermission')}</Alert>
            )}
            {canAssign && <>
                <RadioGroup value={userTyp} onChange={onChange} sx={{width: '100%'}}>
                    <FormControlLabel value="Participant" control={<Radio/>} label={t('qrAssign.participant')}
                                      sx={{width: '100%'}}/>
                    <FormControlLabel value="User" control={<Radio/>} label={t('qrAssign.user')} sx={{width: '100%'}}/>
                    <FormControlLabel value="SystemUser" control={<Radio/>} label={t('qrAssign.scanSystemUser')}
                                      sx={{width: '100%'}}/>
                </RadioGroup>

                {clubs && userTyp === "Participant" && (
                    <Stack sx={{ width: '100%' }} spacing={2}>
                        <Typography>{t('qrAssign.clubs')}</Typography>
                        <RadioGroup value={club || ''} onChange={(e) => setClub(e.target.value)} sx={{width: '100%'}}>
                            {clubs?.data.map(club =>
                                <FormControlLabel
                                    key={club.id}
                                    value={club.id}
                                    control={<Radio/>}
                                    label={club.name}
                                    sx={{width: '100%'}}
                                />
                            ) ?? <Typography>{t('qrAssign.noData')}</Typography>}
                        </RadioGroup>
                    </Stack>)}

                {participants && userTyp === "Participant" && (
                    <Stack sx={{ width: '100%' }} spacing={2}>
                        <Typography>{t('qrAssign.participants')}</Typography>
                        {participants?.data.map(participant =>
                                <Button onClick={() => selectParticipant(participant.id)}
                                        key={participant.id} fullWidth
                                        variant="contained"
                                        color="primary">
                                    {participant.firstname} {participant.lastname}
                                </Button>) ??
                            <Typography>{t('qrAssign.noData')}</Typography>}
                    </Stack>)}

                {users && userTyp === "User" && (
                    <Stack sx={{ width: '100%' }} spacing={2}>
                        <Typography>{t('qrAssign.users')}</Typography>
                        {users?.data.map(user => <Button onClick={() => selectUser(user.id)}
                                                         key={user.id} fullWidth
                                                         variant="contained"
                                                         color="primary">
                                {user.firstname} {user.lastname}
                            </Button>) ??
                            <Typography>{t('qrAssign.noData')}</Typography>}
                    </Stack>)}

                {userTyp === "SystemUser" && scanningSystemUser && (
                    <Box sx={{width: '100%', mt: 2}}>
                        <Typography variant="h6" gutterBottom>
                            {t('qrAssign.scanUserQr')}
                        </Typography>
                        <QrNimiqScanner callback={handleSystemUserScan}/>
                    </Box>
                )}
            </>}
            <Button variant={'outlined'} onClick={() => qr.reset(eventId)} fullWidth>{t('common.back')}</Button>
        </Stack>
    )
}

export default QrAssignPage
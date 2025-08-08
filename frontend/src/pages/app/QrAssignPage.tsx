import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from '@mui/material'
import React, {useEffect, useState} from 'react'
import {updateQrCodeAppuser, updateQrCodeParticipant} from '@api/sdk.gen.ts'
import {qrEventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import PersonIcon from '@mui/icons-material/Person'
import ParticipantAssignment from '@components/qrApp/assign/ParticipantAssignment'
import UserAssignment from '@components/qrApp/assign/UserAssignment'
import SystemUserScanner from '@components/qrApp/assign/SystemUserScanner'
import {useFeedback} from "@utils/hooks.ts";

type UserTyp = 'Participant' | 'User' | 'SystemUser'

interface ConfirmationData {
    id: string
    name: string
    type: 'participant' | 'user'
    additionalInfo?: string[]
}

const QrAssignPage = () => {
    const {t} = useTranslation()
    const {qr, appFunction} = useAppSession()
    const [userTyp, setUserTyp] = useState<UserTyp>('Participant')
    const [scanningSystemUser, setScanningSystemUser] = useState(false)
    const [confirmationOpen, setConfirmationOpen] = useState(false)
    const [selectedPerson, setSelectedPerson] = useState<ConfirmationData | null>(null)
    const {eventId} = qrEventRoute.useParams()
    const feedback = useFeedback()

    useEffect(() => {
        if (!qr.received) {
            qr.reset(eventId)
        }
    }, [qr, appFunction, eventId])

    const handleUserTypChange = (
        _event: React.MouseEvent<HTMLElement>,
        newUserTyp: UserTyp | null,
    ) => {
        if (newUserTyp !== null && newUserTyp !== 'SystemUser') {
            setUserTyp(newUserTyp)
            setScanningSystemUser(false)
        }
    }

    const handleScanSystemUser = () => {
        setScanningSystemUser(true)
        setUserTyp(null as any) // Clear toggle group selection
    }

    const handleParticipantClick = (
        participant: {
            participantId: string
            firstname: string
            lastname: string
            namedParticipant: string
            qrCodeValue?: string | null
        },
        competitionName: string,
    ) => {
        setSelectedPerson({
            id: participant.participantId,
            name: `${participant.firstname} ${participant.lastname}`,
            type: 'participant',
            additionalInfo: [competitionName, participant.namedParticipant],
        })
        setConfirmationOpen(true)
    }

    const handleUserClick = (user: {id: string; firstname: string; lastname: string}) => {
        setSelectedPerson({
            id: user.id,
            name: `${user.firstname} ${user.lastname}`,
            type: 'user',
        })
        setConfirmationOpen(true)
    }

    const handleConfirmSelection = async () => {
        if (!selectedPerson) return

        if (selectedPerson.type === 'participant') {
            await updateQrCodeParticipant({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: selectedPerson.id,
                },
            })
        } else {
            await updateQrCodeAppuser({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: selectedPerson.id,
                },
            })
        }

        setConfirmationOpen(false)
        qr.reset(eventId)
    }

    const handleCloseConfirmation = () => {
        setConfirmationOpen(false)
        setSelectedPerson(null)
    }

    const handleSystemUserScan = async (qrCodeContent: string) => {

        const data = JSON.parse(qrCodeContent)
        setScanningSystemUser(false)
        if (data.appUserId) {
            setScanningSystemUser(false)
            const {error} = await updateQrCodeAppuser({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: data.appUserId,
                },
            })
            if(error){
                feedback.error(t('qrAssign.invalidQrFormat'))
            } else{
                feedback.success(t('qrAssign.success'))
            }
            qr.reset(eventId)
        } else if (data.participantId) {
            setScanningSystemUser(false)
            const {error} = await updateQrCodeParticipant({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: data.participantId,
                },
            })
            qr.reset(eventId)
            if(error){
                feedback.error(t('qrAssign.invalidQrFormat'))
            } else{
                feedback.success(t('qrAssign.success'))
            }
        } else {
            feedback.error(t('qrAssign.invalidUserQr'))
        }
        qr.reset(eventId)
    }

    const canAssign = appFunction === 'APP_QR_MANAGEMENT'

    return (
        <Stack
            spacing={3}
            alignItems="center"
            justifyContent="flex-start"
            sx={{width: '100%', maxWidth: 600, px: 2, py: 3}}>
            <Box sx={{width: '100%', textAlign: 'center'}}>
                <Typography variant="h4" gutterBottom>
                    {t('qrAssign.title')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                    {qr.qrCodeId}
                </Typography>
            </Box>

            {!canAssign && (
                <Alert severity="warning" sx={{width: '100%'}}>
                    {t('qrAssign.noPermission')}
                </Alert>
            )}

            {canAssign && (
                <>
                    <Stack spacing={2} sx={{width: '100%'}}>
                        <ToggleButtonGroup
                            value={scanningSystemUser ? null : userTyp}
                            exclusive
                            onChange={handleUserTypChange}
                            aria-label="user type selection"
                            sx={{width: '100%'}}>
                            <ToggleButton value="Participant" sx={{flex: 1}}>
                                {t('qrAssign.participant')}
                            </ToggleButton>
                            <ToggleButton value="User" sx={{flex: 1}}>
                                {t('qrAssign.user')}
                            </ToggleButton>
                        </ToggleButtonGroup>

                        <Button
                            variant={'outlined'}
                            fullWidth
                            onClick={handleScanSystemUser}
                            sx={{py: 1.5}}>
                            {t('qrAssign.scanSystemUser')}
                        </Button>
                    </Stack>

                    {userTyp === 'Participant' && !scanningSystemUser && (
                        <ParticipantAssignment
                            eventId={eventId}
                            onSelectParticipant={handleParticipantClick}
                        />
                    )}

                    {userTyp === 'User' && !scanningSystemUser && (
                        <UserAssignment onSelectUser={handleUserClick} />
                    )}
                </>
            )}

            {!scanningSystemUser && (
                <Button variant="outlined" onClick={() => qr.reset(eventId)} fullWidth sx={{mt: 2}}>
                    {t('common.back')}
                </Button>
            )}

            {canAssign && scanningSystemUser && (
                <SystemUserScanner
                    onScan={handleSystemUserScan}
                    onBack={() => setScanningSystemUser(false)}
                />
            )}

            <Dialog
                open={confirmationOpen}
                onClose={handleCloseConfirmation}
                maxWidth="sm"
                fullWidth>
                <DialogTitle>{t('common.confirmation.title')}</DialogTitle>
                <DialogContent>
                    <Stack spacing={2} sx={{pt: 2}}>
                        <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                            <PersonIcon sx={{fontSize: 48, color: 'text.secondary'}} />
                            <Box>
                                <Typography variant="h6">{selectedPerson?.name}</Typography>
                                {selectedPerson?.additionalInfo &&
                                    selectedPerson.additionalInfo.map((info, index) => (
                                        <Typography
                                            key={index}
                                            variant="body2"
                                            color="text.secondary">
                                            {info}
                                        </Typography>
                                    ))}
                            </Box>
                        </Box>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleCloseConfirmation} variant="outlined">
                        {t('common.back')}
                    </Button>
                    <Button onClick={handleConfirmSelection} variant="contained" color="primary">
                        {t('common.ok')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Stack>
    )
}

export default QrAssignPage

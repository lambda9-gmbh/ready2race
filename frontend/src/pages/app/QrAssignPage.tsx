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
import React, {useEffect, useRef, useState} from 'react'
import {updateQrCodeAppuser, updateQrCodeParticipant} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import PersonIcon from '@mui/icons-material/Person'
import ParticipantAssignment from '@components/qrApp/assign/ParticipantAssignment'
import UserAssignment from '@components/qrApp/assign/UserAssignment'
import SystemUserScanner from '@components/qrApp/assign/SystemUserScanner'
import {useFeedback} from '@utils/hooks.ts'
import AppTopTitle from '@components/qrApp/AppTopTitle.tsx'
import {ParticipantQrAssignmentDto} from '@api/types.gen.ts'

type UserTyp = 'Participant' | 'User'

interface ConfirmationData {
    id: string
    name: string
    type: 'participant' | 'user'
    additionalInfo?: string[]
}

const QrAssignPage = () => {
    const {t} = useTranslation()
    const {qr, appFunction, navigateTo, eventId} = useAppSession()
    const [userTyp, setUserTyp] = useState<UserTyp | null>('Participant')
    const [scanningSystemUser, setScanningSystemUser] = useState(false)
    const [confirmationOpen, setConfirmationOpen] = useState(false)
    const [selectedPerson, setSelectedPerson] = useState<ConfirmationData | null>(null)
    const feedback = useFeedback()
    const qrCheckPending = useRef<boolean>(false)

    useEffect(() => {
        if (!qr.received) {
            navigateTo('APP_Scanner')
        }
    }, [qr, navigateTo])

    const handleUserTypChange = (
        _event: React.MouseEvent<HTMLElement>,
        newUserTyp: UserTyp | null,
    ) => {
        if (newUserTyp !== null) {
            setUserTyp(newUserTyp)
            setScanningSystemUser(false)
        }
    }

    const handleScanSystemUser = () => {
        setScanningSystemUser(true)
        setUserTyp(null) // Clear toggle group selection
    }

    const handleParticipantClick = (
        participant: ParticipantQrAssignmentDto,
        competitionName: string,
    ) => {
        setSelectedPerson({
            id: participant.participantId,
            name: `${participant.firstname} ${participant.lastname}`,
            type: 'participant',
            additionalInfo: [competitionName, participant.namedParticipantName],
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
            const {error} = await updateQrCodeParticipant({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: selectedPerson.id,
                },
            })
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.success(t('qrAssign.successParticipant'))
            }
        } else {
            const {error} = await updateQrCodeAppuser({
                body: {
                    qrCodeId: qr.qrCodeId!,
                    eventId: eventId,
                    id: selectedPerson.id,
                },
            })
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.success(t('qrAssign.successUser'))
            }
        }

        setConfirmationOpen(false)
        navigateTo('APP_Scanner')
    }

    const handleCloseConfirmation = () => {
        setConfirmationOpen(false)
        setSelectedPerson(null)
    }

    const handleSystemUserScan = async (qrCodeContent: string) => {
        if (!qrCheckPending.current) {
            qrCheckPending.current = true
            try {
                const data = JSON.parse(qrCodeContent)

                if (data.appUserId) {
                    setScanningSystemUser(false)
                    const {error} = await updateQrCodeAppuser({
                        body: {
                            qrCodeId: qr.qrCodeId!,
                            eventId: eventId,
                            id: data.appUserId,
                        },
                    })
                    if (error) {
                        setScanningSystemUser(true)
                        feedback.error(t('qrAssign.notAssigned'))
                    } else {
                        navigateTo('APP_Scanner')

                        feedback.success(t('qrAssign.success'))
                    }
                } else if (data.participantId) {
                    setScanningSystemUser(false)
                    const {error} = await updateQrCodeParticipant({
                        body: {
                            qrCodeId: qr.qrCodeId!,
                            eventId: eventId,
                            id: data.participantId,
                        },
                    })
                    if (error) {
                        setScanningSystemUser(true)
                        feedback.error(t('qrAssign.notAssigned'))
                    } else {
                        navigateTo('APP_Scanner')
                        feedback.success(t('qrAssign.success'))
                    }
                } else {
                    feedback.error(t('qrAssign.invalidUserQr'))
                }
            } catch {
                feedback.error(t('qrAssign.invalidUserQr'))
            }

            qrCheckPending.current = false
        }
    }

    const canAssign = appFunction === 'APP_QR_MANAGEMENT'

    return (
        <Stack
            spacing={3}
            alignItems="center"
            justifyContent="flex-start"
            sx={{width: '100%', maxWidth: 600, px: 2, py: 3}}>
            <Box sx={{width: '100%', textAlign: 'center'}}>
                <AppTopTitle title={t('qrAssign.title')} disableBackButton={scanningSystemUser} />
                <Typography variant="body2" color="text.secondary">
                    {qr.qrCodeId}
                </Typography>
            </Box>

            {!canAssign && (
                <Alert severity="warning" sx={{width: '100%'}}>
                    {t('qrAssign.notAssigned')}
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
                        <UserAssignment eventId={eventId} onSelectUser={handleUserClick} />
                    )}
                </>
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

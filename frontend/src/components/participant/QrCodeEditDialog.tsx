import {ParticipantForEventDto} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import {TextFieldElement, useForm} from "react-hook-form-mui";
import {useEffect, useState} from "react";
import {updateQrCodeParticipant} from "@api/sdk.gen.ts";
import EntityDialog from "@components/EntityDialog.tsx";
import {Typography, Button, Box} from "@mui/material";
import {UserQrCodeDisplay} from "@components/qrcode/UserQrCodeDisplay.tsx";

export const QrCodeEditDialog = ({
                                     dialogIsOpen,
                                     closeDialog,
                                     reloadData,
                                     entity,
                                     onOpen,
                                     eventId,
                                 }: {
    dialogIsOpen: boolean
    closeDialog: () => void
    reloadData: () => void
    entity: ParticipantForEventDto | null
    onOpen: () => void
    eventId: string
}) => {
    const {t} = useTranslation()
    const [showUserQr, setShowUserQr] = useState(false)
    const formContext = useForm<{ qrCodeId: string }>({
        defaultValues: {qrCodeId: entity?.qrCodeId || ''},
        values: {qrCodeId: entity?.qrCodeId || ''},
    })

    useEffect(() => {
        formContext.reset({qrCodeId: entity?.qrCodeId || ''})
        setShowUserQr(false)
    }, [entity])

    const editAction = async (formData: { qrCodeId: string }, entity: ParticipantForEventDto) => {
        return await updateQrCodeParticipant({
            body: {
                id: entity.id,
                qrCodeId: formData.qrCodeId,
                eventId: eventId,
            },
        })
    }

    return (
        <EntityDialog
            entityName={t('club.participant.title')}
            dialogIsOpen={dialogIsOpen}
            closeDialog={closeDialog}
            reloadData={reloadData}
            entity={entity ?? undefined}
            formContext={formContext}
            onOpen={onOpen}
            editAction={editAction}
            title={t('club.participant.qrCodeEdit')}
            disableSave={false}
        >
            <TextFieldElement
                name="qrCodeId"
                label={t('club.participant.qrCodeId')}
                fullWidth
            />
            <Typography variant="body2" sx={{mt: 2}}>
                {t('club.participant.qrCodeEditDescription')}
            </Typography>
            
            <Box sx={{ mt: 3, mb: 2 }}>
                <Button 
                    variant="outlined" 
                    onClick={() => setShowUserQr(!showUserQr)}
                    fullWidth
                >
                    {showUserQr ? t('common.hide') : t('common.show')} {t('qrCode.qrCode')}
                </Button>
            </Box>
            
            {showUserQr && entity && (
                <Box sx={{ mt: 2 }}>
                    <UserQrCodeDisplay 
                        qrCodeData={JSON.stringify({ participantId: entity.id })}
                        label={t('club.participant.qrCode')}
                        displayId={`${t('club.participant.title')}: ${entity.firstname} ${entity.lastname}`}
                    />
                </Box>
            )}
        </EntityDialog>
    )
}
import {AppUserWithQrCodeDto} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import {TextFieldElement, useForm} from "react-hook-form-mui";
import {useEffect} from "react";
import {updateQrCodeAppuser} from "@api/sdk.gen.ts";
import EntityDialog from "@components/EntityDialog.tsx";
import {Typography} from "@mui/material";

export const AppUserQrCodeEditDialog = ({
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
    entity: AppUserWithQrCodeDto | null
    onOpen: () => void
    eventId: string
}) => {
    const {t} = useTranslation()
    const formContext = useForm<{ qrCodeId: string }>({
        defaultValues: {qrCodeId: entity?.qrCodeId || ''},
        values: {qrCodeId: entity?.qrCodeId || ''},
    })

    useEffect(() => {
        formContext.reset({qrCodeId: entity?.qrCodeId || ''})
    }, [entity])

    const editAction = async (formData: { qrCodeId: string }, entity: AppUserWithQrCodeDto) => {
        return await updateQrCodeAppuser({
            body: {
                id: entity.id,
                qrCodeId: formData.qrCodeId,
                eventId: eventId,
            },
        })
    }

    return (
        <EntityDialog
            entityName={t('qrAppuser.title')}
            dialogIsOpen={dialogIsOpen}
            closeDialog={closeDialog}
            reloadData={reloadData}
            entity={entity ?? undefined}
            formContext={formContext}
            onOpen={onOpen}
            editAction={editAction}
            title={t('qrCode.edit')}
            disableSave={false}
        >
            <TextFieldElement
                name="qrCodeId"
                label={t('user.qrCodeId')}
                fullWidth
            />
            <Typography variant="body2" sx={{mt: 2}}>
                {t('club.participant.qrCodeEditDescription')}
            </Typography>
        </EntityDialog>
    )
}
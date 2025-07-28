import {AppUserDto} from "@api/types.gen.ts";
import {Dialog, DialogContent, DialogTitle, IconButton, Box} from "@mui/material";
import {Close} from "@mui/icons-material";
import {UserQrCodeDisplay} from "@components/qrcode/UserQrCodeDisplay.tsx";
import {useTranslation} from "react-i18next";

export const QrCodeDisplayDialog = ({
    dialogIsOpen,
    closeDialog,
    entity,
}: {
    dialogIsOpen: boolean
    closeDialog: () => void
    entity: AppUserDto | null
}) => {
    const {t} = useTranslation()
    
    if (!entity) return null
    
    const qrCodeData = JSON.stringify({ appUserId: entity.id })

    return (
        <Dialog
            open={dialogIsOpen}
            onClose={closeDialog}
            maxWidth="sm"
            fullWidth
        >
            <DialogTitle>
                {t('qrCode.qrCode')}
                <IconButton
                    aria-label="close"
                    onClick={closeDialog}
                    sx={{
                        position: 'absolute',
                        right: 8,
                        top: 8,
                    }}
                >
                    <Close />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, py: 2 }}>
                    <UserQrCodeDisplay 
                        qrCodeData={qrCodeData}
                        displayId={`${t('user.user')}: ${entity.firstname} ${entity.lastname}`}
                    />
                </Box>
            </DialogContent>
        </Dialog>
    )
}
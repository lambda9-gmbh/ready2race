import {Button, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {useTranslation} from 'react-i18next'
import BaseDialog from '@components/BaseDialog.tsx'

export function EventRegistrationMessageDialog(props: {
    open: boolean
    onClose: () => void
    content: string | undefined
}) {
    const {t} = useTranslation()
    return (
        <BaseDialog maxWidth={'md'} open={props.open} onClose={props.onClose}>
            <DialogTitle>{t('event.registration.message')}</DialogTitle>
            <DialogContent dividers={true} sx={{minHeight: 200}}>
                {props.content}
            </DialogContent>
            <DialogActions>
                <Button onClick={props.onClose}>{t('common.close')}</Button>
            </DialogActions>
        </BaseDialog>
    )
}

import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {useTranslation} from 'react-i18next'

export function EventRegistrationMessageDialog(props: {
    open: boolean
    onClose: () => void
    content: string | undefined
}) {
    const {t} = useTranslation()
    return (
        <Dialog
            fullWidth={true}
            maxWidth={'md'}
            scroll={'paper'}
            open={props.open}
            onClose={props.onClose}
            className={'ready2race'}>
            <DialogTitle>{t('event.registration.message')}</DialogTitle>
            <DialogContent dividers={true} sx={{minHeight: 200}}>
                {props.content}
            </DialogContent>
            <DialogActions>
                <Button onClick={props.onClose}>{t('common.close')}</Button>
            </DialogActions>
        </Dialog>
    )
}

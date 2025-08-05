import {Dialog, DialogProps, IconButton} from '@mui/material'
import {Close} from '@mui/icons-material'

export type BaseDialogProps = Omit<DialogProps, 'onClose'> & {
    onClose: () => void
}

const BaseDialog = ({className, onClose, children, ...props}: BaseDialogProps) => {
    return (
        <Dialog
            scroll={'paper'}
            fullWidth
            {...props}
            className={'ready2race' + (className ? ` ${className}` : '')}
            onClose={(_, reason) => {
                if (reason === 'escapeKeyDown') {
                    onClose()
                }
            }}>
            <IconButton
                onClick={onClose}
                sx={{
                    position: 'absolute',
                    right: 6,
                    top: 6,
                }}>
                <Close />
            </IconButton>
            {children}
        </Dialog>
    )
}

export default BaseDialog

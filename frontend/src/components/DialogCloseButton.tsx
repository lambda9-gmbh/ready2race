import {IconButton} from '@mui/material'
import {Close} from '@mui/icons-material'

type Props = {
    onClose: () => void
}

const DialogCloseButton = (props: Props) => {
    return (
        <IconButton
            onClick={props.onClose}
            sx={{
                position: 'absolute',
                right: 8,
                top: 8,
            }}>
            <Close />
        </IconButton>
    )
}

export default DialogCloseButton

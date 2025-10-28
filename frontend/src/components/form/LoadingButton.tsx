import {Button, ButtonProps, CircularProgress} from '@mui/material'
import {PropsWithChildren} from 'react'

type Props = ButtonProps & {
    pending: boolean
}
//todo: KERN: progress not for really short timespan?
const LoadingButton = ({children, pending, sx, ...props}: PropsWithChildren<Props>) => {
    return (
        <Button {...props} disabled={pending || props.disabled} sx={{display: 'flex', ...sx}}>
            {pending && <CircularProgress size={24} sx={{position: 'absolute'}} />}
            {children}
        </Button>
    )
}

export default LoadingButton

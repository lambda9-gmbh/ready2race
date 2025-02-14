import {Button, ButtonProps, CircularProgress} from '@mui/material'

type Props = ButtonProps & {
    label: string
    pending: boolean
}
//todo: KERN: progress not for really short timespan?
const LoadingButton = ({label, pending, ...rest}: Props) => {
    return (
        <Button {...rest} disabled={pending || rest.disabled} sx={{display: 'flex'}}>
            {pending && <CircularProgress size={24} sx={{position: 'absolute'}} />}
            {label}
        </Button>
    )
}

export default LoadingButton

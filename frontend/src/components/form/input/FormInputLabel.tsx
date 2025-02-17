import {Stack, Typography} from '@mui/material'
import {ReactNode} from 'react'

type Props = {
    label: ReactNode
    required: boolean
    optional: string
}
const FormInputLabel = (props: Props) => {
    return (
        <Stack direction="row" spacing={1}>
            <Typography>{props.label}</Typography>
            {!props.required && (
                <Typography
                    className={'input-label-optional-text'}
                    variant="body2"
                    alignSelf="center">{` (${props.optional})`}</Typography>
            )}
        </Stack>
    )
}

export default FormInputLabel

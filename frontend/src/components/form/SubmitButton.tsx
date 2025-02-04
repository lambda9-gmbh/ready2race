import {ButtonProps} from '@mui/material'
import LoadingButton from './LoadingButton.tsx'

type SubmitButtonProps = ButtonProps & {
    label: string
    submitting: boolean
}

export const SubmitButton = ({label, submitting, ...props}: SubmitButtonProps) => {
    return (
        <LoadingButton
            label={label}
            pending={submitting}
            variant={'contained'}
            type={'submit'}
            {...props}
        />
    )
}

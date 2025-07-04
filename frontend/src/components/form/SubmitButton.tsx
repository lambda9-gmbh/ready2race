import {ButtonProps} from '@mui/material'
import LoadingButton from './LoadingButton.tsx'
import {PropsWithChildren} from 'react'

type SubmitButtonProps = ButtonProps & {
    submitting: boolean
}

export const SubmitButton = ({
    children,
    submitting,
    ...props
}: PropsWithChildren<SubmitButtonProps>) => {
    return (
        <LoadingButton pending={submitting} variant={'contained'} type={'submit'} {...props}>
            {children}
        </LoadingButton>
    )
}

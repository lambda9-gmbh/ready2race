import {Stack, Typography} from '@mui/material'
import {PropsWithChildren, ReactNode} from 'react'
import {useTranslation} from 'react-i18next'

type Props = {
    label: ReactNode
    required?: boolean
    horizontal?: boolean
}
const FormInputLabel = (props: PropsWithChildren<Props>) => {
    const {t} = useTranslation()

    return (
        <label>
            {props.label !== undefined && (
                <Stack direction="row" spacing={1} sx={{mb: props.horizontal ? undefined : 1}}>
                    <Typography sx={{fontSize: '1.1rem'}}>{props.label}</Typography>
                    {!props.required && (
                        <Typography
                            className={'input-label-optional-text'}
                            alignSelf="center"
                            color="textSecondary">
                            {` – ${t('common.form.optional')}`}
                        </Typography>
                    )}
                </Stack>
            )}
            {props.children}
        </label>
    )
}

export default FormInputLabel

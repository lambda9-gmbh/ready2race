import {Stack, Typography} from '@mui/material'
import {PropsWithChildren, ReactNode} from 'react'
import {useTranslation} from 'react-i18next'

type Props = {
    label: ReactNode
    required?: boolean
    horizontal?: boolean
    reverse?: boolean
}
const FormInputLabel = (props: PropsWithChildren<Props>) => {
    const {t} = useTranslation()

    return (
        <label
            style={
                props.horizontal
                    ? {
                          display: 'flex',
                          alignItems: 'center',
                          flexDirection: props.reverse ? 'row-reverse' : 'row',
                          justifyContent: props.reverse ? 'flex-end' : 'flex-start',
                      }
                    : {}
            }
            className={props.horizontal ? 'form-label-horizontal' : undefined}>
            <Stack direction="row" spacing={1} sx={props.horizontal ? {mr: 1} : {mb: 1}}>
                <Typography sx={{fontSize: '1.1rem'}}>{props.label}</Typography>
                {!props.required && (
                    <Typography
                        alignSelf="center"
                        color="textSecondary">
                        {` – ${t('common.form.optional')}`}
                    </Typography>
                )}
            </Stack>
            {props.children}
        </label>
    )
}

export default FormInputLabel

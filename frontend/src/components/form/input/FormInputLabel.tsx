import {Box, Stack, Typography} from '@mui/material'
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
            <Box
                sx={{
                    display: 'flex',
                    gap: 1,
                    flexWrap: 'wrap',
                    mr: props.horizontal ? 1 : undefined,
                    mb: !props.horizontal ? 1 : undefined,
                }}>
                <Typography sx={{fontSize: '1.1rem'}}>{props.label}</Typography>
                {!props.required && (
                    <Typography alignSelf="center" color="textSecondary">
                        {` – ${t('common.form.optional')}`}
                    </Typography>
                )}
            </Box>
            {props.children}
        </label>
    )
}

export default FormInputLabel

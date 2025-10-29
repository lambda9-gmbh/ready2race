import {BaseFormInputProps} from '@components/form/input/FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {InputAdornment} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'

type FormInputSecondsProps = Omit<BaseFormInputProps, 'type'>
export const FormInputSeconds = ({sx, ...props}: FormInputSecondsProps) => {
    const {t} = useTranslation()

    return (
        <FormInputNumber
            {...props}
            integer
            slotProps={{
                ...props.slotProps,
                input: props.slotProps?.input
                    ? {
                          endAdornment: (
                              <InputAdornment position={'end'}>
                                  {t('common.form.seconds')}
                              </InputAdornment>
                          ),
                          ...props.slotProps.input,
                      }
                    : {
                          endAdornment: (
                              <InputAdornment position={'end'}>
                                  {t('common.form.seconds')}
                              </InputAdornment>
                          ),
                      },
            }}
            sx={{...sx}}
        />
    )
}

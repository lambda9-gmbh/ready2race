import {BaseFormInputProps, FormInputText} from './FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {formRegexCurrency} from '../../../utils/helpers.ts'
import {InputAdornment} from '@mui/material'

type FormInputCurrencyProps = Omit<BaseFormInputProps, 'type'>
export const FormInputCurrency = (props: FormInputCurrencyProps) => {
    const {t} = useTranslation()

    return (
        <FormInputText
            {...props}
            rules={{
                ...props.rules,
                ...(!props.rules?.pattern && {
                    pattern: {
                        value: formRegexCurrency,
                        message: t('common.form.currency.invalid'),
                    },
                }),
            }}
            // todo: does this work correctly?
            slotProps={{
                ...props.slotProps,
                input: props.slotProps?.input
                    ? {
                          endAdornment: <InputAdornment position={'end'}>€</InputAdornment>,
                          ...props.slotProps.input,
                      }
                    : {endAdornment: <InputAdornment position={'end'}>€</InputAdornment>},
            }}
        />
    )
}

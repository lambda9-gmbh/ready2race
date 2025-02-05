import {BaseTextFieldProps, FilledInputProps, InputAdornment, SlotProps} from '@mui/material'
import {BaseFormInputProps, FormInputText} from './FormInputText.tsx'
import {useTranslation} from 'react-i18next'
import {ElementType} from 'react'
import {formRegexCurrency} from "../../../utils/helpers.ts";

type FormInputCurrencyProps = Omit<BaseFormInputProps, 'type'>
export const FormInputCurrency = (props: FormInputCurrencyProps) => {
    const {t} = useTranslation()

    const inputProps: SlotProps< // todo: better way to do this?
        ElementType<FilledInputProps, keyof JSX.IntrinsicElements>,
        {},
        BaseTextFieldProps
    > = props.slotProps?.input
        ? {
              ...props.slotProps.input,
              endAdornment: <InputAdornment position={'end'}>€</InputAdornment>,
          }
        : {endAdornment: <InputAdornment position={'end'}>€</InputAdornment>}

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
            slotProps={{
                ...props.slotProps,
                input: inputProps,
            }}
        />
    )
}

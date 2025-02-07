import {BaseFormInputProps, FormInputText} from './FormInputText.tsx'
import {formRegexInteger, formRegexNumber} from '../../../utils/helpers.ts'
import {useTranslation} from 'react-i18next'

type FormInputNumberProps = Omit<BaseFormInputProps, 'type'> & {
    min?: number
    max?: number
    integer?: boolean
}

const FormInputNumber = (props: FormInputNumberProps) => {
    const {t} = useTranslation()
    return (
        <FormInputText
            {...props}
            rules={{
                min: props.min
                    ? {
                          value: props.min,
                          message: t('common.form.number.invalid.range', {
                              min: props.min,
                              max: props.max,
                          }),
                      }
                    : undefined,
                max: props.max
                    ? {
                          value: props.max,
                          message: t('common.form.number.invalid.range', {
                              min: props.min,
                              max: props.max,
                          }),
                      }
                    : undefined,
                pattern: {
                    value: props.integer ? formRegexInteger : formRegexNumber,
                    message: props.integer
                        ? t('common.form.number.invalid.pattern.integer')
                        : t('common.form.number.invalid.pattern.number'),
                },
            }}
        />
    )
}

export default FormInputNumber

import {BaseFormInputProps, FormInputText} from './FormInputText.tsx'
import {formRegexInteger, formRegexNumber} from '@utils/helpers.ts'
import {useTranslation} from 'react-i18next'

type FormInputNumberProps = Omit<BaseFormInputProps, 'type'> & {
    min?: number
    max?: number
    integer?: boolean
}

const FormInputNumber = ({min, max, integer, ...props}: FormInputNumberProps) => {
    const {t} = useTranslation()
    return (
        <FormInputText
            {...props}
            rules={{
                ...props.rules,
                ...(!props.rules?.min && {
                    min: min
                        ? {
                              value: min,
                              message: t('common.form.number.invalid.range', {
                                  min: min,
                                  max: max,
                              }),
                          }
                        : undefined,
                }),
                ...(!props.rules?.max && {
                    max: max
                        ? {
                              value: max,
                              message: t('common.form.number.invalid.range', {
                                  min: min,
                                  max: max,
                              }),
                          }
                        : undefined,
                }),
                ...(!props.rules?.pattern && {
                    pattern: {
                        value: integer ? formRegexInteger : formRegexNumber,
                        message: integer
                            ? t('common.form.number.invalid.pattern.integer')
                            : t('common.form.number.invalid.pattern.number'),
                    },
                }),
            }}
        />
    )
}

export default FormInputNumber

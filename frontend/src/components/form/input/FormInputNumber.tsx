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

    const minMaxMsgStart = t('common.form.number.invalid.value.messageStart')

    return (
        <FormInputText
            {...props}
            rules={{
                ...props.rules,
                ...(!props.rules?.min && {
                    min: min
                        ? {
                              value: min,
                              message:
                                  minMaxMsgStart +
                                  (max !== undefined
                                      ? t('common.form.number.invalid.value.range', {
                                            min: min,
                                            max: max,
                                        })
                                      : t('common.form.number.invalid.value.min', {min: min})),
                          }
                        : undefined,
                }),
                ...(!props.rules?.max && {
                    max: max
                        ? {
                              value: max,
                              message:
                                  minMaxMsgStart +
                                  (min !== undefined
                                      ? t('common.form.number.invalid.value.range', {
                                            min: min,
                                            max: max,
                                        })
                                      : t('common.form.number.invalid.value.max', {max: max})),
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

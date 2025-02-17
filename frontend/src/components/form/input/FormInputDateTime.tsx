import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'
import {DateTimePickerElement, DateTimePickerElementProps} from 'react-hook-form-mui/date-pickers'
import {formatISO} from 'date-fns'
import FormInputLabel from "./FormInputLabel.tsx";

type FormInputDateTimeProps = DateTimePickerElementProps & RefAttributes<HTMLDivElement>

const FormInputDateTime = (props: FormInputDateTimeProps) => {
    const {t} = useTranslation()

    // todo? "overwriteErrorMessages" to translate the error msg: "Date is invalid" if the Input is invalid
    return (
        <DateTimePickerElement
            {...props}
            ampm={false}
            rules={{
                ...props.rules,
                ...(props.required &&
                    !props.rules?.required && {
                        required: t('common.form.required'),
                    }),
            }}
            transform={{
                output: value => (value === null ? undefined : formatISO(value).slice(0, 19)),
            }}
            label={
                <FormInputLabel
                    label={props.label}
                    required={props.required === true || props.rules?.required !== undefined}
                    optional={t('common.form.optional')}
                />
            }
        />
    )
}
export default FormInputDateTime

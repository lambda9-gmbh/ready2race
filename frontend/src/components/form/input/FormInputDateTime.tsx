import {RefAttributes, useEffect} from 'react'
import {useTranslation} from 'react-i18next'
import {DateTimePickerElement, DateTimePickerElementProps} from 'react-hook-form-mui/date-pickers'
import dayjs from 'dayjs'
import {useFormContext} from "react-hook-form-mui";

type FormInputDateTimeProps = DateTimePickerElementProps & RefAttributes<HTMLDivElement>

const FormInputDateTime = (props: FormInputDateTimeProps) => {
    const {t} = useTranslation()

    const formContext = useFormContext()

    useEffect(() => {
        const value = formContext.getValues(props.name)
        if (value) {
            formContext.setValue(props.name, dayjs(value))
        }
    }, [])

    return (
        <DateTimePickerElement
            {...props}
            ampm={false}
            rules={{
                ...props.rules,
                ...(props.required &&
                    !props.rules?.required && {required: t('common.form.required')}),
            }}
        />
    )
}
export default FormInputDateTime

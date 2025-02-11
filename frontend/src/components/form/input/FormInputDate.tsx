import {RefAttributes} from 'react'
import {useTranslation} from 'react-i18next'
import {DatePickerElement, DatePickerElementProps} from 'react-hook-form-mui/date-pickers'
import dayjs from 'dayjs'
import 'dayjs/plugin/utc'

type FormInputDateProps = DatePickerElementProps & RefAttributes<HTMLDivElement>

const FormInputDate = (props: FormInputDateProps) => {
    const {t} = useTranslation()

    return (
        <DatePickerElement
            {...props}
            rules={{
                ...props.rules,
                ...(props.required &&
                    !props.rules?.required && {required: t('common.form.required')}),
            }}
            transform={{
                output: value => {
                    console.log(value)
                    return dayjs(value).utc(true).format('YYYY-MM-DD')
                },
            }}
        />
    )
}
export default FormInputDate

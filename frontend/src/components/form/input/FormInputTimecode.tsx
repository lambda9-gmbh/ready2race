import {useTranslation} from 'react-i18next'
import {BaseFormInputProps, FormInputText} from '@components/form/input/FormInputText.tsx'

type FormInputEmailProps = Omit<BaseFormInputProps, 'type'>

const FormInputTimecode = ({sx, ...props}: FormInputEmailProps) => {
    const {t} = useTranslation()
    return (
        <FormInputText
            {...props}
            type={'text'}
            rules={{
                ...props.rules,
                ...(!props.rules?.pattern && {
                    pattern: {
                        value: /^([-+])?(\d+)(:([0-5]\d))?(:([0-5]\d))?(\.(\d{1,3}))?$/,
                        message: t('common.form.timecode.invalid'),
                    },
                }),
            }}
            sx={{...sx}}
        />
    )
}

export default FormInputTimecode

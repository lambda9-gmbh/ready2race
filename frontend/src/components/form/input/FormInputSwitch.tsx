import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {SwitchElement, SwitchElementProps} from 'react-hook-form-mui'
import {RefAttributes} from 'react'

export type FormInputSwitchProps = SwitchElementProps &
    RefAttributes<HTMLLabelElement> & {horizontal?: boolean; reverse?: boolean}

const FormInputSwitch = (props: FormInputSwitchProps) => {
    return (
        <FormInputLabel
            label={props.label}
            required={true}
            horizontal={props.horizontal}
            reverse={props.reverse}>
            <SwitchElement {...props} label={null} />
        </FormInputLabel>
    )
}

export default FormInputSwitch

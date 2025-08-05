import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {SwitchElement, SwitchElementProps} from 'react-hook-form-mui'
import {RefAttributes} from 'react'

export type FormInputSwitchProps = SwitchElementProps &
    RefAttributes<HTMLLabelElement> & {horizontal?: boolean; reverse?: boolean}

const FormInputSwitch = ({label, horizontal, reverse, ...props}: FormInputSwitchProps) => {
    return (
        <FormInputLabel
            label={label}
            required={true}
            horizontal={horizontal}
            reverse={reverse}>
            <SwitchElement {...props} label={null} />
        </FormInputLabel>
    )
}

export default FormInputSwitch

import React, {useEffect, useRef} from 'react'
import {Checkbox} from '@mui/material'
import {useTranslation} from 'react-i18next'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

interface SelectAllCheckboxProps {
    checked: boolean
    indeterminate: boolean
    onChange: () => void
    disabled?: boolean
}

const SelectAllCheckbox: React.FC<SelectAllCheckboxProps> = ({
    checked,
    indeterminate,
    onChange,
    disabled = false,
}) => {
    const {t} = useTranslation()
    const checkboxRef = useRef<HTMLInputElement>(null)

    useEffect(() => {
        if (checkboxRef.current) {
            checkboxRef.current.indeterminate = indeterminate
        }
    }, [indeterminate])

    return (
        <FormInputLabel label={t('common.selectAll')} reverse horizontal required>
            <Checkbox
                inputRef={checkboxRef}
                checked={checked}
                indeterminate={indeterminate}
                onChange={onChange}
                disabled={disabled}
            />
        </FormInputLabel>
    )
}

export default SelectAllCheckbox

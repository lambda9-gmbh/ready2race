import React, {useEffect, useRef} from 'react'
import {Checkbox, FormControlLabel} from '@mui/material'
import {useTranslation} from 'react-i18next'

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
        <FormControlLabel
            control={
                <Checkbox
                    inputRef={checkboxRef}
                    checked={checked}
                    indeterminate={indeterminate}
                    onChange={onChange}
                    disabled={disabled}
                />
            }
            label={t('common.selectAll')}
        />
    )
}

export default SelectAllCheckbox

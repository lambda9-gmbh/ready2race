import {useFormContext, useWatch} from 'react-hook-form-mui'
import {useEffect, useMemo, useState} from 'react'
import {debounce} from '@mui/material'
import {Search} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {ClubSearchDto, getClubNames} from '@api/index.ts'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'

export const AutocompleteClub = (props: {
    name: string
    label: string
    required?: boolean
    disabled?: boolean
    onChange?: () => void
}) => {
    const {t} = useTranslation()

    const [options, setOptions] = useState<string[]>([])
    const [searchResults, setSearchResults] = useState<ClubSearchDto[]>([])
    const [loading, setLoading] = useState<boolean>(false)
    const [inputValue, setInputValue] = useState('')

    const formContext = useFormContext()

    const value = useWatch({control: formContext.control, name: props.name, defaultValue: ''})

    const search = useMemo(
        () =>
            debounce((search: string) => {
                setLoading(true)
                getClubNames({
                    query: {
                        limit: 10,
                        search: search,
                        offset: 0,
                        sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}]),
                    },
                })
                    .then(res => setSearchResults(res.data?.data ?? []))
                    .finally(() => setLoading(false))
            }, 400),
        [],
    )

    useEffect(() => {
        setOptions(() => {
            if (value != undefined) {
                return [value, ...searchResults.map(r => r.name)]
            }
            return [...searchResults.map(r => r.name)]
        })
    }, [value, searchResults])

    useEffect(() => {
        search(inputValue)
    }, [inputValue, search])

    const onChange = (value: string) => {
        if (props.onChange) {
            props.onChange()
        }
        setInputValue(value)
    }

    return (
        <FormInputAutocomplete
            name={props.name}
            label={props.label}
            options={options}
            loading={loading}
            required={props.required}
            rules={{
                ...(props.required && {required: t('common.form.required')}),
            }}
            textFieldProps={{
                onChange: e => {
                    onChange(e.target.value)
                },
                InputProps: {
                    endAdornment: <Search sx={{color: '#ccc'}} />,
                },
            }}
            autocompleteProps={{
                size: 'small',
                // @ts-ignore
                freeSolo: true,
                filterSelectedOptions: true,
                autoSelect: true,
                disabled: props.disabled,
            }}
        />
    )
}

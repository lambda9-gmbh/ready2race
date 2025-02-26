import {AutocompleteElement, useFormContext, useWatch} from 'react-hook-form-mui'
import {useEffect, useMemo, useState} from 'react'
import {debounce} from '@mui/material'
import {Search} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {ClubSearchDto, getClubNames} from '../../../api'

export const FormInputAutocompleteClub = (props: {name: string; label: string; required?: boolean, disabled?: boolean}) => {
    const {t} = useTranslation()

    const [options, setOptions] = useState<string[]>([])
    const [searchResults, setSearchResults] = useState<ClubSearchDto[]>([])
    const [loading, setLoading] = useState<boolean>(false)
    const [inputValue] = useState('')

    const formContext = useFormContext()

    const value = useWatch({control: formContext.control, name: props.name})

    const search = useMemo(
        () =>
            debounce((search: string) => {
                setLoading(true)
                getClubNames({
                    query: {
                        limit: 10,
                        search: search,
                        offset: 0,
                        sort: JSON.stringify({field: 'name', direction: 'asc'}),
                    },
                })
                    .then(res => setSearchResults(res.data?.data ?? []))
                    .finally(() => setLoading(false))
            }, 400),
        [],
    )

    useEffect(() => {
        setOptions(() => {
            if (value !== undefined) {
                return [value, ...searchResults.map(r => r.name)]
            }
            return [...searchResults.map(r => r.name)]
        })
    }, [value, searchResults])

    useEffect(() => {
        search(inputValue)
    }, [inputValue, search])

    return (
        <AutocompleteElement
            name={props.name}
            label={props.label}
            options={options}
            loading={loading}
            required={props.required}
            rules={{
                ...(props.required && {required: t('common.form.required')}),
            }}
            textFieldProps={{
                InputProps: {
                    endAdornment: <Search sx={{color: '#ccc'}} />,
                },
            }}
            autocompleteProps={{
                freeSolo: true,
                filterSelectedOptions: true,
                disableClearable: true,
                autoSelect: true,
                disabled: props.disabled,
            }}
        />
    )
}

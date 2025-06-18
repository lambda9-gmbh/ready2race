import {useTranslation} from 'react-i18next'
import {getUsers} from '../../../api'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {useFetch} from '@utils/hooks.ts'

export const FormInputAutocompleteUser = (props: {
    name: string
    label: string
    required?: boolean
    disabled?: boolean
    onChange?: () => void
}) => {
    const {t} = useTranslation()

    const {data, pending} = useFetch(signal =>
        // TODO Implement call with reduced data (id, firstname, lastname)
        getUsers({
            query: {
                sort: JSON.stringify([
                    {field: 'FIRSTNAME', direction: 'ASC'},
                    {field: 'LASTNAME', direction: 'ASC'},
                ]),
            },
            signal,
        }),
    )

    return (
        <FormInputAutocomplete
            name={props.name}
            label={props.label}
            options={data?.data ?? []}
            loading={pending}
            matchId
            required={props.required}
            rules={{
                ...(props.required && {required: t('common.form.required')}),
            }}
            autocompleteProps={{
                size: 'small',
                filterSelectedOptions: true,
                disabled: props.disabled,
                limitTags: 5,
                getOptionLabel: option => `${option?.firstname} ${option?.lastname}`,
            }}
            // @ts-ignore
            multiple={true}
        />
    )
}

import {EventRegistrationParticipantUpsertDto} from '@api/types.gen.ts'
import {SyntheticEvent, useCallback, useMemo} from 'react'
import {AutocompleteChangeDetails, AutocompleteChangeReason} from '@mui/material'
import {AutocompleteElement, useWatch} from 'react-hook-form-mui'

export const TeamParticipantAutocomplete = (props: {
    name: string
    label: string
    count: number
    required?: boolean
    loading?: boolean
    disabled?: boolean
    options: EventRegistrationParticipantUpsertDto[]
    transform?:
        | {
              input?: ((value: any) => EventRegistrationParticipantUpsertDto[]) | undefined
              output?:
                  | ((
                        event: SyntheticEvent,
                        value: EventRegistrationParticipantUpsertDto[],
                        reason: AutocompleteChangeReason,
                        details?:
                            | AutocompleteChangeDetails<EventRegistrationParticipantUpsertDto>
                            | undefined,
                    ) => any)
                  | undefined
          }
        | undefined
}) => {
    const value = useWatch({name: props.name})

    const limitReached = useMemo(() => {
        return value
            ? (props.transform?.input?.(value) ?? value ?? []).length >= props.count
            : false
    }, [
        value,
        (props.transform?.input?.(value) ?? value ?? []).length,
        props.transform,
        props.count,
    ])

    const getOptionDisabled = useCallback(
        (option: EventRegistrationParticipantUpsertDto) => {
            return (
                limitReached && !(props.transform?.input?.(value) ?? value ?? []).includes(option)
            )
        },
        [limitReached, value, props.transform],
    )

    return (
        <AutocompleteElement
            name={props.name}
            transform={props.transform}
            matchId
            required={props.required}
            multiple
            loading={props.loading}
            rules={{
                validate: _ => limitReached, // TODO validate selected genders?
            }}
            autocompleteProps={{
                size: 'small',
                limitTags: 5,
                disabled: props.disabled,
                getOptionDisabled: getOptionDisabled,
                slotProps: {
                    popper: {
                        sx: {
                            '& .MuiAutocomplete-groupLabel': {
                                overflow: 'hidden',
                                whiteSpace: 'nowrap',
                                textOverflow: 'ellipsis',
                            },
                        },
                    },
                },
                groupBy: option => option.externalClubName || '',
                getOptionLabel: option => `${option.firstname} ${option.lastname}`,
            }}
            options={props.options}
        />
    )
}

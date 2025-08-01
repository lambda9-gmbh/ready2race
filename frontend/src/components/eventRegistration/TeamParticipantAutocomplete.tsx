import {
    CompetitionRegistrationNamedParticipantUpsertDto,
    CompetitionRegistrationUpsertDto,
    EventRegistrationParticipantUpsertDto,
    Gender,
    ParticipantForEventDto,
} from '@api/types.gen.ts'
import {SyntheticEvent, useCallback, useMemo} from 'react'
import {AutocompleteChangeDetails, AutocompleteChangeReason} from '@mui/material'
import {AutocompleteElement, useWatch} from 'react-hook-form-mui'

export const TeamParticipantAutocomplete = (props: {
    name: string
    label: string
    countMales: number
    countFemales: number
    countNonBinary: number
    countMixed: number
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
    competitionPath?: string
    namedParticipantsPath?: string
    disabledParticipants?: ParticipantForEventDto[]
}) => {
    const value: CompetitionRegistrationNamedParticipantUpsertDto | undefined = useWatch({
        name: props.name,
    })

    const {limitReached, limitReachedMale, limitReachedFemale, limitReachedNonBinary} =
        useMemo(() => {
            const countByGender = props.options
                .filter(p => value?.participantIds.includes(p.id))
                .reduce(
                    (acc, p) => {
                        acc[p.gender] = (acc[p.gender] || 0) + 1
                        return acc
                    },
                    {} as Record<Gender, number>,
                )

            const remainingMixed =
                props.countMixed -
                (countByGender.M > props.countMales ? countByGender.M - props.countMales : 0) -
                (countByGender.F > props.countFemales ? countByGender.F - props.countFemales : 0) -
                (countByGender.D > props.countNonBinary
                    ? countByGender.D - props.countNonBinary
                    : 0)

            const maleFemaleUsedByNonBinary =
                (countByGender.D ?? 0) - props.countNonBinary - props.countMixed < 0
                    ? 0
                    : (countByGender.D ?? 0) - props.countNonBinary - props.countMixed

            const maleSlotsFilled =
                (countByGender.M ?? 0) + maleFemaleUsedByNonBinary >= props.countMales
            const femaleSlotsFilled =
                (countByGender.F ?? 0) + maleFemaleUsedByNonBinary >= props.countFemales

            // NonBinary genders can also fill strict male/female slots
            // These slots are only filled with nonBinary AFTER all strict nonBinary and mixed slots are filled
            return {
                limitReached: value
                    ? (value.participantIds ?? []).length >=
                      props.countMixed +
                          props.countNonBinary +
                          props.countMales +
                          props.countFemales
                    : false,
                limitReachedMale: maleSlotsFilled && remainingMixed <= 0,
                limitReachedFemale: femaleSlotsFilled && remainingMixed <= 0,
                limitReachedNonBinary:
                    (countByGender.D ?? 0) >= props.countNonBinary &&
                    remainingMixed <= 0 &&
                    maleSlotsFilled &&
                    femaleSlotsFilled,
            }
        }, [value, props.countMales, props.countFemales, props.countNonBinary, props.countMixed])

    // If @TeamParticipantAutocomplete is used in event registration, we can use this to make sure,
    // participants are only used once for a competition across all teams.
    const competitionWatch: CompetitionRegistrationUpsertDto | undefined =
        props.competitionPath && useWatch({name: props.competitionPath})

    // If @TeamParticipantAutocomplete is used in single competition registration, we can use this to make sure,
    // participants are only used once in a team across all named participants.
    const namedParticipantsWatch: CompetitionRegistrationNamedParticipantUpsertDto[] | undefined =
        props.namedParticipantsPath && useWatch({name: props.namedParticipantsPath})

    const getOptionDisabled = useCallback(
        (option: EventRegistrationParticipantUpsertDto) => {
            if ((value?.participantIds ?? []).includes(option.id)) {
                return false
            } else {
                return (
                    limitReached ||
                    (option.gender === 'M' && limitReachedMale) ||
                    (option.gender === 'F' && limitReachedFemale) ||
                    (option.gender === 'D' && limitReachedNonBinary) ||
                    (competitionWatch?.teams
                        ?.flatMap(t => t.namedParticipants?.flatMap(n => n?.participantIds ?? []))
                        ?.some(userId => userId === option.id) ??
                        false) ||
                    (namedParticipantsWatch
                        ?.flatMap(n => n?.participantIds ?? [])
                        ?.some(userId => userId === option.id) ??
                        false) ||
                    (props.disabledParticipants?.some(p => p.id === option.id) ?? false)
                )
            }
        },
        [
            limitReached,
            limitReachedMale,
            limitReachedFemale,
            limitReachedNonBinary,
            value,
            props.transform,
            competitionWatch,
            props.disabledParticipants,
        ],
    )

    return (
        <AutocompleteElement
            name={props.name}
            transform={props.transform}
            matchId
            required={props.required}
            multiple
            loading={props.loading}
            /*rules={{
                validate: _ => limitReached, // TODO validate selected genders? - edit: "limitReached" does not mean, the input is wrong. It just disables the options
            }}*/
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

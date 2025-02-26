import {AutocompleteElement, useFieldArray, useFormContext, useWatch} from 'react-hook-form-mui'
import {Button, Divider, IconButton, Paper, Stack, Typography} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {GroupAdd} from '@mui/icons-material'
import {v4 as uuid} from 'uuid'

import {EventRegistrationParticipantUpsertDto, EventRegistrationRaceDto, EventRegistrationUpsertDto} from '../../api'
import {useTranslation} from 'react-i18next'
import {EventRegistrationPriceTooltip} from './EventRegistrationPriceTooltip.tsx'

export const TeamParticipantAutocomplete = (props: {
    name: string
    label: string
    options: EventRegistrationParticipantUpsertDto[]
}) => {
    return (
        <AutocompleteElement
            label={props.label}
            name={props.name}
            matchId
            required
            autocompleteProps={{
                size: 'small',
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
                sx: {
                    width: 250,
                    minWidth: 250,
                },
                groupBy: option => option.externalClubName || '',
                getOptionLabel: option => `${option.firstname} ${option.lastname}`,
            }}
            options={props.options}
        />
    )
}

const EventRegistrationTeamForm = (props: {
    index: number
    race: EventRegistrationRaceDto
}) => {
    const {t} = useTranslation()

    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const {fields, append, remove} = useFieldArray({
        control: formContext.control,
        name: `raceRegistrations.${props.index}.teams`,
        keyName: 'fieldId',
    })

    const participants: EventRegistrationParticipantUpsertDto[] = useWatch({
        control: formContext.control,
        name: `participants`,
    })

    return (
        <Stack padding={1}>
            <Stack direction={'row'} spacing={1} alignItems={'center'}>
                <Typography>{`${props.race.identifier} ${props.race.name} (${props.race.shortName})`}</Typography>
                <EventRegistrationPriceTooltip race={props.race} />
            </Stack>
            <Stack spacing={1} padding={1}>
                {fields.map((field, fieldIndex) => (
                    <Paper sx={{p: 2}} elevation={2} key={field.fieldId}>
                        <Stack
                            direction={'row'}
                            rowGap={2}
                            columnGap={1}
                            flexWrap={'wrap'}
                            key={field.fieldId}>
                            {[
                                ...Array(
                                    props.race.countMales +
                                    props.race.countFemales +
                                    props.race.countMixed +
                                    props.race.countNonBinary,
                                ),
                            ].map((_, index) => (
                                <TeamParticipantAutocomplete
                                    name={`teamRaces.${props.index}.teams.${fieldIndex}.participant.${index}`}
                                    key={`${field.fieldId}-${index}`}
                                    label={t('club.participant.title')}
                                    options={participants}
                                />
                            ))}
                            {/*{props.race.mitSteuermann && (*/}
                            {/*    <TeamParticipantAutocomplete*/}
                            {/*        name={`teamrennanmeldung.${props.tagIndex}.anmeldungen.${props.index}.teams.${fieldIndex}.steuermann`}*/}
                            {/*        key={`${field.fieldId}-steuermann`}*/}
                            {/*        label={t('regatta.registration.cox')}*/}
                            {/*        options={participants}*/}
                            {/*    />*/}
                            {/*)}*/}
                            {/*{props.race.privatbootErlaubt && (*/}
                            {/*    <CheckboxElement*/}
                            {/*        name={`teamrennanmeldung.${props.tagIndex}.anmeldungen.${props.index}.teams.${fieldIndex}.privatboot`}*/}
                            {/*        label={t('regatta.registration.ownBoat')}*/}
                            {/*    />*/}
                            {/*)}*/}
                            <IconButton sx={{alignSelf: 'end'}} onClick={() => remove(fieldIndex)}>
                                <DeleteIcon />
                            </IconButton>
                        </Stack>
                    </Paper>
                ))}

                <Button
                    onClick={() => {
                        append({id: uuid(), participants: []})
                    }}>
                    <GroupAdd />
                </Button>
            </Stack>
            <Divider />
        </Stack>
    )
}

export default EventRegistrationTeamForm

import {CheckboxElement, RadioButtonGroup, useFormContext, useWatch} from 'react-hook-form-mui'
import {Box, IconButton, Paper, Stack, Typography} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {useTranslation} from 'react-i18next'
import {EventRegistrationParticipantUpsertDto, EventRegistrationUpsertDto} from '../../api'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {FormInputAutocompleteClub} from '../form/input/FormInputAutocompleteClub.tsx'
import FormInputNumber from '../form/input/FormInputNumber.tsx'
import {useEffect, useState} from 'react'

export const EventRegistrationParticipantForm = (props: {
    index: number
    removeParticipant: () => void
}) => {
    const {t} = useTranslation()

    const [existingParticipantValues, setExistingParticipantValues] = useState<EventRegistrationParticipantUpsertDto |undefined>(undefined)

    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const currentYear = new Date().getFullYear()

    const isNew = useWatch({name: `participants.${props.index}.isNew`, defaultValue: false})
    const isExternal = useWatch({name: `participants.${props.index}.external`, defaultValue: false})

    useEffect(() => {
        if(!isNew){
           setExistingParticipantValues(formContext.getValues(`participants.${props.index}`))
        }
    }, [isNew])

    useEffect(() => {
        if (!isExternal) {
            formContext.setValue(`participants.${props.index}.externalClubName`, '')
        }
    }, [isExternal])

    return (
        <Paper sx={{p: 2}} elevation={2}>
            <Stack direction={'row'} alignItems={'center'} spacing={2}>
                {isNew ?
                    <>
                        <Stack spacing={1} flex={1}>
                            <Stack direction="row" spacing={2} alignItems={'center'}>
                                <FormInputText
                                    name={`participants.${props.index}.firstname`}
                                    label={t('entity.firstname')}
                                    required
                                />
                                <FormInputText
                                    name={`participants.${props.index}.lastname`}
                                    label={t('entity.lastname')}
                                    required
                                />
                                <RadioButtonGroup
                                    label={t('entity.gender')}
                                    name={`participants.${props.index}.gender`}
                                    onChange={() =>
                                        formContext.setValue(`participants.${props.index}.competitionsSingle`, [])
                                    }
                                    row
                                    options={[
                                        {
                                            id: 'F',
                                            label: 'F',
                                        },
                                        {
                                            id: 'M',
                                            label: 'M',
                                        },
                                        {
                                            id: 'D',
                                            label: 'D',
                                        },
                                    ]}
                                    required
                                />
                                <FormInputNumber
                                    name={`participants.${props.index}.year`}
                                    label={t('club.participant.year')}
                                    required
                                    min={currentYear - 120}
                                    max={currentYear}
                                    sx={{width: '100px'}}
                                />
                            </Stack>
                            <Stack direction="row" spacing={2} alignItems={'center'}>
                                <CheckboxElement
                                    name={`participants.${props.index}.external`}
                                    label={t('club.participant.external')}
                                />
                                <Box flex={1}>
                                    <FormInputAutocompleteClub
                                        name={`participants.${props.index}.externalClubName`}
                                        disabled={!isExternal}
                                        label={t('club.club')}
                                    />
                                </Box>
                            </Stack>
                        </Stack>
                        <IconButton onClick={props.removeParticipant}>
                            <DeleteIcon />
                        </IconButton>
                    </>
                    : <Stack flex={1}>
                        <Typography>{existingParticipantValues?.firstname} {existingParticipantValues?.lastname}</Typography>
                    </Stack>
                }
                <Typography alignSelf={'end'} variant={'overline'} color={'grey'}>
                    #{props.index + 1}
                </Typography>
            </Stack>
        </Paper>
    )
}

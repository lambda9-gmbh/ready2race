import {useFormContext, useWatch} from 'react-hook-form-mui'
import {Box, IconButton, Paper, Stack, Typography} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {useTranslation} from 'react-i18next'
import {EventRegistrationParticipantUpsertDto, EventRegistrationUpsertDto} from '../../api'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {FormInputAutocompleteClub} from '../form/input/FormInputAutocompleteClub.tsx'
import FormInputNumber from '../form/input/FormInputNumber.tsx'
import {useEffect, useMemo, useState} from 'react'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {Edit} from '@mui/icons-material'
import {grey} from '@mui/material/colors'

export const EventRegistrationParticipantForm = (props: {
    index: number
    removeParticipant: () => void
}) => {
    const {t} = useTranslation()

    const [isEditable, setIsEditable] = useState(false)
    const [hasChanged, setHasChanged] = useState<boolean | undefined>(undefined)
    const [isNew, setIsNew] = useState<boolean>(true)

    const [existingParticipantValues, setExistingParticipantValues] = useState<
        EventRegistrationParticipantUpsertDto | undefined
    >(undefined)

    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const currentYear = useMemo(() => new Date().getFullYear(), [])

    const isExternal = useWatch({name: `participants.${props.index}.external`, defaultValue: false})

    const handleChange = () => {
        if (!hasChanged) {
            setHasChanged(true)
            formContext.setValue(`participants.${props.index}.hasChanged`, true)
        }
    }

    useEffect(() => {
        const isNewParticipant = formContext.getValues(`participants.${props.index}.isNew`)
        if (!isNewParticipant) {
            setIsNew(false)
            let changed = formContext.getValues(`participants.${props.index}.hasChanged`)
            if (changed !== true) {
                setIsEditable(false)
                setExistingParticipantValues(formContext.getValues(`participants.${props.index}`))
            } else {
                setHasChanged(true)
            }
        } else {
            setIsEditable(true)
        }
    }, [])

    useEffect(() => {
        if (!isExternal) {
            formContext.setValue(`participants.${props.index}.externalClubName`, '')
        }
    }, [isExternal])

    return (
        <Paper sx={{p: 1, pl: 2, pr: 2}} elevation={2}>
            <Stack direction={'row'} alignItems={'center'} spacing={1}>
                {isEditable ? (
                    <>
                        <Stack spacing={1} flex={1}>
                            <Stack direction="row" spacing={2} alignItems={'center'}>
                                <FormInputText
                                    name={`participants.${props.index}.firstname`}
                                    label={t('entity.firstname')}
                                    required
                                    size={'small'}
                                    onChange={handleChange}
                                />
                                <FormInputText
                                    name={`participants.${props.index}.lastname`}
                                    label={t('entity.lastname')}
                                    required
                                    size={'small'}
                                    onChange={handleChange}
                                />
                                <FormInputRadioButtonGroup
                                    label={t('entity.gender')}
                                    name={`participants.${props.index}.gender`}
                                    onChange={() => {
                                        handleChange()
                                        formContext.setValue(
                                            `participants.${props.index}.competitionsSingle`,
                                            [],
                                        )
                                    }}
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
                                    size={'small'}
                                    onChange={handleChange}
                                />
                            </Stack>
                            <Stack direction="row" spacing={2} alignItems={'center'}>
                                <FormInputCheckbox
                                    name={`participants.${props.index}.external`}
                                    label={t('club.participant.external')}
                                    onChange={handleChange}
                                />
                                <Box flex={1}>
                                    <FormInputAutocompleteClub
                                        name={`participants.${props.index}.externalClubName`}
                                        onChange={handleChange}
                                        disabled={!isExternal}
                                        label={t('club.club')}
                                        required
                                    />
                                </Box>
                            </Stack>
                        </Stack>
                        {isNew && (
                            <IconButton onClick={props.removeParticipant}>
                                <DeleteIcon />
                            </IconButton>
                        )}
                    </>
                ) : (
                    <Stack flex={1} direction={'row'} alignItems={'center'}>
                        <Stack direction={'row'} flex={1} spacing={1}>
                            <Typography>
                                {existingParticipantValues?.firstname}{' '}
                                {existingParticipantValues?.lastname}
                            </Typography>
                            <Typography color={grey[600]}>
                                {existingParticipantValues?.gender}
                            </Typography>
                            <Typography color={grey[600]}>
                                {existingParticipantValues?.year}
                            </Typography>
                            <Typography color={grey[600]} fontStyle={'italic'}>
                                {existingParticipantValues?.externalClubName}
                            </Typography>
                        </Stack>
                        <IconButton onClick={() => setIsEditable(true)}>
                            <Edit />
                        </IconButton>
                    </Stack>
                )}
                <Typography alignSelf={'end'} variant={'overline'} color={'grey'}>
                    #{props.index + 1}
                </Typography>
            </Stack>
        </Paper>
    )
}

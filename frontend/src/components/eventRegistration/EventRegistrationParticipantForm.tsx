import {useFormContext, useWatch} from 'react-hook-form-mui'
import {Box, IconButton, Paper, Stack, Tooltip, Typography} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {useTranslation} from 'react-i18next'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {AutocompleteClub} from '../club/AutocompleteClub.tsx'
import FormInputNumber from '../form/input/FormInputNumber.tsx'
import {useEffect, useMemo, useState} from 'react'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {Edit, HelpOutline} from '@mui/icons-material'
import {grey} from '@mui/material/colors'
import {
    EventRegistrationFormData,
    EventRegistrationParticipantFormData,
} from '../../pages/eventRegistration/EventRegistrationCreatePage.tsx'
import FormInputEmail from '@components/form/input/FormInputEmail.tsx'

export const EventRegistrationParticipantForm = (props: {
    index: number
    removeParticipant: () => void
}) => {
    const {t} = useTranslation()

    const [isEditable, setIsEditable] = useState(false)
    const [hasChanged, setHasChanged] = useState<boolean | undefined>(undefined)
    const [isNew, setIsNew] = useState<boolean>(true)

    const [existingParticipantValues, setExistingParticipantValues] = useState<
        EventRegistrationParticipantFormData | undefined
    >(undefined)

    const formContext = useFormContext<EventRegistrationFormData>()

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
                setIsEditable(true)
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
        <Paper sx={{p: {xs: 0.5, sm: 1}, pl: {xs: 1, sm: 2}, pr: {xs: 1, sm: 2}}} elevation={2}>
            <Stack direction={'row'} alignItems={'center'} spacing={1}>
                {isEditable ? (
                    <>
                        <Stack spacing={1} flex={1}>
                            <Stack
                                direction={{xs: 'column', sm: 'row'}}
                                spacing={2}
                                alignItems={{xs: 'stretch', sm: 'center'}}>
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
                                <FormInputEmail
                                    name={`participants.${props.index}.email`}
                                    label={t('user.email.email')}
                                    size={'small'}
                                    onChange={handleChange}
                                    fullWidth
                                />
                            </Stack>
                            <Stack
                                direction={{xs: 'column', sm: 'row'}}
                                spacing={2}
                                alignItems={{xs: 'flex-start', sm: 'center'}}>
                                <FormInputCheckbox
                                    name={`participants.${props.index}.external`}
                                    label={
                                        <Stack direction={'row'}>
                                            {t('club.participant.external')}
                                            <Tooltip title={t('club.participant.externalHint')}>
                                                <HelpOutline fontSize={'small'} color={'info'} />
                                            </Tooltip>
                                        </Stack>
                                    }
                                    onChange={handleChange}
                                />
                                <Box flex={1} sx={{width: {xs: '100%', sm: 'auto'}}}>
                                    {isExternal && (
                                        <AutocompleteClub
                                            name={`participants.${props.index}.externalClubName`}
                                            onChange={handleChange}
                                            disabled={!isExternal}
                                            label={t('club.participant.externalClub')}
                                            required
                                        />
                                    )}
                                </Box>
                            </Stack>
                        </Stack>
                        {isNew && (
                            <IconButton onClick={props.removeParticipant} sx={{cursor: 'pointer'}}>
                                <DeleteIcon />
                            </IconButton>
                        )}
                    </>
                ) : (
                    <Stack flex={1} direction={'row'} alignItems={'center'} flexWrap={'wrap'}>
                        <Stack
                            direction={'row'}
                            flex={1}
                            spacing={1}
                            flexWrap={'wrap'}
                            sx={{minWidth: {xs: '100%', sm: 'auto'}}}>
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
                        <IconButton onClick={() => setIsEditable(true)} sx={{cursor: 'pointer'}}>
                            <Edit />
                        </IconButton>
                    </Stack>
                )}
                <Typography
                    alignSelf={'end'}
                    variant={'overline'}
                    color={'grey'}
                    sx={{display: {xs: 'none', sm: 'block'}}}>
                    #{props.index + 1}
                </Typography>
            </Stack>
        </Paper>
    )
}

import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Select,
    Stack,
    Typography,
} from '@mui/material'
import {CompetitionRoundDto} from '@api/types.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {addSubstitution, getPossibleSubOuts} from '@api/sdk.gen.ts'
import {useState} from 'react'
import BaseDialog from '@components/BaseDialog.tsx'
import {Controller, FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useTranslation} from 'react-i18next'
import SubstitutionSelectParticipantIn from '@components/event/competition/excecution/SubstitutionSelectParticipantIn.tsx'

type Props = {
    reloadRoundDto: () => void
    roundDto: CompetitionRoundDto
}

type Form = {
    participantIn: string
    participantOut: string
    reason: string
}
const Substitutions = ({reloadRoundDto, roundDto}: Props) => {
    const feedback = useFeedback()
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const formContext = useForm<Form>()

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)

    const openDialog = () => {
        setDialogOpen(true)
    }

    const closeDialog = () => {
        setDialogOpen(false)
    }

    const {data: subOutsData} = useFetch(
        signal =>
            getPossibleSubOuts({
                signal,
                path: {
                    eventId,
                    competitionId,
                    competitionSetupRoundId: roundDto.setupRoundId,
                },
            }),
        {
            deps: [eventId, competitionId],
        },
    )

    const subOutOptions: AutocompleteOption[] =
        subOutsData?.map(p => ({
            id: p.id,
            label: `${p.firstName} ${p.lastName} (${p.clubName} ${p.competitionRegistrationName})`,
        })) ?? []

    console.log('options', subOutOptions)

    const onSubmit = async (formData: Form) => {
        setSubmitting(true)
        console.log(formData)
        const {error} = await addSubstitution({
            path: {
                eventId: eventId,
                competitionId: competitionId,
            },
            body: {
                competitionRegistrationId: roundDto.matches[0].teams[0].registrationId,
                competitionSetupRound: roundDto.setupRoundId,
                participantOut: formData.participantOut ?? '',
                participantIn: formData.participantIn ?? '',
                reason: takeIfNotEmpty(formData.reason),
            },
        })
        setSubmitting(false)

        if (error) {
            feedback.error('todo ERROR')
        } else {
            closeDialog()
            feedback.success('todo Saved')
        }
        reloadRoundDto()
    }

    /*    const onClick = () => {
            const {error} = await addSubstitution({
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                },
                body: {
                    competitionRegistrationId: roundDto.matches[0].teams[0].registrationId,
                    competitionSetupRound: roundDto.setupRoundId,
                    participantOut: roundDto.matches[0].teams[0].
                }
            })

            if (error) {
                feedback.error("todo error")
            } else {
                feedback.success("todo success")
            }
        }*/

    return (
        <>
            <Typography>Subs:</Typography>
            {roundDto.substitutions.map(sub => (
                <Box key={sub.id}>
                    <Typography>todo Club: {sub.clubId}</Typography>
                    <Typography>
                        todo In: {sub.participantIn.firstName} {sub.participantIn.lastName}
                    </Typography>
                    <Typography>
                        todo Out: {sub.participantOut.firstName} {sub.participantOut.lastName}
                    </Typography>
                </Box>
            ))}

            <Button onClick={openDialog}>Add Substitution</Button>

            <BaseDialog open={dialogOpen} onClose={closeDialog} maxWidth={'sm'}>
                <DialogTitle>{'TODO Add Substitution'}</DialogTitle>
                <FormContainer formContext={formContext} onSuccess={onSubmit}>
                    <DialogContent dividers>
                        <Controller
                            name={'participantOut'}
                            rules={{
                                required: t('common.form.required'),
                            }}
                            render={({
                                field: {
                                    onChange: participantOutOnChange,
                                    value: participantOutValue = '',
                                },
                            }) => (
                                <Stack spacing={4}>
                                    <Select
                                        value={participantOutValue}
                                        onChange={e => {
                                            participantOutOnChange(e)
                                        }}>
                                        {subOutOptions.map(opt => (
                                            <MenuItem key={opt?.id} value={opt?.id}>
                                                {opt?.label}
                                            </MenuItem>
                                        ))}
                                    </Select>
                                    {participantOutValue && (
                                        <SubstitutionSelectParticipantIn
                                            setupRoundId={roundDto.setupRoundId}
                                            selectedParticipantOut={participantOutValue}
                                        />
                                    )}
                                </Stack>
                            )}
                        />
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeDialog} disabled={submitting}>
                            {t('common.cancel')}
                        </Button>
                        <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
        </>
    )
}

export default Substitutions

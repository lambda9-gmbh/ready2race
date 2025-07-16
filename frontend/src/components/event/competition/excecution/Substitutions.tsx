import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    List,
    ListItem,
    MenuItem,
    Select,
    Stack,
    Typography,
    useTheme,
} from '@mui/material'
import {CompetitionRoundDto, SubstitutionDto, SubstitutionParticipantDto} from '@api/types.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {addSubstitution, deleteSubstitution, getPossibleSubOuts} from '@api/sdk.gen.ts'
import {Fragment, useState} from 'react'
import BaseDialog from '@components/BaseDialog.tsx'
import {Controller, FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useTranslation} from 'react-i18next'
import SubstitutionSelectParticipantIn from '@components/event/competition/excecution/SubstitutionSelectParticipantIn.tsx'
import SouthIcon from '@mui/icons-material/South'
import NorthIcon from '@mui/icons-material/North'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import Add from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/Delete'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type SubstitutionWithSwap = {
    substitution: SubstitutionDto
    swapSubstitution?: SubstitutionDto
}

type Props = {
    reloadRoundDto: () => void
    roundDto: CompetitionRoundDto
    roundIndex: number
}

type Form = {
    participantIn: string
    participantOut: string
    reason: string
}
const Substitutions = ({reloadRoundDto, roundDto, roundIndex}: Props) => {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const theme = useTheme()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const {confirmAction} = useConfirmation()

    const substitutions: Array<SubstitutionWithSwap> = roundDto.substitutions
        .sort((a, b) => b.orderForRound - a.orderForRound)
        .reduce((acc, val) => {
            if (val.swapSubstitution) {
                if (acc.find(s => s.swapSubstitution?.id === val.id) === undefined) {
                    acc.push({
                        substitution: val,
                        swapSubstitution: roundDto.substitutions.find(
                            s => s.id === val.swapSubstitution,
                        ),
                    })
                }
            } else {
                acc.push({substitution: val})
            }
            return acc
        }, new Array<SubstitutionWithSwap>())

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

    const onSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {error} = await addSubstitution({
            path: {
                eventId: eventId,
                competitionId: competitionId,
            },
            body: {
                participantOut: formData.participantOut ?? '',
                participantIn: formData.participantIn ?? '',
                reason: takeIfNotEmpty(formData.reason),
            },
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('event.competition.execution.substitution.add.error'))
        } else {
            closeDialog()
            feedback.success(t('event.competition.execution.substitution.add.success'))
        }
        reloadRoundDto()
    }

    const participantName = (
        participant: SubstitutionParticipantDto,
        clubAndRegistration?: {clubName: string; registrationName: string},
    ) => {
        return (
            `${participant.firstName} ${participant.lastName}` +
            (clubAndRegistration
                ? ` (${clubAndRegistration?.clubName} ${clubAndRegistration?.registrationName})`
                : '')
        )
    }

    const handleDeleteSubstitution = async () => {
        confirmAction(
            async () => {
                setSubmitting(true)
                const {error} = await deleteSubstitution({
                    path: {
                        eventId: eventId,
                        competitionId: competitionId,
                    },
                })
                setSubmitting(false)

                if (error) {
                    feedback.error(t('event.competition.execution.substitution.delete.error'))
                } else {
                    feedback.success(t('event.competition.execution.substitution.delete.success'))
                }
                reloadRoundDto()
            },
            {
                content: t('event.competition.execution.substitution.delete.confirmation'),
                okText: t('common.delete'),
            },
        )
    }

    return (
        <>
            {roundIndex === 0 && (
                <Box sx={{my: 2, flex: 1, display: 'flex', justifyContent: 'end'}}>
                    <Button variant={'outlined'} onClick={openDialog} startIcon={<Add />}>
                        {t('event.competition.execution.substitution.add.add')}
                    </Button>
                </Box>
            )}
            <List>
                {substitutions.map((sub, subIdx) => (
                    <Fragment key={sub.substitution.id}>
                        <ListItem
                            sx={{
                                gap: 2,
                                [theme.breakpoints.down('lg')]: {
                                    flexDirection: 'column',
                                },
                            }}>
                            <Box
                                sx={{
                                    flex: 1,
                                    display: 'flex',
                                    gap: 2,
                                    [theme.breakpoints.down('lg')]: {
                                        flexDirection: 'column',
                                        alignItems: 'center',
                                        textAlign: 'center',
                                    },
                                }}>
                                <Typography sx={{flex: 1}}>
                                    {participantName(
                                        sub.substitution.participantIn,
                                        sub.swapSubstitution !== undefined
                                            ? {
                                                  clubName: sub.swapSubstitution.clubName,
                                                  registrationName:
                                                      sub.swapSubstitution
                                                          .competitionRegistrationName,
                                              }
                                            : undefined,
                                    )}
                                </Typography>
                                {sub.swapSubstitution !== undefined ? (
                                    <SwapHorizIcon sx={{px: 2}} />
                                ) : (
                                    <Box sx={{display: 'flex', gap: 1, px: 2}}>
                                        <SouthIcon />
                                        <Typography>
                                            {t('event.competition.execution.substitution.inFor')}
                                        </Typography>
                                        <NorthIcon />
                                    </Box>
                                )}
                                <Typography
                                    sx={{
                                        flex: 1,
                                        [theme.breakpoints.up('lg')]: {
                                            textAlign: 'end',
                                        },
                                    }}>
                                    {participantName(sub.substitution.participantOut, {
                                        clubName: sub.substitution.clubName,
                                        registrationName:
                                            sub.substitution.competitionRegistrationName,
                                    })}
                                </Typography>
                            </Box>
                            <Divider orientation={'vertical'} flexItem />
                            <Box
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    [theme.breakpoints.up('lg')]: {
                                        width: 250,
                                    },
                                    [theme.breakpoints.down('lg')]: {
                                        flexDirection: 'column',
                                    },
                                }}>
                                <Typography sx={{flex: 1, wordBreak: 'break-word'}} >{sub.substitution.reason}</Typography>
                                {subIdx === 0 && roundIndex === 0 && (
                                    <IconButton sx={{ml: 2}} onClick={handleDeleteSubstitution}>
                                        <DeleteIcon />
                                    </IconButton>
                                )}
                            </Box>
                        </ListItem>
                        {subIdx < substitutions.length - 1 && <Divider sx={{my: 1}} />}
                    </Fragment>
                ))}
            </List>

            <BaseDialog open={dialogOpen} onClose={closeDialog} maxWidth={'sm'}>
                <DialogTitle>{t('event.competition.execution.substitution.add.add')}</DialogTitle>
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
                                    <FormInputLabel
                                        label={t(
                                            'event.competition.execution.substitution.participant',
                                        )}
                                        required={true}>
                                        <Select
                                            value={participantOutValue}
                                            onChange={e => {
                                                participantOutOnChange(e)
                                            }}
                                            sx={{width: 1}}>
                                            {subOutOptions.map(opt => (
                                                <MenuItem key={opt?.id} value={opt?.id}>
                                                    {opt?.label}
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormInputLabel>
                                    {participantOutValue && (
                                        <SubstitutionSelectParticipantIn
                                            setupRoundId={roundDto.setupRoundId}
                                            selectedParticipantOut={participantOutValue}
                                        />
                                    )}
                                    <FormInputText
                                        name={'reason'}
                                        label={t('event.competition.execution.substitution.reason')}
                                    />
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

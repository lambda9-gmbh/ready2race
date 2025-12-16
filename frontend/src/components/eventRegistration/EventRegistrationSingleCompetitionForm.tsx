import {CheckboxButtonGroup, useFormContext, useWatch} from 'react-hook-form-mui'
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Chip,
    FormControlLabel,
    Paper,
    Stack,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from '@mui/material'
import * as React from 'react'
import {Fragment, ReactNode, useCallback, useEffect, useMemo, useState} from 'react'
import {
    CheckBox,
    CheckBoxOutlineBlank,
    FilterAlt,
    IndeterminateCheckBox,
    Person,
} from '@mui/icons-material'
import {EventRegistrationCompetitionDto, RatingCategoryToEventDto} from '../../api'
import {Trans, useTranslation} from 'react-i18next'
import {
    EventRegistrationFormData,
    EventRegistrationParticipantFormData,
} from '../../pages/eventRegistration/EventRegistrationCreatePage.tsx'
import {useEventRegistration} from '@contexts/eventRegistration/EventRegistrationContext.ts'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'

/**
 * Check if a rating category's age restriction is valid for a participant
 */
const isRatingCategoryValidForParticipant = (
    ratingCategory: RatingCategoryToEventDto,
    participant: EventRegistrationParticipantFormData,
): boolean => {
    // If no age restriction, category is valid for everyone
    if (!ratingCategory.yearFrom && !ratingCategory.yearTo) {
        return true
    }

    // If participant has no year, we can't validate - assume invalid
    if (!participant.year) {
        return false
    }

    const meetsMinAge = !ratingCategory.yearFrom || participant.year >= ratingCategory.yearFrom
    const meetsMaxAge = !ratingCategory.yearTo || participant.year <= ratingCategory.yearTo

    return meetsMinAge && meetsMaxAge
}

const EventSingleCompetitionField = (props: {
    option: EventRegistrationCompetitionDto
    participantIndex: number
    locked: boolean
    isLate: boolean
}) => {
    const {t} = useTranslation()
    const {ratingCategories} = useEventRegistration()

    const formContext = useFormContext<EventRegistrationFormData>()

    const [active, setActive] = useState(false)
    const [competitionIndex, setCompetitionIndex] = useState<number | undefined>(undefined)

    const singleCompetitions = useWatch({
        control: formContext.control,
        name: `participants.${props.participantIndex}.competitionsSingle`,
    })

    // Watch the current participant to get their birth year
    const participant = useWatch({
        control: formContext.control,
        name: `participants.${props.participantIndex}`,
    })

    useEffect(() => {
        let index = singleCompetitions?.findIndex(r => r.competitionId === props.option.id) ?? -1
        setCompetitionIndex(index !== -1 ? index : undefined)
        setActive(index !== -1)
    }, [singleCompetitions])

    const onChange = (checked: boolean) => {
        if (checked) {
            // Determine the rating category to use
            let selectedRatingCategory = props.option.ratingCategoryRequired ? '' : 'none'

            // If rating category is required, check if there's exactly one valid option
            if (props.option.ratingCategoryRequired) {
                const validRatingCategories = ratingCategories.filter(rc =>
                    isRatingCategoryValidForParticipant(rc, participant),
                )

                // Auto-select if there's exactly one valid rating category
                if (validRatingCategories.length === 1) {
                    selectedRatingCategory = validRatingCategories[0].ratingCategory.id
                }
            }

            formContext.setValue(`participants.${props.participantIndex}.competitionsSingle`, [
                ...(singleCompetitions ?? []),
                {
                    competitionId: props.option.id,
                    locked: false,
                    isLate: props.isLate,
                    optionalFees: [],
                    ratingCategory: selectedRatingCategory,
                },
            ])
        } else {
            formContext.setValue(
                `participants.${props.participantIndex}.competitionsSingle`,
                singleCompetitions?.filter(c => c.competitionId != props.option.id),
            )
        }
    }

    // Get the actual fee objects for selected optional fees
    const selectedOptionalFees = useMemo(() => {
        if (competitionIndex === undefined || !props.option.fees) return []
        const competition = singleCompetitions?.[competitionIndex]
        if (!competition?.optionalFees || competition.optionalFees.length === 0) return []
        return props.option.fees.filter(
            f => !f.required && competition.optionalFees?.includes(f.id),
        )
    }, [competitionIndex, singleCompetitions, props.option.fees])

    // Filter rating categories based on age restrictions
    const ratingCategoryOptions = useMemo(() => {
        const baseOptions = props.option.ratingCategoryRequired
            ? []
            : [
                  {
                      id: 'none',
                      label: t('common.form.select.none'),
                  },
              ]

        const filteredRatingCategories = ratingCategories.filter(rc =>
            isRatingCategoryValidForParticipant(rc, participant),
        )

        return [
            ...baseOptions,
            ...filteredRatingCategories.map(rc => ({
                id: rc.ratingCategory.id,
                label: rc.ratingCategory.name,
            })),
        ]
    }, [props.option.ratingCategoryRequired, ratingCategories, participant, t])

    return (
        <Stack
            direction={{xs: 'column', sm: 'row'}}
            sx={{alignItems: {xs: 'flex-start', sm: 'center'}, gap: {xs: 1, sm: 0}}}>
            <FormControlLabel
                control={<Checkbox />}
                disabled={props.locked || (props.isLate && !props.option.lateRegistrationAllowed)}
                checked={active}
                onChange={(_, checked) => onChange(checked)}
                sx={{cursor: 'pointer'}}
                label={
                    <Stack direction={'row'} alignItems={'center'} spacing={1} flexWrap={'wrap'}>
                        <Typography>{props.option.name}</Typography>
                        {props.option.competitionCategory && (
                            <Chip
                                variant={'outlined'}
                                label={props.option.competitionCategory}
                                size={'small'}
                            />
                        )}
                    </Stack>
                }
            />

            {active && (props.option.fees?.length ?? 0) > 0 && (
                <Box sx={{display: 'flex', gap: 4, flexWrap: 'wrap'}}>
                    {/* Fee Display */}
                    <Stack spacing={0.5} sx={{ml: {xs: 0, sm: 4}, mt: 0.5, pl: {xs: 1, sm: 0}}}>
                        {props.option.fees
                            ?.filter(f => f.required)
                            .map(fee => (
                                <Fragment key={fee.id}>
                                    <Typography variant="body2" color="text.secondary">
                                        {fee.label}:{' '}
                                        <strong>{Number(fee.amount).toFixed(2)}€</strong>
                                    </Typography>
                                    {props.option.lateRegistrationAllowed && fee.lateAmount && (
                                        <Typography variant="caption" sx={{ml: 1}}>
                                            (
                                            <Trans
                                                i18nKey={'event.competition.fee.asLate'}
                                                values={{
                                                    amount: Number(fee.lateAmount).toFixed(2),
                                                }}
                                            />
                                            )
                                        </Typography>
                                    )}
                                </Fragment>
                            ))}
                        {selectedOptionalFees.length > 0 &&
                            selectedOptionalFees.map(fee => (
                                <Fragment key={fee.id}>
                                    <Typography variant="body2" color="text.secondary">
                                        + {fee.label}:{' '}
                                        <strong>{Number(fee.amount).toFixed(2)}€</strong>
                                    </Typography>
                                    {props.option.lateRegistrationAllowed && fee.lateAmount && (
                                        <Typography variant="caption" sx={{ml: 1}}>
                                            (
                                            <Trans
                                                i18nKey={'event.competition.fee.asLate'}
                                                values={{
                                                    amount: Number(fee.lateAmount).toFixed(2),
                                                }}
                                            />
                                            )
                                        </Typography>
                                    )}
                                </Fragment>
                            ))}
                    </Stack>
                    <CheckboxButtonGroup
                        disabled={props.locked}
                        name={`participants.${props.participantIndex}.competitionsSingle.${competitionIndex}.optionalFees`}
                        options={props.option.fees?.filter(f => !f.required) ?? []}
                        row
                    />
                </Box>
            )}
            {active && ratingCategories.length > 0 && (
                <Box sx={{ml: {xs: 0, sm: 2}, my: 1, width: {xs: '100%', sm: 'auto'}}}>
                    <Typography variant={'subtitle2'}>
                        {t('event.competition.registration.ratingCategory')}
                    </Typography>
                    <FormInputSelect
                        name={`participants.${props.participantIndex}.competitionsSingle.${competitionIndex}.ratingCategory`}
                        options={ratingCategoryOptions}
                        required
                        variant={'standard'}
                        disabled={props.locked}
                    />
                </Box>
            )}
        </Stack>
    )
}

export const EventRegistrationSingleCompetitionForm = () => {
    const ALL_CATEGORIES = 'show_all_categories'

    const {t} = useTranslation()
    const [category, setCategory] = React.useState<string>(ALL_CATEGORIES)
    const formContext = useFormContext<EventRegistrationFormData>()
    const {info, ratingCategories} = useEventRegistration()

    const competitionsSingle: Map<string, Array<EventRegistrationCompetitionDto>> = useMemo(() => {
        return new Map([
            [
                'M',
                info?.competitionsSingle?.filter(
                    c =>
                        c.namedParticipant?.[0].countMales === 1 ||
                        c.namedParticipant?.[0].countMixed === 1 ||
                        c.namedParticipant?.[0].countNonBinary === 1,
                ) ?? [],
            ],
            [
                'F',
                info?.competitionsSingle?.filter(
                    c =>
                        c.namedParticipant?.[0].countFemales === 1 ||
                        c.namedParticipant?.[0].countMixed === 1 ||
                        c.namedParticipant?.[0].countNonBinary === 1,
                ) ?? [],
            ],
            ['D', info?.competitionsSingle ?? []],
        ])
    }, [info?.competitionsSingle])

    const participantList = useWatch({
        control: formContext.control,
        name: `participants`,
    })

    const handleChange = (_: any, newCategory: string | null) => {
        if (newCategory !== null) {
            setCategory(newCategory)
        }
    }

    const getToggleButtons = useCallback(() => {
        const allCategories = new Set(
            Array.from(competitionsSingle.values()).flatMap(competitions =>
                competitions.map(c => c.competitionCategory),
            ),
        )
        if (allCategories.size > 1) {
            return (
                <Alert icon={<FilterAlt />} color={'info'} sx={{mB: 2}}>
                    <Stack direction={{xs: 'column', sm: 'row'}} spacing={1} alignItems={'center'}>
                        <Typography>{t('event.competition.category.category')}</Typography>
                        <ToggleButtonGroup
                            size={'small'}
                            value={category}
                            exclusive
                            onChange={handleChange}
                            sx={{
                                flexWrap: 'wrap',
                                gap: 0.5,
                                '& .MuiToggleButton-root': {
                                    minWidth: {xs: '70px', sm: 'auto'},
                                },
                            }}>
                            <ToggleButton value={ALL_CATEGORIES}>{t('common.all')}</ToggleButton>
                            {Array.from(allCategories)
                                .filter(cat => cat !== undefined)
                                .map(cat => (
                                    <ToggleButton key={cat} value={cat}>
                                        {cat}
                                    </ToggleButton>
                                ))}
                        </ToggleButtonGroup>
                    </Stack>
                </Alert>
            )
        }
    }, [competitionsSingle, category])

    // Calculate selection state for "Select All" button
    const selectionState = useMemo(() => {
        let totalSelectableCompetitions = 0
        let totalSelectedCompetitions = 0

        participantList.forEach(participant => {
            const availableCompetitions = competitionsSingle.get(participant.gender ?? 'O') ?? []

            availableCompetitions.forEach(competition => {
                const isLocked =
                    participant.competitionsSingle?.find(c => c.competitionId === competition.id)
                        ?.locked === true
                const isDisabled =
                    isLocked || (info?.state === 'LATE' && !competition.lateRegistrationAllowed)

                if (!isDisabled) {
                    totalSelectableCompetitions++
                    const isSelected =
                        participant.competitionsSingle?.some(
                            c => c.competitionId === competition.id,
                        ) ?? false
                    if (isSelected) {
                        totalSelectedCompetitions++
                    }
                }
            })
        })

        if (totalSelectedCompetitions === 0) {
            return 'none'
        } else if (totalSelectedCompetitions === totalSelectableCompetitions) {
            return 'all'
        } else {
            return 'partial'
        }
    }, [participantList, competitionsSingle, info?.state])

    const handleSelectAll = useCallback(() => {
        const shouldSelect = selectionState !== 'all'

        participantList.forEach((participant, participantIndex) => {
            const availableCompetitions = competitionsSingle.get(participant.gender ?? 'O') ?? []

            if (shouldSelect) {
                // Select all non-locked, non-disabled competitions
                const competitionsToAdd = availableCompetitions
                    .filter(competition => {
                        const isLocked =
                            participant.competitionsSingle?.find(
                                c => c.competitionId === competition.id,
                            )?.locked === true
                        const isDisabled =
                            isLocked ||
                            (info?.state === 'LATE' && !competition.lateRegistrationAllowed)
                        const isAlreadySelected =
                            participant.competitionsSingle?.some(
                                c => c.competitionId === competition.id,
                            ) ?? false

                        return !isDisabled && !isAlreadySelected
                    })
                    .map(competition => {
                        // Determine the rating category to use
                        let selectedRatingCategory = competition.ratingCategoryRequired
                            ? ''
                            : 'none'

                        // If rating category is required, check if there's exactly one valid option
                        if (competition.ratingCategoryRequired) {
                            const validRatingCategories = ratingCategories.filter(rc =>
                                isRatingCategoryValidForParticipant(rc, participant),
                            )

                            // Auto-select if there's exactly one valid rating category
                            if (validRatingCategories.length === 1) {
                                selectedRatingCategory = validRatingCategories[0].ratingCategory.id
                            }
                        }

                        return {
                            competitionId: competition.id,
                            locked: false,
                            isLate: info?.state === 'LATE',
                            optionalFees: [],
                            ratingCategory: selectedRatingCategory,
                        }
                    })

                if (competitionsToAdd.length > 0) {
                    formContext.setValue(`participants.${participantIndex}.competitionsSingle`, [
                        ...(participant.competitionsSingle ?? []),
                        ...competitionsToAdd,
                    ])
                }
            } else {
                // Deselect all non-locked competitions
                const filteredCompetitions =
                    participant.competitionsSingle?.filter(c => c.locked === true) ?? []
                formContext.setValue(
                    `participants.${participantIndex}.competitionsSingle`,
                    filteredCompetitions,
                )
            }
        })
    }, [
        selectionState,
        participantList,
        competitionsSingle,
        info?.state,
        formContext,
        ratingCategories,
    ])

    const getSelectAllButton = () => {
        let icon: ReactNode
        let label = ''

        switch (selectionState) {
            case 'all':
                icon = <CheckBox />
                label = t('common.deselectAll')
                break
            case 'partial':
                icon = <IndeterminateCheckBox />
                label = t('common.selectAll')
                break
            case 'none':
            default:
                icon = <CheckBoxOutlineBlank />
                label = t('common.selectAll')
                break
        }

        return (
            <Button
                variant="outlined"
                startIcon={icon}
                onClick={handleSelectAll}
                sx={{cursor: 'pointer', minHeight: '44px', width: {xs: '100%', sm: 'auto'}}}>
                {label}
            </Button>
        )
    }

    return (
        <Stack spacing={2}>
            {getToggleButtons()}
            {participantList.length > 0 && <Box>{getSelectAllButton()}</Box>}
            {participantList.map((participant, index) => (
                <Paper sx={{p: {xs: 1, sm: 2}}} elevation={2} key={participant.id}>
                    <Stack direction="row" spacing={1}>
                        <Stack spacing={2} flex={1}>
                            <Stack direction="row" alignItems={'end'} spacing={1}>
                                <Person />
                                <Typography alignItems={'center'}>
                                    {`${participant.firstname} ${participant.lastname}`}
                                </Typography>
                            </Stack>
                            <Stack flex={1}>
                                {competitionsSingle.get(participant.gender ?? 'O')?.map(option => (
                                    <Box
                                        key={option.id}
                                        // We just hide the input, so fields are still validated
                                        hidden={
                                            category !== ALL_CATEGORIES &&
                                            option.competitionCategory !== category
                                        }>
                                        <EventSingleCompetitionField
                                            participantIndex={index}
                                            option={option}
                                            locked={
                                                participant.competitionsSingle?.find(
                                                    c => c.competitionId === option.id,
                                                )?.locked === true
                                            }
                                            isLate={info?.state === 'LATE'}
                                        />
                                    </Box>
                                ))}
                            </Stack>
                        </Stack>
                        <Typography
                            alignSelf={'end'}
                            variant={'overline'}
                            color={'grey'}
                            sx={{display: {xs: 'none', sm: 'block'}}}>
                            #{index + 1}
                        </Typography>
                    </Stack>
                </Paper>
            ))}
        </Stack>
    )
}

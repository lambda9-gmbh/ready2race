import * as React from 'react'
import {useCallback} from 'react'
import {Alert, Box, Stack, ToggleButton, ToggleButtonGroup, Typography} from '@mui/material'
import EventRegistrationTeamsForm from './EventRegistrationTeamsForm.tsx'
import {useTranslation} from 'react-i18next'
import {FilterAlt} from '@mui/icons-material'
import {useEventRegistration} from "@contexts/eventRegistration/EventRegistrationContext.ts";

export const EventRegistrationTeamCompetitionForm = () => {
    const ALL_CATEGORIES = 'show_all_categories'

    const {t} = useTranslation()
    const [category, setCategory] = React.useState<string>(ALL_CATEGORIES)

    const {info} = useEventRegistration()

    const handleChange = (_: any, newCategory: string | null) => {
        if (newCategory !== null) {
            setCategory(newCategory)
        }
    }

    const getToggleButtons = useCallback(() => {
        const allCategories = new Set(
            info?.competitionsTeam.map(c => c.competitionCategory),
        )
        if (allCategories.size > 1) {
            return (
                <Alert icon={<FilterAlt />} color={'info'} sx={{mB: 2}}>
                    <Stack direction={'row'} spacing={1} alignItems={'center'}>
                        <Typography>{t('event.competition.category.category')}</Typography>
                        <ToggleButtonGroup
                            size={'small'}
                            value={category}
                            exclusive
                            onChange={handleChange}>
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
    }, [info?.competitionsTeam, category])

    const getForms = useCallback(() => {
        return info?.competitionsTeam.map((competition, index) => {
            return (
                <Box
                    key={competition.id}
                    // We just hide the input, so fields are still validated
                    hidden={
                        category !== ALL_CATEGORIES && competition.competitionCategory !== category
                    }>
                    <EventRegistrationTeamsForm
                        index={index}
                        competition={competition}
                        isLate={info?.state === 'LATE'}
                    />
                </Box>
            )
        })
    }, [info?.competitionsTeam, category])

    return (
        <React.Fragment>
            {getToggleButtons()}
            <Stack>{getForms()}</Stack>
        </React.Fragment>
    )
}

export default EventRegistrationTeamCompetitionForm

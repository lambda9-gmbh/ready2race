import {Autocomplete, Box, TextField, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'

type FilterOption = {
    id: string
    label: string
}

type Props = {
    competitions: FilterOption[] | undefined | null
    selectedCompetition: FilterOption | null
    onCompetitionChange: (value: FilterOption | null) => void
    ratingCategories: FilterOption[] | undefined | null
    selectedRatingCategory: FilterOption | null
    onRatingCategoryChange: (value: FilterOption | null) => void
}

const ResultsFilterBar = ({
    competitions,
    selectedCompetition,
    onCompetitionChange,
    ratingCategories,
    selectedRatingCategory,
    onRatingCategoryChange,
}: Props) => {
    const {t} = useTranslation()
    const theme = useTheme()

    return (
        <Box
            sx={{
                display: 'flex',
                flexDirection: 'row',
                gap: 2,
                mb: 3,
                [theme.breakpoints.down('sm')]: {
                    flexDirection: 'column',
                },
            }}>
            <Autocomplete
                options={competitions || []}
                value={selectedCompetition}
                onChange={(_event, value) => onCompetitionChange(value)}
                getOptionLabel={option => option.label}
                isOptionEqualToValue={(option, value) => option.id === value.id}
                renderInput={params => (
                    <TextField
                        {...params}
                        label={t('event.competition.competition')}
                        size="small"
                    />
                )}
                sx={{flex: 1}}
            />
            <Autocomplete
                options={ratingCategories || []}
                value={selectedRatingCategory}
                onChange={(_event, value) => onRatingCategoryChange(value)}
                getOptionLabel={option => option.label}
                isOptionEqualToValue={(option, value) => option.id === value.id}
                renderInput={params => (
                    <TextField
                        {...params}
                        label={t('configuration.ratingCategory.ratingCategory')}
                        size="small"
                    />
                )}
                sx={{flex: 1}}
            />
        </Box>
    )
}

export default ResultsFilterBar
import {UseFormReturn} from 'react-hook-form-mui'
import {
    Box,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    CompetitionSetupForm,
    fillSeedingList,
    getHighestTeamsCount,
    getMatchupsString,
} from '@components/event/competition/setup/common.ts'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    roundIndex: number
    nextRoundTeams: number
    // Only the first bracket round can show a meaningful raw-seeding matchup preview (#a vs #b).
    showMatchupPreview: boolean
}

const MIN_COUNT = 1
const MAX_COUNT = 32

/**
 * Lets the organizer predefine match name + execution order per participant count N (the fixed bracket size).
 * Only deviations from the default (the match's own name/order) are stored in `matchNamings`.
 */
const CompetitionSetupRoundNaming = ({formContext, roundIndex, nextRoundTeams, showMatchupPreview}: Props) => {
    const {t} = useTranslation()

    const matches = formContext.watch(`rounds.${roundIndex}.matches`) ?? []
    const namings = formContext.watch(`rounds.${roundIndex}.matchNamings`) ?? []

    const [count, setCount] = useState<number>(Math.min(MAX_COUNT, Math.max(MIN_COUNT, nextRoundTeams || 8)))

    const findNaming = (weighting: number) =>
        namings.find(n => n.participantCount === count && n.matchWeighting === weighting)

    const setNaming = (weighting: number, field: 'name' | 'executionOrder', value: string | number | undefined) => {
        const others = namings.filter(
            n => !(n.participantCount === count && n.matchWeighting === weighting),
        )
        const existing = findNaming(weighting)
        const updated = {
            participantCount: count,
            matchWeighting: weighting,
            name: existing?.name ?? null,
            executionOrder: existing?.executionOrder ?? null,
            [field]: value === '' || value === undefined ? null : value,
        }
        const isEmpty =
            (updated.name === null || updated.name === undefined || updated.name === '') &&
            (updated.executionOrder === null || updated.executionOrder === undefined)

        formContext.setValue(
            `rounds.${roundIndex}.matchNamings`,
            isEmpty ? others : [...others, updated],
        )
    }

    // Raw-seeding preview: which seeds land in each match slot for the chosen participant count.
    const seedsPerSlot: number[][] = showMatchupPreview
        ? fillSeedingList(
              matches.length,
              getHighestTeamsCount(
                  matches.map(m => m.teams),
                  count,
              ),
              matches.map(m => Number(m.teams)),
              count,
          )
        : []

    if (matches.length < 1) {
        return null
    }

    return (
        <Stack spacing={1} sx={{maxWidth: 600}}>
            <Typography variant="h3">
                {t('event.competition.setup.naming.title')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
                {t('event.competition.setup.naming.hint')}
            </Typography>
            <Box sx={{maxWidth: 220}}>
                <TextField
                    type="number"
                    size="small"
                    label={t('event.competition.setup.naming.participantCount')}
                    value={count}
                    onChange={e => {
                        const v = Number(e.target.value)
                        if (!Number.isNaN(v)) {
                            setCount(Math.min(MAX_COUNT, Math.max(MIN_COUNT, Math.floor(v))))
                        }
                    }}
                    slotProps={{htmlInput: {min: MIN_COUNT, max: MAX_COUNT, step: 1}}}
                />
            </Box>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('event.competition.setup.match.match')}</TableCell>
                            {showMatchupPreview && (
                                <TableCell>
                                    {t('event.competition.setup.naming.matchup')}
                                </TableCell>
                            )}
                            <TableCell>{t('event.competition.setup.naming.name')}</TableCell>
                            <TableCell>{t('event.competition.setup.naming.order')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {matches.map((match, index) => {
                            const weighting = index + 1
                            const naming = findNaming(weighting)
                            return (
                                <TableRow key={weighting}>
                                    <TableCell>{match.name || `#${weighting}`}</TableCell>
                                    {showMatchupPreview && (
                                        <TableCell>
                                            {seedsPerSlot[index]?.length
                                                ? getMatchupsString(seedsPerSlot[index])
                                                : '–'}
                                        </TableCell>
                                    )}
                                    <TableCell>
                                        <TextField
                                            size="small"
                                            placeholder={match.name || `#${weighting}`}
                                            value={naming?.name ?? ''}
                                            onChange={e =>
                                                setNaming(weighting, 'name', e.target.value)
                                            }
                                        />
                                    </TableCell>
                                    <TableCell sx={{maxWidth: 90}}>
                                        <TextField
                                            type="number"
                                            size="small"
                                            placeholder={`${match.executionOrder}`}
                                            value={naming?.executionOrder ?? ''}
                                            onChange={e => {
                                                const v = e.target.value
                                                setNaming(
                                                    weighting,
                                                    'executionOrder',
                                                    v === '' ? undefined : Number(v),
                                                )
                                            }}
                                            slotProps={{htmlInput: {min: 1, step: 1}}}
                                        />
                                    </TableCell>
                                </TableRow>
                            )
                        })}
                    </TableBody>
                </Table>
            </TableContainer>
        </Stack>
    )
}

export default CompetitionSetupRoundNaming

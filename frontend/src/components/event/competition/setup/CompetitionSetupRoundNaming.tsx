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
import {useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    CompetitionSetupForm,
    getMatchupsString,
} from '@components/event/competition/setup/common.ts'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    roundIndex: number
    // Best-case global seed pairings per match (weighting order) at full capacity; filtered to the chosen N.
    matchupSeedings?: number[][]
}

const MIN_COUNT = 1
const MAX_COUNT = 32

/**
 * Lets the organizer predefine match name + execution order per participant count N (= the bracket size,
 * the number of teams entering the first bracket round). Only deviations from the default (the match's own
 * name/order) are stored in `matchNamings`.
 *
 * For a chosen N the matchup column shows the best-case seed pairing per match and only the matches that are
 * actually contested are listed (a match whose seeds are all above N is empty and therefore hidden).
 */
const CompetitionSetupRoundNaming = ({formContext, roundIndex, matchupSeedings}: Props) => {
    const {t} = useTranslation()

    const matches = formContext.watch(`rounds.${roundIndex}.matches`) ?? []
    const namings = formContext.watch(`rounds.${roundIndex}.matchNamings`) ?? []
    const requiredRound = formContext.watch(`rounds.${roundIndex}.required`) ?? false

    // In a non-required round a single-team match is an automatic bye (see automaticFirstPlace in the backend):
    // it is not contested and needs no name. A required round is always conducted, so single-team matches count.
    const minTeamsPerMatch = requiredRound ? 1 : 2

    // Teams entering this round saturate at its own capacity: for N >= capacity the round is full and behaves
    // like the default (e.g. a semifinal with capacity 4 is identical for N = 4, 7, 16). So overrides only make
    // sense for fewer teams. Only known when every match has a defined team count.
    const capacities = matches.map(m => Number(m.teams))
    const roundCapacity =
        matches.length > 0 && capacities.every(c => c > 0)
            ? capacities.reduce((acc, val) => acc + val, 0)
            : undefined

    // How many matches are contested for a given N (matches with at least minTeamsPerMatch real seeds).
    const contestedMatchCount = (n: number) =>
        matchupSeedings?.filter(seeds => seeds.filter(s => s <= n).length >= minTeamsPerMatch).length ?? 0

    // The selectable range is bounded to the N values that actually yield contested matches. This accounts for
    // the required flag: with byes excluded, a non-required round only has contested matches once N exceeds the
    // match count (e.g. a round of 16 with 8 matches starts at N = 9), and its full capacity stays the default.
    const highestRelevantCount = roundCapacity
        ? (() => {
              for (let n = roundCapacity - 1; n >= MIN_COUNT; n--) {
                  if (!matchupSeedings || contestedMatchCount(n) > 0) return n
              }
              return 0
          })()
        : MAX_COUNT
    const maxCount = Math.max(MIN_COUNT, highestRelevantCount)
    const minCount = roundCapacity
        ? (() => {
              for (let n = MIN_COUNT; n <= maxCount; n++) {
                  if (!matchupSeedings || contestedMatchCount(n) > 0) return n
              }
              return maxCount
          })()
        : MIN_COUNT
    const clamp = (v: number) => Math.min(maxCount, Math.max(minCount, Math.floor(v)))

    const [count, setCount] = useState<number>(clamp(highestRelevantCount || 8))

    // Keep the selected N within the valid range when it shifts (e.g. toggling required widens/narrows it).
    useEffect(() => {
        setCount(c => Math.min(maxCount, Math.max(minCount, c)))
    }, [minCount, maxCount])

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

    if (matches.length < 1) {
        return null
    }

    // No participant count yields a contested match (e.g. a non-required final): nothing to override.
    if (roundCapacity && highestRelevantCount === 0) {
        return null
    }

    // For the chosen N: seeds above N become byes/empty. A match is relevant when enough real seeds remain.
    const matchupForWeighting = (index: number): number[] | null => {
        const seeds = matchupSeedings?.[index]
        return seeds ? seeds.filter(s => s <= count) : null
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
                            setCount(clamp(v))
                        }
                    }}
                    slotProps={{htmlInput: {min: minCount, max: maxCount, step: 1}}}
                />
            </Box>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('event.competition.setup.match.match')}</TableCell>
                            <TableCell>{t('event.competition.setup.naming.matchup')}</TableCell>
                            <TableCell>{t('event.competition.setup.naming.name')}</TableCell>
                            <TableCell>{t('event.competition.setup.naming.order')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {matches.map((match, index) => {
                            const weighting = index + 1
                            const matchup = matchupForWeighting(index)
                            // Show only contested matches when seeding info is available: at least one real team
                            // for required rounds, at least two (no byes) for non-required rounds.
                            if (matchup !== null && matchup.length < minTeamsPerMatch) {
                                return null
                            }
                            const naming = findNaming(weighting)
                            return (
                                <TableRow key={weighting}>
                                    <TableCell>{match.name || `#${weighting}`}</TableCell>
                                    <TableCell>
                                        {matchup && matchup.length > 0
                                            ? getMatchupsString(matchup)
                                            : '–'}
                                    </TableCell>
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

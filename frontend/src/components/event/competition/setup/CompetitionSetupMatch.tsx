import {useFieldArray} from 'react-hook-form-mui'
import {
    Box,
    Card,
    Divider,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'
import CompetitionSetupParticipants from '@components/event/competition/setup/CompetitionSetupParticipants.tsx'
import {FormInputSeconds} from '@components/form/input/FormInputSeconds.tsx'
import {useTranslation} from 'react-i18next'
import {Info} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'

type Props = CompetitionSetupMatchOrGroupProps
const CompetitionSetupMatch = ({formContext, roundIndex, fieldInfo, ...props}: Props) => {
    const {t} = useTranslation()
    const {fields: participantFields} = useFieldArray({
        control: formContext.control,
        name: `rounds.${roundIndex}.matches.${fieldInfo.index}.participants`,
    })

    const watchParticipants = formContext.watch(
        `rounds.${roundIndex}.matches.${fieldInfo.index}.participants`,
    )

    const controlledParticipantFields = participantFields.map((field, index) => ({
        ...field,
        ...watchParticipants?.[index],
    }))

    const executionOrderOptions = formContext
        .getValues(`rounds.${roundIndex}.matches`)
        .map((_, index) => ({
            id: index + 1,
            label: index + 1,
        }))

    const watchTeams = formContext.watch(`rounds.${roundIndex}.matches.${fieldInfo.index}.teams`)

    return (
        <Card
            sx={{
                display: 'flex',
                flexDirection: 'column',
                gap: 2,
                p: 2,
                boxSizing: 'border-box',
            }}>
            {watchParticipants.length > 0 && (
                <>
                    <Box>
                        <Typography sx={{fontSize: '1.1rem'}}>
                            {t('event.competition.setup.match.participants')}:
                        </Typography>
                        <CompetitionSetupParticipants
                            fieldInfo={fieldInfo}
                            roundIndex={roundIndex}
                            controlledParticipantFields={controlledParticipantFields}
                            useDefaultSeeding={props.useDefaultSeeding}
                            updatePlaces={props.participantFunctions.updatePlaces}
                        />
                    </Box>
                    <Divider />
                </>
            )}
            <FormInputText
                name={`rounds.${roundIndex}.matches.${fieldInfo.index}.name`}
                label={t('event.competition.setup.match.name')}
            />
            <FormInputSelect
                name={`rounds.${roundIndex}.matches.${fieldInfo.index}.executionOrder`}
                label={t('event.competition.setup.match.executionOrder')}
                options={executionOrderOptions}
                required
                transform={{
                    output: value => Number(value.target.value),
                }}
            />
            {props.useStartTimeOffsets && (
                <FormInputSeconds
                    name={`rounds.${roundIndex}.matches.${fieldInfo.index}.startTimeOffset`}
                    label={t('event.competition.setup.startTimeOffset.startTimeOffset')}
                    transform={{
                        output: value =>
                            value.target.value !== '' ? Number(value.target.value) : undefined,
                    }}
                />
            )}
            <FormInputText
                name={`rounds.${roundIndex}.matches.${fieldInfo.index}.teams`}
                label={t('event.competition.setup.match.teams.teams')}
                onChange={v => {
                    onTeamsChanged(
                        roundIndex,
                        Number(v.target.value),
                        props.useDefaultSeeding,
                        props.participantFunctions,
                        props.teamCounts,
                        () => {
                            formContext.setValue(`rounds.${roundIndex}.placesOption`, 'EQUAL')
                        },
                    )
                }}
                rules={{
                    min: {
                        value: 1,
                        message: t('common.form.number.invalid.minOrUndefined', {min: 1}),
                    },
                }}
                placeholder={t('event.competition.setup.match.teams.unlimited')}
            />
            <Divider />
            <Typography sx={{fontSize: '1.1rem'}}>
                {t('event.competition.setup.match.outcome.outcomes')}:
            </Typography>
            <Stack direction={'row'} spacing={2}>
                <Typography>
                    {props.outcomes.join(', ') + (watchTeams === '' ? ', ...' : '')}
                </Typography>
                <HtmlTooltip
                    title={
                        <Stack p={1}>
                            <Typography fontWeight={500}>
                                {t('event.competition.setup.match.outcome.tooltip')}
                            </Typography>
                            <TableContainer>
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>
                                                {t('event.competition.setup.match.outcome.result')}{' '}
                                                ({t('event.competition.setup.match.match')})
                                            </TableCell>
                                            <TableCell>
                                                {t('event.competition.setup.match.outcome.outcome')}
                                            </TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {props.outcomes.map((outcome, index) => (
                                            <TableRow key={`${outcome}-${index}`}>
                                                <TableCell>{index + 1}</TableCell>
                                                <TableCell>{outcome}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        </Stack>
                    }>
                    <Info color={'info'} fontSize={'small'} />
                </HtmlTooltip>
            </Stack>
        </Card>
    )
}

export default CompetitionSetupMatch

import {
    Alert,
    Autocomplete,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    TextField,
    Typography,
    debounce,
} from '@mui/material'
import {useEffect, useMemo, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    addParticipantAdditionalClub,
    ClubSearchDto,
    getClubNames,
    ParticipantDto,
    removeParticipantAdditionalClub,
} from '@api/index.ts'
import {useFeedback} from '@utils/hooks.ts'

/**
 * Die weiteren Vereine einer Person pflegen.
 *
 * Nur der Stammverein sieht diesen Dialog (siehe ParticipantTable) — das Backend prüft es
 * unabhängig davon noch einmal. Die Person bleibt EIN Datensatz; hier wird nur festgelegt, wer
 * sie außerdem melden darf. Ihre Stammdaten ändert weiterhin allein der Stammverein.
 */
const ParticipantClubsDialog = (props: {
    open: boolean
    onClose: () => void
    clubId: string
    participant: ParticipantDto | null
    reload: () => void
}) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [options, setOptions] = useState<ClubSearchDto[]>([])
    const [selected, setSelected] = useState<ClubSearchDto | null>(null)
    const [inputValue, setInputValue] = useState('')
    const [pending, setPending] = useState(false)

    const search = useMemo(
        () =>
            debounce((term: string) => {
                getClubNames({
                    query: {
                        limit: 10,
                        offset: 0,
                        search: term,
                        sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}]),
                    },
                }).then(res => setOptions(res.data?.data ?? []))
            }, 400),
        [],
    )

    useEffect(() => {
        if (props.open) {
            search(inputValue)
        }
    }, [inputValue, props.open, search])

    useEffect(() => {
        if (props.open) {
            setSelected(null)
            setInputValue('')
        }
    }, [props.open])

    const participant = props.participant

    // Der Stammverein und die schon eingetragenen Vereine fallen aus der Auswahl — beides wäre
    // ein Fehler, den das Backend abweist, und ein angebotener Eintrag, der garantiert scheitert,
    // ist kein Angebot.
    const alreadyTaken = new Set([
        props.clubId,
        ...(participant?.additionalClubs.map(c => c.id) ?? []),
    ])

    const add = async () => {
        if (!participant || !selected) return
        setPending(true)
        const {error} = await addParticipantAdditionalClub({
            path: {
                clubId: props.clubId,
                participantId: participant.id,
                additionalClubId: selected.id,
            },
        })
        setPending(false)
        if (error) {
            feedback.error(t('club.participant.additionalClubs.addError'))
        } else {
            feedback.success(t('club.participant.additionalClubs.addSuccess'))
            setSelected(null)
            setInputValue('')
            props.reload()
        }
    }

    const remove = async (additionalClubId: string) => {
        if (!participant) return
        setPending(true)
        const {error} = await removeParticipantAdditionalClub({
            path: {clubId: props.clubId, participantId: participant.id, additionalClubId},
        })
        setPending(false)
        if (error) {
            feedback.error(t('club.participant.additionalClubs.removeError'))
        } else {
            feedback.success(t('club.participant.additionalClubs.removeSuccess'))
            props.reload()
        }
    }

    return (
        <Dialog open={props.open} onClose={props.onClose} fullWidth maxWidth={'sm'}>
            <DialogTitle>
                {t('club.participant.additionalClubs.title', {
                    name: participant ? `${participant.firstname} ${participant.lastname}` : '',
                })}
            </DialogTitle>
            <DialogContent>
                <Stack spacing={3} sx={{mt: 1}}>
                    <Alert severity={'info'}>
                        <Typography variant={'body2'}>
                            {t('club.participant.additionalClubs.hint')}
                        </Typography>
                    </Alert>

                    <Stack spacing={1}>
                        <Typography variant={'subtitle2'}>
                            {t('club.participant.additionalClubs.homeClub')}
                        </Typography>
                        <Typography variant={'body2'}>{participant?.clubName}</Typography>
                    </Stack>

                    <Stack spacing={1}>
                        <Typography variant={'subtitle2'}>
                            {t('club.participant.additionalClubs.current')}
                        </Typography>
                        {participant && participant.additionalClubs.length > 0 ? (
                            <Stack direction={'row'} flexWrap={'wrap'} gap={1}>
                                {participant.additionalClubs.map(club => (
                                    <Chip
                                        key={club.id}
                                        label={club.name}
                                        onDelete={() => remove(club.id)}
                                        disabled={pending}
                                        className={'cursor-pointer'}
                                    />
                                ))}
                            </Stack>
                        ) : (
                            <Typography variant={'body2'} color={'text.secondary'}>
                                {t('club.participant.additionalClubs.none')}
                            </Typography>
                        )}
                    </Stack>

                    <Stack direction={'row'} spacing={2} alignItems={'center'}>
                        <Autocomplete
                            sx={{flex: 1}}
                            size={'small'}
                            value={selected}
                            onChange={(_, value) => setSelected(value)}
                            inputValue={inputValue}
                            onInputChange={(_, value) => setInputValue(value)}
                            options={options.filter(o => !alreadyTaken.has(o.id))}
                            getOptionLabel={o => o.name}
                            isOptionEqualToValue={(a, b) => a.id === b.id}
                            renderInput={inputProps => (
                                <TextField
                                    {...inputProps}
                                    label={t('club.participant.additionalClubs.add')}
                                />
                            )}
                        />
                        <Button
                            variant={'contained'}
                            disabled={!selected || pending}
                            onClick={add}
                            className={'cursor-pointer'}>
                            {t('common.add')}
                        </Button>
                    </Stack>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={props.onClose} className={'cursor-pointer'}>
                    {t('common.close')}
                </Button>
            </DialogActions>
        </Dialog>
    )
}

export default ParticipantClubsDialog

import {useEffect, useMemo, useRef} from 'react'
import {Box, List, ListItem, ListItemButton, ListItemText, Typography} from '@mui/material'
import {Link} from '@tanstack/react-router'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEventSchedule} from '@api/sdk.gen.ts'
import {EventScheduleSlotDto} from '@api/types.gen.ts'
import {competitionTag, groupSlotsByDay, slotLabel} from '@components/event/schedule/common.ts'
import Throbber from '@components/Throbber.tsx'
import {CompetitionTab} from '@components/event/competition/common.ts'

type Props = {
    eventId: string
    /** Wettkampf der gerade offenen Seite - sein erster Slot wird hervorgehoben. */
    competitionId: string
    activeTab: CompetitionTab
    onNavigate: () => void
}

/** Läuft der Lauf gerade? Aktiviert (an den Start gerufen) zählt mit, sonst wäre das Fenster
 * zwischen Aufruf und Ist-Start unmarkiert - genau die Minuten, in denen die Boote am Start
 * stehen und jemand im Regattabüro auf den Slot schaut. */
const isUnderway = (slot: EventScheduleSlotDto): boolean =>
    (slot.matchActivatedAt != null || slot.matchStartedAt != null) && slot.matchFinishedAt == null

const CompetitionScheduleRail = ({eventId, competitionId, activeTab, onNavigate}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const listRef = useRef<HTMLUListElement | null>(null)

    const {data, pending} = useFetch(signal => getEventSchedule({signal, path: {eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.single', {entity: t('event.schedule.tab')}),
                )
            }
        },
        deps: [eventId],
    })

    const days = useMemo(() => groupSlotsByDay(data?.slots ?? []), [data])

    // Erster Slot des offenen Wettkampfs: der Zeitplan kennt mehrere Läufe pro Rennen, für die
    // Hervorhebung reicht der früheste - er ist der, an dem gerade gearbeitet wird.
    const markedSlotId = useMemo(() => {
        const own = (data?.slots ?? [])
            .filter(s => s.competitionId === competitionId)
            .sort((a, b) => a.startTime.localeCompare(b.startTime))
        return own[0]?.id ?? null
    }, [data, competitionId])

    useEffect(() => {
        const list = listRef.current
        const item = list?.querySelector<HTMLElement>('[data-marked="true"]')
        if (!list || !item) {
            return
        }
        const itemBottom = item.offsetTop + item.offsetHeight
        const outOfView =
            item.offsetTop < list.scrollTop || itemBottom > list.scrollTop + list.clientHeight
        if (outOfView) {
            list.scrollTop = item.offsetTop - (list.clientHeight - item.offsetHeight) / 2
        }
    }, [markedSlotId, days.length])

    if (pending && !data) {
        return (
            <Box sx={{p: 2}}>
                <Throbber />
            </Box>
        )
    }

    return (
        <List dense ref={listRef} sx={{flex: 1, overflowY: 'auto', py: 0}}>
            {days.map(day => (
                <li key={day.date}>
                    <Typography
                        variant={'overline'}
                        component={'div'}
                        sx={{
                            px: 2,
                            py: 0.5,
                            bgcolor: 'action.hover',
                            color: 'text.secondary',
                            position: 'sticky',
                            top: 0,
                            zIndex: 1,
                        }}>
                        {format(new Date(day.date), t('format.date'))}
                    </Typography>
                    <ul style={{padding: 0, margin: 0, listStyle: 'none'}}>
                        {day.slots.map(slot => {
                            const marked = slot.id === markedSlotId
                            const underway = isUnderway(slot)
                            // Freie Programmpunkte ohne Wettkampf sind keine Sprungziele.
                            const target = slot.competitionId
                            const label = [competitionTag(slot), slotLabel(slot, 'short')]
                                .filter(v => v)
                                .join(' · ')
                            const time = slot.startTime.slice(11, 16)

                            const content = (
                                <ListItem
                                    disablePadding
                                    data-marked={marked ? 'true' : undefined}
                                    sx={{
                                        borderLeft: 3,
                                        borderColor: underway
                                            ? 'success.main'
                                            : marked
                                              ? 'primary.main'
                                              : 'transparent',
                                        bgcolor: underway ? 'success.light' : undefined,
                                    }}>
                                    <ListItemButton
                                        selected={marked}
                                        disabled={!target}
                                        sx={{py: 0.5}}>
                                        <ListItemText
                                            primary={`${time}  ${label}`}
                                            slotProps={{
                                                primary: {
                                                    noWrap: true,
                                                    fontWeight: marked || underway ? 'bold' : undefined,
                                                    variant: 'body2',
                                                },
                                            }}
                                        />
                                    </ListItemButton>
                                </ListItem>
                            )

                            return target ? (
                                <Link
                                    key={slot.id}
                                    to={'/event/$eventId/competition/$competitionId'}
                                    params={{eventId, competitionId: target}}
                                    search={{tab: activeTab === 'general' ? undefined : activeTab}}
                                    onClick={onNavigate}
                                    title={`${time} ${slotLabel(slot)}`}>
                                    {content}
                                </Link>
                            ) : (
                                <Box key={slot.id}>{content}</Box>
                            )
                        })}
                    </ul>
                </li>
            ))}
            {days.length === 0 && (
                <ListItem>
                    <ListItemText secondary={t('common.noResults')} />
                </ListItem>
            )}
        </List>
    )
}

export default CompetitionScheduleRail

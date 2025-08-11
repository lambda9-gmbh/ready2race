import CompetitionTable from '@components/event/competition/CompetitionTable.tsx'
import {updateEventGlobal} from '@authorization/privileges.ts'
import InlineLink from '@components/InlineLink.tsx'
import CompetitionDialog from '@components/event/competition/CompetitionDialog.tsx'
import EventDayTable from '@components/event/eventDay/EventDayTable.tsx'
import EventDayDialog from '@components/event/eventDay/EventDayDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {CompetitionDto, EventDayDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {useUser} from '@contexts/user/UserContext.ts'
import {Stack} from '@mui/material'

const CompetitionsAndEventDays = () => {
    const {t} = useTranslation()
    const user = useUser()

    const competitionAdministrationProps = useEntityAdministration<CompetitionDto>(
        t('event.competition.competition'),
    )
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>(
        t('event.eventDay.eventDay'),
    )

    return (
        <Stack spacing={2}>
            <CompetitionTable
                {...competitionAdministrationProps.table}
                title={t('event.competition.competitions')}
                hints={
                    user.checkPrivilege(updateEventGlobal)
                        ? [
                              <>
                                  {t('event.competition.tableHint.templates.part1')}
                                  <InlineLink
                                      to={'/config'}
                                      search={{tab: 'competition-templates'}}>
                                      {t('event.competition.tableHint.templates.part2Link')}
                                  </InlineLink>
                                  {t('event.competition.tableHint.templates.part3')}
                              </>,
                              <>
                                  {t('event.competition.tableHint.competitionComponents.part1')}
                                  <InlineLink to={'/config'} search={{tab: 'competition-elements'}}>
                                      {t(
                                          'event.competition.tableHint.competitionComponents.part2Link',
                                      )}
                                  </InlineLink>
                                  {t('event.competition.tableHint.competitionComponents.part3')}
                              </>,
                          ]
                        : undefined
                }
            />
            <CompetitionDialog {...competitionAdministrationProps.dialog} />
            <EventDayTable
                {...eventDayAdministrationProps.table}
                title={t('event.eventDay.eventDays')}
            />
            <EventDayDialog {...eventDayAdministrationProps.dialog} />
        </Stack>
    )
}
export default CompetitionsAndEventDays

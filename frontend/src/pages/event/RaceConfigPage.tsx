import {Box, Stack} from '@mui/material'
import {useEntityAdministration} from '@utils/hooks.ts'
import RaceTemplateTable from '@components/event/race/template/RaceTemplateTable.tsx'
import RaceTemplateDialog from '@components/event/race/template/RaceTemplateDialog.tsx'
import {useTranslation} from 'react-i18next'
import RaceCategoryTable from '@components/event/race/category/RaceCategoryTable.tsx'
import RaceCategoryDialog from '@components/event/race/category/RaceCategoryDialog.tsx'
import NamedParticipantTable from '@components/event/race/namedParticipant/NamedParticipantTable.tsx'
import NamedParticipantDialog from '@components/event/race/namedParticipant/NamedParticipantDialog.tsx'
import {NamedParticipantDto, RaceCategoryDto, RaceTemplateDto} from "@api/types.gen.ts";

const RaceConfigPage = () => {
    const {t} = useTranslation()

    const raceTemplateAdministrationProps = useEntityAdministration<RaceTemplateDto>(
        t('event.race.template.template'),
    )
    const raceCategoryAdministrationProps = useEntityAdministration<RaceCategoryDto>(
        t('event.race.category.category'),
    )
    const namedParticipantAdministrationProps = useEntityAdministration<NamedParticipantDto>(
        t('event.race.namedParticipant.namedParticipant'),
    )

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            <Box sx={{mt: 4}}>
                <RaceTemplateTable
                    {...raceTemplateAdministrationProps.table}
                    title={t('event.race.template.templates')}
                />
                <RaceTemplateDialog {...raceTemplateAdministrationProps.dialog} />
            </Box>
            <Stack spacing={10} direction="row" justifyContent='space-between' sx={{mt: 4}}>
                <Box sx={{flex: 1}}>
                    <RaceCategoryTable
                        {...raceCategoryAdministrationProps.table}
                        title={t('event.race.category.categories')}
                    />
                    <RaceCategoryDialog {...raceCategoryAdministrationProps.dialog} />
                </Box>
                <Box sx={{flex: 1}}>
                    <NamedParticipantTable
                        {...namedParticipantAdministrationProps.table}
                        title={t('event.race.namedParticipant.namedParticipants')}
                    />
                    <NamedParticipantDialog {...namedParticipantAdministrationProps.dialog} />
                </Box>
            </Stack>
        </Box>
    )
}

export default RaceConfigPage

import {Box, Stack, Typography} from '@mui/material'
import {useEntityAdministration} from '../../utils/hooks.ts'
import {NamedParticipantDto, RaceCategoryDto, RaceTemplateDto} from '../../api'
import RaceTemplateTable from '../../components/event/race/template/RaceTemplateTable.tsx'
import RaceTemplateDialog from '../../components/event/race/template/RaceTemplateDialog.tsx'
import {useTranslation} from 'react-i18next'
import RaceCategoryTable from '../../components/event/race/category/RaceCategoryTable.tsx'
import RaceCategoryDialog from '../../components/event/race/category/RaceCategoryDialog.tsx'
import NamedParticipantTable from "../../components/event/race/namedParticipant/NamedParticipantTable.tsx";
import NamedParticipantDialog from "../../components/event/race/namedParticipant/NamedParticipantDialog.tsx";

const RaceConfigPage = () => {
    const {t} = useTranslation()

    const raceTemplateAdministrationProps = useEntityAdministration<RaceTemplateDto>()
    const raceCategoryAdministrationProps = useEntityAdministration<RaceCategoryDto>()
    const namedParticipantAdministrationProps = useEntityAdministration<NamedParticipantDto>()

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            <Box sx={{mt: 4}}>
                <Typography variant="h4">{t('event.race.template.templates')}</Typography>
                <RaceTemplateTable {...raceTemplateAdministrationProps} />
                <RaceTemplateDialog {...raceTemplateAdministrationProps} />
            </Box>
            <Stack spacing={4} direction="row" sx={{mt: 4}}>
                <Box>
                    <Typography variant="h4">{t('event.race.category.categories')}</Typography>
                    <RaceCategoryTable {...raceCategoryAdministrationProps} />
                    <RaceCategoryDialog {...raceCategoryAdministrationProps} />
                </Box>
                <Box>
                    <Typography variant="h4">{t('event.race.namedParticipant.namedParticipants')}</Typography>
                    <NamedParticipantTable {...namedParticipantAdministrationProps} />
                    <NamedParticipantDialog {...namedParticipantAdministrationProps} />
                </Box>
            </Stack>
        </Box>
    )
}

export default RaceConfigPage

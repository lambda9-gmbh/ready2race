import {Box, Stack} from '@mui/material'
import {useEntityAdministration} from '@utils/hooks.ts'
import CompetitionTemplateTable from '@components/event/competition/template/CompetitionTemplateTable.tsx'
import CompetitionTemplateDialog from '@components/event/competition/template/CompetitionTemplateDialog.tsx'
import {useTranslation} from 'react-i18next'
import CompetitionCategoryTable from '@components/event/competition/category/CompetitionCategoryTable.tsx'
import CompetitionCategoryDialog from '@components/event/competition/category/CompetitionCategoryDialog.tsx'
import NamedParticipantTable from '@components/event/competition/namedParticipant/NamedParticipantTable.tsx'
import NamedParticipantDialog from '@components/event/competition/namedParticipant/NamedParticipantDialog.tsx'
import {NamedParticipantDto, CompetitionCategoryDto, CompetitionTemplateDto} from "@api/types.gen.ts";

const CompetitionConfigPage = () => {
    const {t} = useTranslation()

    const competitionTemplateAdministrationProps = useEntityAdministration<CompetitionTemplateDto>(
        t('event.competition.template.template'),
    )
    const competitionCategoryAdministrationProps = useEntityAdministration<CompetitionCategoryDto>(
        t('event.competition.category.category'),
    )
    const namedParticipantAdministrationProps = useEntityAdministration<NamedParticipantDto>(
        t('event.competition.namedParticipant.namedParticipant'),
    )

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            <Box sx={{mt: 4}}>
                <CompetitionTemplateTable
                    {...competitionTemplateAdministrationProps.table}
                    title={t('event.competition.template.templates')}
                />
                <CompetitionTemplateDialog {...competitionTemplateAdministrationProps.dialog} />
            </Box>
            <Stack spacing={10} direction="row" justifyContent='space-between' sx={{mt: 4}}>
                <Box sx={{flex: 1}}>
                    <CompetitionCategoryTable
                        {...competitionCategoryAdministrationProps.table}
                        title={t('event.competition.category.categories')}
                    />
                    <CompetitionCategoryDialog {...competitionCategoryAdministrationProps.dialog} />
                </Box>
                <Box sx={{flex: 1}}>
                    <NamedParticipantTable
                        {...namedParticipantAdministrationProps.table}
                        title={t('event.competition.namedParticipant.namedParticipants')}
                    />
                    <NamedParticipantDialog {...namedParticipantAdministrationProps.dialog} />
                </Box>
            </Stack>
        </Box>
    )
}

export default CompetitionConfigPage

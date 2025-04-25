import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {
    CompetitionCategoryDto,
    CompetitionTemplateDto,
    EventDocumentTypeDto,
    FeeDto,
    NamedParticipantDto,
    ParticipantRequirementDto,
} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import ParticipantRequirementTable from '@components/event/participantRequirement/ParticipantRequirementTable.tsx'
import ParticipantRequirementDialog from '@components/event/participantRequirement/ParticipantRequirementDialog.tsx'
import {Stack, Tab, Typography} from '@mui/material'
import {useState} from 'react'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import TabPanel from '@components/tab/TabPanel.tsx'
import CompetitionCategoryTable from '@components/event/competition/category/CompetitionCategoryTable.tsx'
import CompetitionCategoryDialog from '@components/event/competition/category/CompetitionCategoryDialog.tsx'
import NamedParticipantTable from '@components/event/competition/namedParticipant/NamedParticipantTable.tsx'
import NamedParticipantDialog from '@components/event/competition/namedParticipant/NamedParticipantDialog.tsx'
import FeeTable from '@components/event/competition/fee/FeeTable.tsx'
import FeeDialog from '@components/event/competition/fee/FeeDialog.tsx'
import CompetitionTemplateTable from '@components/event/competition/template/CompetitionTemplateTable.tsx'
import CompetitionTemplateDialog from '@components/event/competition/template/CompetitionTemplateDialog.tsx'

const ConfigurationPage = () => {
    const {t} = useTranslation()

    const [activeTab, setActiveTab] = useState(0)

    const a11yProps = (index: number) => {
        return {
            id: `configuration-tab-${index}`,
            'aria-controls': `configuration-tabpanel-${index}`,
        }
    }

    const competitionTemplateAdministrationProps = useEntityAdministration<CompetitionTemplateDto>(
        t('event.competition.template.template'),
    )

    const competitionCategoryAdministrationProps = useEntityAdministration<CompetitionCategoryDto>(
        t('event.competition.category.category'),
    )
    const namedParticipantAdministrationProps = useEntityAdministration<NamedParticipantDto>(
        t('event.competition.namedParticipant.namedParticipant'),
    )
    const feeAdministrationProps = useEntityAdministration<FeeDto>(t('event.competition.fee.fee'))

    const documentTypeAdministrationProps = useEntityAdministration<EventDocumentTypeDto>(
        t('event.document.type.documentType'),
    )
    const participantRequirementAdministrationProps =
        useEntityAdministration<ParticipantRequirementDto>(
            t('participantRequirement.participantRequirement'),
        )

    return (
        <Stack spacing={4}>
            <Typography variant={'h1'}>{t('configuration.configuration')}</Typography>
            <TabSelectionContainer activeTab={activeTab} setActiveTab={setActiveTab}>
                <Tab label={t('configuration.tabs.competitionTemplates')} {...a11yProps(0)} />
                <Tab label={t('configuration.tabs.competitionElements')} {...a11yProps(1)} />
                <Tab label={t('configuration.tabs.eventElements')} {...a11yProps(2)} />
            </TabSelectionContainer>
            <TabPanel index={0} activeTab={activeTab}>
                <CompetitionTemplateTable
                    {...competitionTemplateAdministrationProps.table}
                    title={t('event.competition.template.templates')}
                />
                <CompetitionTemplateDialog {...competitionTemplateAdministrationProps.dialog} />
            </TabPanel>
            <TabPanel index={1} activeTab={activeTab}>
                <Stack spacing={2}>
                    <CompetitionCategoryTable
                        {...competitionCategoryAdministrationProps.table}
                        title={t('event.competition.category.categories')}
                    />
                    <CompetitionCategoryDialog {...competitionCategoryAdministrationProps.dialog} />
                    <NamedParticipantTable
                        {...namedParticipantAdministrationProps.table}
                        title={t('event.competition.namedParticipant.namedParticipants')}
                    />
                    <NamedParticipantDialog {...namedParticipantAdministrationProps.dialog} />
                    <FeeTable
                        {...feeAdministrationProps.table}
                        title={t('event.competition.fee.fees')}
                    />
                    <FeeDialog {...feeAdministrationProps.dialog} />
                </Stack>
            </TabPanel>
            <TabPanel index={2} activeTab={activeTab}>
                <Stack spacing={2}>
                    <DocumentTypeTable
                        {...documentTypeAdministrationProps.table}
                        title={t('event.document.type.documentTypes')}
                    />
                    <DocumentTypeDialog {...documentTypeAdministrationProps.dialog} />
                    <ParticipantRequirementTable
                        {...participantRequirementAdministrationProps.table}
                        title={t('participantRequirement.participantRequirements')}
                    />
                    <ParticipantRequirementDialog
                        {...participantRequirementAdministrationProps.dialog}
                    />
                </Stack>
            </TabPanel>
        </Stack>
    )
}

export default ConfigurationPage

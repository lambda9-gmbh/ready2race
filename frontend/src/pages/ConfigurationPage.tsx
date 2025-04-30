import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {
    CompetitionCategoryDto,
    CompetitionTemplateDto,
    DocumentTemplateDto,
    EventDocumentTypeDto,
    FeeDto,
    NamedParticipantDto,
    ParticipantRequirementDto,
} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import ParticipantRequirementTable from '@components/event/participantRequirement/ParticipantRequirementTable.tsx'
import ParticipantRequirementDialog from '@components/event/participantRequirement/ParticipantRequirementDialog.tsx'
import {Stack, Tab, Typography} from '@mui/material'
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
import {configurationIndexRoute} from '@routes'
import {useNavigate} from '@tanstack/react-router'
import DocumentTemplateTable from "@components/documentTemplate/DocumentTemplateTable.tsx";
import DocumentTemplateDialog from "@components/documentTemplate/DocumentTemplateDialog.tsx";
import AssignDocumentTemplate from "@components/documentTemplate/AssignDocumentTemplate.tsx";
import InlineLink from "@components/InlineLink.tsx";

const ConfigurationPage = () => {
    const {t} = useTranslation()

    const {tabIndex} = configurationIndexRoute.useSearch()
    const activeTab = tabIndex ?? 0

    const navigate = useNavigate()
    const switchTab = (tabIndex: number) => {
        navigate({from: configurationIndexRoute.fullPath, search: {tabIndex: tabIndex}}).then()
    }

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

    const documentTemplateAdministrationProps =
        useEntityAdministration<DocumentTemplateDto>(
            t('document.template.template'),
            {entityUpdate: false}
        )

    return (
        <Stack spacing={4}>
            <Typography variant={'h1'}>{t('configuration.configuration')}</Typography>
            <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                <Tab label={t('configuration.tabs.competitionTemplates')} {...a11yProps(0)} />
                <Tab label={t('configuration.tabs.competitionElements')} {...a11yProps(1)} />
                <Tab label={t('configuration.tabs.eventElements')} {...a11yProps(2)} />
                <Tab label={t('configuration.tabs.globalSettings')} {...a11yProps(3)} />
            </TabSelectionContainer>
            <TabPanel index={0} activeTab={activeTab}>
                <CompetitionTemplateTable
                    {...competitionTemplateAdministrationProps.table}
                    title={t('event.competition.template.templates')}
                    hints={[t('event.competition.template.tableHint')]}
                />
                <CompetitionTemplateDialog {...competitionTemplateAdministrationProps.dialog} />
            </TabPanel>
            <TabPanel index={1} activeTab={activeTab}>
                <Stack spacing={2}>
                    <CompetitionCategoryTable
                        {...competitionCategoryAdministrationProps.table}
                        title={t('event.competition.category.categories')}
                        hints={[t('event.competition.category.tableHint')]}
                    />
                    <CompetitionCategoryDialog {...competitionCategoryAdministrationProps.dialog} />
                    <NamedParticipantTable
                        {...namedParticipantAdministrationProps.table}
                        title={t('event.competition.namedParticipant.namedParticipants')}
                        hints={[t('event.competition.namedParticipant.tableHint')]}
                    />
                    <NamedParticipantDialog {...namedParticipantAdministrationProps.dialog} />
                    <FeeTable
                        {...feeAdministrationProps.table}
                        title={t('event.competition.fee.fees')}
                        hints={[t('event.competition.fee.tableHint')]}
                    />
                    <FeeDialog {...feeAdministrationProps.dialog} />
                </Stack>
            </TabPanel>
            <TabPanel index={2} activeTab={activeTab}>
                <Stack spacing={2}>
                    <DocumentTypeTable
                        {...documentTypeAdministrationProps.table}
                        title={t('event.document.type.documentTypes')}
                        hints={[t('event.document.type.tableHint')]}
                    />
                    <DocumentTypeDialog {...documentTypeAdministrationProps.dialog} />
                    <ParticipantRequirementTable
                        {...participantRequirementAdministrationProps.table}
                        title={t('participantRequirement.participantRequirements')}
                        hints={[t('participantRequirement.tableHint')]}
                    />
                    <ParticipantRequirementDialog
                        {...participantRequirementAdministrationProps.dialog}
                    />
                    <DocumentTemplateTable
                        {...documentTemplateAdministrationProps.table}
                        title={t('document.template.templates')}
                        hints={[
                            t('document.template.tableHint.1'),
                            <>
                                {t('document.template.tableHint.2')}
                                <InlineLink to={'/config'} search={{tabIndex: 3}}>
                                    {t('document.template.tableHint.3')}
                                </InlineLink>
                                {t('document.template.tableHint.4')}
                            </>
                        ]}
                    />
                    <DocumentTemplateDialog
                        {...documentTemplateAdministrationProps.dialog}
                    />
                </Stack>
            </TabPanel>
            <TabPanel index={3} activeTab={activeTab}>
                <Stack spacing={2}>
                    <AssignDocumentTemplate />
                </Stack>
            </TabPanel>
        </Stack>
    )
}

export default ConfigurationPage

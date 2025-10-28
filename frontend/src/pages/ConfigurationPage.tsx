import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {
    BankAccountDto,
    CompetitionCategoryDto,
    CompetitionSetupTemplateDto,
    CompetitionTemplateDto,
    ContactInformationDto,
    DocumentTemplateDto,
    EventDocumentTypeDto,
    FeeDto,
    NamedParticipantDto,
    ParticipantRequirementDto,
    WorkTypeDto,
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
// TODO: @Improve, @Discussion: Maybe extract TabContents into several Components
import DocumentTemplateTable from '@components/documentTemplate/DocumentTemplateTable.tsx'
import DocumentTemplateDialog from '@components/documentTemplate/DocumentTemplateDialog.tsx'
import InlineLink from '@components/InlineLink.tsx'
import CompetitionSetupTemplateTable from '@components/event/competition/setup/template/CompetitionSetupTemplateTable.tsx'
import CompetitionSetupTemplateDialog from '@components/event/competition/setup/template/CompetitionSetupTemplateDialog.tsx'
import BankAccountTable from '@components/bankAccount/BankAccountTable.tsx'
import BankAccountDialog from '@components/bankAccount/BankAccountDialog.tsx'
import ContactInformationTable from '@components/contactInformation/ContactInformationTable.tsx'
import ContactInformationDialog from '@components/contactInformation/ContactInformationDialog.tsx'
import WorkTypeDialog from '@components/work/WorkTypeDialog.tsx'
import WorkTypeTable from '@components/work/WorkTypeTable.tsx'
import StartListConfigPanel from '@components/startListConfig/StartListConfigPanel.tsx'
import {a11yProps} from '@utils/helpers.ts'
import RatingCategoryPanel from '@components/ratingCategory/RatingCategoryPanel.tsx'
import MatchResultImportConfigPanel from '@components/matchResultImportConfig/MatchResultImportConfigPanel.tsx'
import GlobalConfigurationsTab from '@components/configurations/GlobalConfigurationsTab.tsx'
import WebDavExportImport from '@components/configurations/WebDavExportImport.tsx'
import {readWebDavGlobal} from '@authorization/privileges.ts'
import {useUser} from '@contexts/user/UserContext.ts'

const CONFIGURATION_TABS = [
    'competition-templates',
    'competition-elements',
    'event-elements',
    'global-settings',
    'export-data',
] as const
export type ConfigurationTab = (typeof CONFIGURATION_TABS)[number]

const ConfigurationPage = () => {
    const {t} = useTranslation()
    const user = useUser()

    const {tab} = configurationIndexRoute.useSearch()
    const activeTab = tab ?? 'competition-templates'

    const navigate = useNavigate()
    const switchTab = (tab: ConfigurationTab) => {
        navigate({from: configurationIndexRoute.fullPath, search: {tab}}).then()
    }

    const tabProps = (tab: ConfigurationTab) => a11yProps('configuration', tab)

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

    const competitionSetupTemplateProps = useEntityAdministration<CompetitionSetupTemplateDto>(
        t('event.competition.setup.template.template'),
    )

    const documentTemplateAdministrationProps = useEntityAdministration<DocumentTemplateDto>(
        t('document.template.template'),
    )

    const workTypeAdministrationProps = useEntityAdministration<WorkTypeDto>(t('work.type.type'))

    const bankAccountAdministrationProps = useEntityAdministration<BankAccountDto>(
        t('invoice.bank.account'),
    )

    const contactInformationAdministrationProps = useEntityAdministration<ContactInformationDto>(
        t('contact.information'),
    )

    return (
        <Stack spacing={4}>
            <Typography variant={'h1'}>{t('configuration.configuration')}</Typography>
            <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                <Tab
                    label={t('configuration.tabs.competitionTemplates')}
                    {...tabProps('competition-templates')}
                />
                <Tab
                    label={t('configuration.tabs.competitionElements')}
                    {...tabProps('competition-elements')}
                />
                <Tab
                    label={t('configuration.tabs.eventElements')}
                    {...tabProps('event-elements')}
                />
                <Tab
                    label={t('configuration.tabs.globalSettings')}
                    {...tabProps('global-settings')}
                />
                {user.checkPrivilege(readWebDavGlobal) && (
                    <Tab label={t('configuration.tabs.exportData')} {...tabProps('export-data')} />
                )}
            </TabSelectionContainer>
            <TabPanel index={'competition-templates'} activeTab={activeTab}>
                <CompetitionTemplateTable
                    {...competitionTemplateAdministrationProps.table}
                    title={t('event.competition.template.templates')}
                    hints={[t('event.competition.template.tableHint')]}
                />
                <CompetitionTemplateDialog {...competitionTemplateAdministrationProps.dialog} />
            </TabPanel>
            <TabPanel index={'competition-elements'} activeTab={activeTab}>
                <Stack spacing={2}>
                    <CompetitionCategoryTable
                        {...competitionCategoryAdministrationProps.table}
                        title={t('event.competition.category.categories')}
                        hints={[t('event.competition.category.tableHint')]}
                    />
                    <CompetitionCategoryDialog {...competitionCategoryAdministrationProps.dialog} />
                    <RatingCategoryPanel />
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
                    <CompetitionSetupTemplateTable
                        {...competitionSetupTemplateProps.table}
                        title={t('event.competition.setup.template.templates')}
                        hints={[t('event.competition.setup.tableHint')]}
                    />
                    <CompetitionSetupTemplateDialog {...competitionSetupTemplateProps.dialog} />
                    <StartListConfigPanel />
                    <MatchResultImportConfigPanel />
                </Stack>
            </TabPanel>
            <TabPanel index={'event-elements'} activeTab={activeTab}>
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
                                <InlineLink to={'/config'} search={{tab: 'global-settings'}}>
                                    {t('document.template.tableHint.3')}
                                </InlineLink>
                                {t('document.template.tableHint.4')}
                            </>,
                        ]}
                    />
                    <DocumentTemplateDialog {...documentTemplateAdministrationProps.dialog} />
                    <WorkTypeTable
                        {...workTypeAdministrationProps.table}
                        id={'worktypes'}
                        title={t('work.type.types')}
                        hints={[t('work.type.tableHint')]}
                    />
                    <WorkTypeDialog {...workTypeAdministrationProps.dialog} />
                    <ContactInformationTable
                        {...contactInformationAdministrationProps.table}
                        title={t('contact.contacts')}
                        hints={[
                            t('contact.tableHint.1'),
                            <>
                                {t('contact.tableHint.2')}
                                <InlineLink to={'/config'} search={{tab: 'global-settings'}}>
                                    {t('contact.tableHint.3')}
                                </InlineLink>
                                {t('contact.tableHint.4')}
                            </>,
                        ]}
                    />
                    <ContactInformationDialog {...contactInformationAdministrationProps.dialog} />
                    <BankAccountTable
                        {...bankAccountAdministrationProps.table}
                        title={t('invoice.bank.accounts')}
                        hints={[
                            t('invoice.bank.tableHint.1'),
                            <>
                                {t('invoice.bank.tableHint.2')}
                                <InlineLink to={'/config'} search={{tab: 'global-settings'}}>
                                    {t('invoice.bank.tableHint.3')}
                                </InlineLink>
                                {t('invoice.bank.tableHint.4')}
                            </>,
                        ]}
                    />
                    <BankAccountDialog {...bankAccountAdministrationProps.dialog} />
                </Stack>
            </TabPanel>
            <TabPanel index={'global-settings'} activeTab={activeTab}>
                <GlobalConfigurationsTab />
            </TabPanel>
            {user.checkPrivilege(readWebDavGlobal) && (
                <TabPanel index={'export-data'} activeTab={activeTab}>
                    <WebDavExportImport />
                </TabPanel>
            )}
        </Stack>
    )
}

export default ConfigurationPage

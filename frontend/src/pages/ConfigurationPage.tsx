import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {DocumentTemplateDto, EventDocumentTypeDto, ParticipantRequirementDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import ParticipantRequirementTable from '@components/event/participantRequirement/ParticipantRequirementTable.tsx'
import ParticipantRequirementDialog from '@components/event/participantRequirement/ParticipantRequirementDialog.tsx'
import {Stack} from '@mui/material'
import DocumentTemplateTable from "@components/documentTemplate/DocumentTemplateTable.tsx";
import DocumentTemplateDialog from "@components/documentTemplate/DocumentTemplateDialog.tsx";
import AssignDocumentTemplate from "@components/documentTemplate/AssignDocumentTemplate.tsx";

const ConfigurationPage = () => {
    const {t} = useTranslation()
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
            <DocumentTypeTable
                {...documentTypeAdministrationProps.table}
                title={t('event.document.type.documentTypes')}
            />
            <DocumentTypeDialog {...documentTypeAdministrationProps.dialog} />
            <ParticipantRequirementTable
                {...participantRequirementAdministrationProps.table}
                title={t('participantRequirement.participantRequirements')}
            />
            <ParticipantRequirementDialog {...participantRequirementAdministrationProps.dialog} />
            <DocumentTemplateTable
                {...documentTemplateAdministrationProps.table}
                title={t('document.template.templates')}
            />
            <DocumentTemplateDialog
                {...documentTemplateAdministrationProps.dialog}
            />
            <AssignDocumentTemplate />
        </Stack>
    )
}

export default ConfigurationPage

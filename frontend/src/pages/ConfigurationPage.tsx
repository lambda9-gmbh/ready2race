import DocumentTypeTable from '@components/event/document/type/DocumentTypeTable.tsx'
import DocumentTypeDialog from '@components/event/document/type/DocumentTypeDialog.tsx'
import {useEntityAdministration} from '@utils/hooks.ts'
import {EventDocumentTypeDto} from '@api/types.gen.ts'

const ConfigurationPage = () => {
    const documentTypeAdministrationProps =
        useEntityAdministration<EventDocumentTypeDto>('[todo] document type')

    return (
        <>
            <DocumentTypeTable {...documentTypeAdministrationProps.table} />
            <DocumentTypeDialog {...documentTypeAdministrationProps.dialog} />
        </>
    )
}

export default ConfigurationPage

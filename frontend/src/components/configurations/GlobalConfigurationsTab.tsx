import AssignDocumentTemplate from '@components/documentTemplate/AssignDocumentTemplate.tsx'
import AssignBankAccount from '@components/bankAccount/AssignBankAccount.tsx'
import AssignContactInformation from '@components/contactInformation/AssignContactInformation.tsx'
import {Stack} from '@mui/material'
import AssignGapDocumentTemplate from '@components/gapDocumentTemplate/AssignGapDocumentTemplate.tsx'

const GlobalConfigurationsTab = () => {
    return (
        <Stack spacing={2}>
            <AssignDocumentTemplate />
            <AssignGapDocumentTemplate />
            <AssignBankAccount />
            <AssignContactInformation />
        </Stack>
    )
}

export default GlobalConfigurationsTab

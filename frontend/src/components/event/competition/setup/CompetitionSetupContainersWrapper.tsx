import {PropsWithChildren, RefObject} from 'react'
import {FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {CompetitionSetupForm} from '@components/event/competition/setup/common.ts'
import {Box} from "@mui/material";

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    handleFormSubmission: boolean
    treeHelperPortalContainer?: RefObject<HTMLDivElement>
    handleSubmit?: (formData: CompetitionSetupForm) => Promise<void>
}
// This component is necessary to handle both EntityDialog (has its own FormContainer) and Custom FormContainer
// CompetitionSetup has its own Container, CompetitionSetupTemplate uses EntityDialog
const CompetitionSetupContainersWrapper = ({children, ...props}: PropsWithChildren<Props>) => {
    return (
        <Box>
            {props.handleFormSubmission ? (
                <>
                    <div ref={props.treeHelperPortalContainer} />
                    <FormContainer
                        formContext={props.formContext}
                        onSuccess={props.handleSubmit}
                        FormProps={{style: {width: '100%'}}}>
                        {children}
                    </FormContainer>
                </>
            ) : (
                <>{children}</>
            )}
        </Box>
    )
}
export default CompetitionSetupContainersWrapper

import {PropsWithChildren, RefObject} from 'react'
import {FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {CompetitionSetupForm} from '@components/event/competition/setup/common.ts'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    handleFormSubmission: boolean
    treeHelperPortalContainer?: RefObject<HTMLDivElement>
    handleSubmit?: (formData: CompetitionSetupForm) => Promise<void>
}
const CompetitionSetupContainersWrapper = ({children, ...props}: PropsWithChildren<Props>) => {
    return (
        <>
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
        </>
    )
}
export default CompetitionSetupContainersWrapper

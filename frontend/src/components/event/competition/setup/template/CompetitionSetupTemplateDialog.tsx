import {BaseEntityDialogProps} from '@utils/types.ts'
import {CompetitionSetupTemplateDto} from '@api/types.gen.ts'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useForm} from 'react-hook-form-mui'
import {
    CompetitionSetupForm,
    mapCompetitionSetupTemplateDtoToForm,
    mapFormToCompetitionSetupTemplateRequest
} from '@components/event/competition/setup/common.ts'
import {addCompetitionSetupTemplate, updateCompetitionSetupTemplate} from '@api/sdk.gen.ts'
import {useCallback, useRef} from 'react'
import CompetitionSetup from '@components/event/competition/setup/CompetitionSetup.tsx'

const CompetitionSetupTemplateDialog = (
    props: BaseEntityDialogProps<CompetitionSetupTemplateDto>,
) => {

    const addAction = (formData: CompetitionSetupForm) => {
        return addCompetitionSetupTemplate({
            body: mapFormToCompetitionSetupTemplateRequest(formData),
        })
    }

    const editAction = (
        formData: CompetitionSetupForm,
        entity: CompetitionSetupTemplateDto,
    ) => {
        return updateCompetitionSetupTemplate({
            path: {competitionSetupTemplateId: entity.id},
            body: mapFormToCompetitionSetupTemplateRequest(formData),
        })
    }

    const defaultValues: CompetitionSetupForm = {
        name: '',
        description: '',
        rounds: [],
    }

    const formContext = useForm<CompetitionSetupForm>()

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity ? mapCompetitionSetupTemplateDtoToForm(props.entity) : defaultValues,
        )
    }, [props.entity])

    // This allows the Tournament Tree Generator Form to exist outside the CompetitionSetup Form while being rendered inside
    const treeHelperPortalContainer = useRef<HTMLDivElement>(null)

    return (
        <>
            <div ref={treeHelperPortalContainer} />
            <EntityDialog
                {...props}
                formContext={formContext}
                onOpen={onOpen}
                addAction={addAction}
                editAction={editAction}
                wide>
                <Stack spacing={4}>
                    <FormInputText name="name" label={'[todo] Name'} required />
                    <FormInputText name="description" label={'[todo] Description'} />
                    <CompetitionSetup
                        formContext={formContext}
                        handleFormSubmission={false}
                        treeHelperPortalContainer={treeHelperPortalContainer}
                    />
                </Stack>
            </EntityDialog>
        </>
    )
}

export default CompetitionSetupTemplateDialog

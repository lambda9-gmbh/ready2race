import {BaseEntityDialogProps} from '@utils/types.ts'
import {ParticipantRequirementDto, ParticipantRequirementUpsertDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {addParticipantRequirement, updateParticipantRequirement} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'

type ParticipantRequirementForm = {
    name: string
    description: string
    publicNote: string
    optional: boolean
    checkInApp: boolean
    publiclyVisible: boolean
    checkEarliestMinutesBefore: string
    checkLatestMinutesBefore: string
}

const ParticipantRequirementDialog = (props: BaseEntityDialogProps<ParticipantRequirementDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: ParticipantRequirementForm) => {
        return addParticipantRequirement({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (
        formData: ParticipantRequirementForm,
        entity: ParticipantRequirementDto,
    ) => {
        return updateParticipantRequirement({
            path: {participantRequirementId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: ParticipantRequirementForm = {
        name: '',
        description: '',
        publicNote: '',
        optional: false,
        checkInApp: false,
        publiclyVisible: false,
        checkEarliestMinutesBefore: '',
        checkLatestMinutesBefore: '',
    }

    const formContext = useForm<ParticipantRequirementForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name="name" label={t('event.name')} required />
                <FormInputText name="description" label={t('entity.description')} />
                {/* Getrennt von der Beschreibung: die ist die interne Arbeitsanweisung, dieser
                    Text geht wörtlich an die Athleten in "Mein Event". */}
                <FormInputText
                    name="publicNote"
                    label={t('participantRequirement.publicNote')}
                    helperText={t('participantRequirement.publicNoteHint')}
                />
                <FormInputCheckbox name="optional" label={t('entity.optional')} />
                <FormInputCheckbox name="checkInApp" label={t('participantRequirement.checkInApp')} />
                <FormInputCheckbox
                    name="publiclyVisible"
                    label={t('participantRequirement.publiclyVisible')}
                />
                <FormInputNumber
                    name="checkEarliestMinutesBefore"
                    label={t('participantRequirement.checkEarliestMinutesBefore')}
                    min={1}
                    integer
                />
                <FormInputNumber
                    name="checkLatestMinutesBefore"
                    label={t('participantRequirement.checkLatestMinutesBefore')}
                    min={1}
                    integer
                />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: ParticipantRequirementForm): ParticipantRequirementUpsertDto {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        publicNote: takeIfNotEmpty(formData.publicNote),
        optional: formData.optional,
        checkInApp: formData.checkInApp,
        publiclyVisible: formData.publiclyVisible,
        checkEarliestMinutesBefore:
            formData.checkEarliestMinutesBefore !== ''
                ? Number(formData.checkEarliestMinutesBefore)
                : undefined,
        checkLatestMinutesBefore:
            formData.checkLatestMinutesBefore !== ''
                ? Number(formData.checkLatestMinutesBefore)
                : undefined,
    }
}

function mapDtoToForm(dto: ParticipantRequirementDto): ParticipantRequirementForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        publicNote: dto.publicNote ?? '',
        optional: dto.optional,
        checkInApp: dto.checkInApp,
        publiclyVisible: dto.publiclyVisible,
        checkEarliestMinutesBefore: dto.checkEarliestMinutesBefore?.toString() ?? '',
        checkLatestMinutesBefore: dto.checkLatestMinutesBefore?.toString() ?? '',
    }
}

export default ParticipantRequirementDialog

import {BaseEntityDialogProps} from "@utils/types.ts";
import {DocumentTemplateDto, DocumentTemplateRequest} from "@api/types.gen.ts";
import {useForm} from "react-hook-form-mui";
import {useTranslation} from "react-i18next";
import {useCallback} from "react";
import EntityDialog from "@components/EntityDialog.tsx";
import {addDocumentTemplate} from "@api/sdk.gen.ts";
import {InputAdornment, Stack, Typography} from "@mui/material";
import FormInputNumber from "@components/form/input/FormInputNumber.tsx";
import SelectFileButton from "@components/SelectFileButton.tsx";

type Form = {
    pagePaddingTop: string
    pagePaddingLeft: string
    pagePaddingRight: string
    pagePaddingBottom: string
    files: {
        file: File
    }[]
}

const defaultValues: Form = {
    pagePaddingTop: '',
    pagePaddingLeft: '',
    pagePaddingRight: '',
    pagePaddingBottom: '',
    files: [],
}

const mapFormToRequest = (formData: Form): DocumentTemplateRequest => ({
    pagePaddingTop: Number(formData.pagePaddingTop),
    pagePaddingLeft: Number(formData.pagePaddingLeft),
    pagePaddingRight: Number(formData.pagePaddingRight),
    pagePaddingBottom: Number(formData.pagePaddingBottom),
})

const mapEntityToForm = (dto: DocumentTemplateDto): Form => ({
    pagePaddingTop: dto.pagePaddingTop?.toString() ?? '',
    pagePaddingLeft: dto.pagePaddingLeft?.toString() ?? '',
    pagePaddingRight: dto.pagePaddingRight?.toString() ?? '',
    pagePaddingBottom: dto.pagePaddingBottom?.toString() ?? '',
    files: [],
})

const addAction = (formData: Form) =>
    addDocumentTemplate({
        body: {
            request: mapFormToRequest(formData),
            files: formData.files.map(file => file.file)
        }
    })

const DocumentTemplateDialog = (props: BaseEntityDialogProps<DocumentTemplateDto>) => {
    const formContext = useForm<Form>({
        resolver: (values) =>
            values.files.length === 1 || props.entity
                ? { values, errors: {} }
                : { values: {}, errors: { files: { type: 'required', message: t('document.template.file.missing') } } }
    })
    const {t} = useTranslation()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapEntityToForm(props.entity) : defaultValues)
    }, [props.entity])

    const chosenFile = formContext.watch('files')?.[0]?.file?.name
    const noFileError = formContext.formState.errors.files?.message

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}>
            <Stack spacing={2}>
                {!props.entity &&
                    <>
                        <Typography>{chosenFile}</Typography>
                        <SelectFileButton
                            variant={'text'}
                            onSelected={file => {
                                formContext.setValue('files', [{file}])
                            }}
                            accept={'application/pdf'}>
                            {chosenFile ? t('document.template.file.change') : t('document.template.file.choose')}
                        </SelectFileButton>
                        {noFileError && <Typography color={'error'}>{noFileError}</Typography>}
                    </>
                }
                <FormInputNumber
                    name={'pagePaddingTop'}
                    label={t('document.template.pagePadding.top')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>
                        }
                    }}
                />
                <FormInputNumber
                    name={'pagePaddingLeft'}
                    label={t('document.template.pagePadding.left')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>
                        }
                    }}
                />
                <FormInputNumber
                    name={'pagePaddingRight'}
                    label={t('document.template.pagePadding.right')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>
                        }
                    }}
                />
                <FormInputNumber
                    name={'pagePaddingBottom'}
                    label={t('document.template.pagePadding.bottom')}
                    min={0}
                    slotProps={{
                        input: {
                            endAdornment: <InputAdornment position={'end'}>mm</InputAdornment>
                        }
                    }}
                />
            </Stack>
        </EntityDialog>
    )
}

export default DocumentTemplateDialog
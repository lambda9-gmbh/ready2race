import {BankAccountDto, BankAccountRequest} from "@api/types.gen.ts";
import {addBankAccount, updateBankAccount} from "@api/sdk.gen.ts";
import {BaseEntityDialogProps} from "@utils/types.ts";
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import EntityDialog from "@components/EntityDialog.tsx";
import {Stack} from "@mui/material";
import {FormInputText} from "@components/form/input/FormInputText.tsx";

type Form = BankAccountRequest

const defaultValues: Form = {
    holder: '',
    iban: '',
    bic: '',
    bank: '',
}

const mapDtoToForm = (dto: BankAccountDto): Form => {
    const {id, ...rest} = dto
    return rest
}

const addAction = (formData: Form) =>
    addBankAccount({
        body: formData
    })

const editAction = (formData: Form, entity: BankAccountDto) =>
    updateBankAccount({
        path: {bankAccountId: entity.id},
        body: formData
    })

const BankAccountDialog = (props: BaseEntityDialogProps<BankAccountDto>) => {

    const formContext = useForm<Form>()

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
                <FormInputText name={'holder'} label={'[todo] holder'} required />
                <FormInputText name={'iban'} label={'[todo] iban'} required />
                <FormInputText name={'bic'} label={'[todo] bic'} required />
                <FormInputText name={'bank'} label={'[todo] bank'} required />
            </Stack>
        </EntityDialog>
    )
}

export default BankAccountDialog
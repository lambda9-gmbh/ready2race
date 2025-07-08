import {BankAccountDto, BankAccountRequest} from "@api/types.gen.ts";
import {addBankAccount, updateBankAccount} from "@api/sdk.gen.ts";
import {BaseEntityDialogProps} from "@utils/types.ts";
import {useForm} from "react-hook-form-mui";
import {useCallback} from "react";
import EntityDialog from "@components/EntityDialog.tsx";
import {Stack} from "@mui/material";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {useTranslation} from "react-i18next";

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

    const {t} = useTranslation()

    const formContext = useForm<Form>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    // todo: Validation (Same as Backend Validations - f.e. string length)

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'holder'} label={t('invoice.bank.accountData.holder')} required />
                <FormInputText name={'iban'} label={t('invoice.bank.accountData.iban')} required />
                <FormInputText name={'bic'} label={t('invoice.bank.accountData.bic')} required />
                <FormInputText name={'bank'} label={t('invoice.bank.accountData.bank')} required />
            </Stack>
        </EntityDialog>
    )
}

export default BankAccountDialog
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {AutocompleteOption} from '@utils/types.ts'
import {CompetitionPropertiesDto, CompetitionPropertiesRequestDto} from "@api/types.gen.ts";

export type CompetitionForm = {
    identifier: string
    name: string
    shortName: string
    description: string
    countMales: string
    countFemales: string
    countNonBinary: string
    countMixed: string
    competitionCategory: AutocompleteOption
    namedParticipants: {
        namedParticipant: AutocompleteOption
        required: boolean
        countMales: string
        countFemales: string
        countNonBinary: string
        countMixed: string
    }[]
    fees: {
        fee: AutocompleteOption
        required: boolean
        amount: string
    }[]
}

export const competitionFormDefaultValues: CompetitionForm = {
    identifier: '',
    name: '',
    shortName: '',
    description: '',
    countMales: '0',
    countFemales: '0',
    countNonBinary: '0',
    countMixed: '0',
    competitionCategory: {id: '', label: ''},
    namedParticipants: [],
    fees: [],
}

export function mapCompetitionFormToCompetitionPropertiesRequest(formData: CompetitionForm): CompetitionPropertiesRequestDto {
    return {
        identifier: formData.identifier,
        name: formData.name,
        shortName: takeIfNotEmpty(formData.shortName),
        description: takeIfNotEmpty(formData.description),
        countMales: Number(formData.countMales),
        countFemales: Number(formData.countFemales),
        countNonBinary: Number(formData.countNonBinary),
        countMixed: Number(formData.countMixed),
        competitionCategory: takeIfNotEmpty(formData.competitionCategory.id),
        namedParticipants: formData.namedParticipants.map(value => ({
            namedParticipant: value.namedParticipant.id,
            required: value.required,
            countMales: Number(value.countMales),
            countFemales: Number(value.countFemales),
            countNonBinary: Number(value.countNonBinary),
            countMixed: Number(value.countMixed),
        })),
        fees: formData.fees.map(value => ({
            fee: value.fee.id,
            required: value.required,
            amount: value.amount.replace(',', '.'),
        })),
    }
}

export function mapCompetitionPropertiesToCompetitionForm(
    dto: CompetitionPropertiesDto,
    decimalPoint: string,
): CompetitionForm {
    return {
        identifier: dto.identifier,
        name: dto.name,
        shortName: dto.shortName ?? '',
        description: dto.description ?? '',
        countMales: dto.countMales.toString(),
        countFemales: dto.countFemales.toString(),
        countNonBinary: dto.countNonBinary.toString(),
        countMixed: dto.countMixed.toString(),
        competitionCategory: dto.competitionCategory
            ? {
                  id: dto.competitionCategory?.id,
                  label: dto.competitionCategory.name,
              }
            : {id: '', label: ''},
        namedParticipants: dto.namedParticipants.map(value => ({
            namedParticipant: {id: value.id, label: value.name},
            required: value.required,
            countMales: value.countMales.toString(),
            countFemales: value.countFemales.toString(),
            countNonBinary: value.countNonBinary.toString(),
            countMixed: value.countMixed.toString(),
        })),
        fees: dto.fees.map(value => ({
            fee: {id: value.id, label: value.name},
            required: value.required,
            amount: value.amount.replace('.', decimalPoint),
        })),
    }
}


export function competitionLabelName(identifier: string, name: string) {
    return `${identifier} | ${name}`
}
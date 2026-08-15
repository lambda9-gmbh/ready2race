import {CheckedParticipantRequirement, ParticipantScanCompetitionDto} from '@api/types.gen.ts'

/**
 * Die Frage „gilt diese Bedingung für die Person **in diesem Bezugsrahmen** als erfüllt?" für
 * die Scan-App an der Waage — dieselbe Regel wie `RequirementScopeLogic.covers` im Backend,
 * hier als reine Funktion, damit sie ohne Netz und Komponenten testbar bleibt.
 *
 * Warum die Regel zweimal existiert: Das Backend entscheidet mit ihr, ob jemand starten darf;
 * die App muss dieselbe Antwort *anzeigen*, bevor sie abhakt. Ein grünes Häkchen, das in
 * Wirklichkeit zu einem anderen Wettkampf gehört, würde an der Waage dazu führen, dass jemand
 * ungewogen weitergewinkt wird. Weichen die beiden Regeln je auseinander, ist die Anzeige
 * falsch, nicht die Prüfung — deshalb steht die Backend-Fassung in der Doku als Original.
 */
export type RequirementScope = {
    perEventDay: boolean
    perCompetition: boolean
}

/** Der Rahmen, in dem gerade abgehakt wird: heutiger Wettkampftag, gewählter Wettkampf. */
export type ScanContext = {
    todayEventDayId?: string | null
    competitionId?: string | null
}

/**
 * Deckt eine gespeicherte Bestätigung den aktuellen Rahmen ab?
 *
 * Verglichen wird nur, was der jeweilige Schalter verlangt — steht er an, muss die Dimension
 * exakt stimmen, auch gegen null/undefined. Eine Zeile ohne Wettkampf deckt bei
 * `perCompetition` also keinen Wettkampf ab. Das ist die vorsichtige Richtung: lieber einmal
 * zu viel wiegen als jemanden ungeprüft an den Start lassen.
 */
export const covers = (
    scope: RequirementScope,
    fulfillment: CheckedParticipantRequirement,
    context: ScanContext,
): boolean =>
    (!scope.perEventDay || (fulfillment.eventDayId ?? null) === (context.todayEventDayId ?? null)) &&
    (!scope.perCompetition ||
        (fulfillment.competitionId ?? null) === (context.competitionId ?? null))

/**
 * Die Bestätigung, die den aktuellen Rahmen abdeckt — oder undefined. Bewusst die Zeile selbst
 * statt eines Ja/Nein, damit die Liste die Notiz genau dieser Bestätigung zeigen kann.
 */
export const coveringFulfillment = (
    requirementId: string,
    scope: RequirementScope,
    checked: CheckedParticipantRequirement[],
    context: ScanContext,
): CheckedParticipantRequirement | undefined =>
    checked.find(c => c.id === requirementId && covers(scope, c, context))

/**
 * Welcher Wettkampf beim Öffnen einer Person vorgewählt ist.
 *
 * Die Waage-Station arbeitet blockweise denselben Wettkampf ab, deshalb bleibt die zuletzt
 * gewählte Kennung über die Scans hinweg stehen ([stored]) — aber nur, wenn die neue Person
 * dort überhaupt gemeldet ist. Sonst hakte man reihenweise für einen Wettkampf ab, in dem die
 * Person gar nicht startet, und das Backend nähme das widerspruchslos an.
 *
 * Bleibt nichts übrig und ist die Person nur in einem Wettkampf gemeldet, ist die Wahl
 * eindeutig und wird getroffen. Bei mehreren ohne passenden Speicher bleibt es bewusst leer:
 * die Auswahl trifft der Mensch an der Waage.
 */
export const preselectCompetition = (
    stored: string | null | undefined,
    competitions: ParticipantScanCompetitionDto[],
): string | null => {
    if (stored != null && competitions.some(c => c.id === stored)) {
        return stored
    }
    if (competitions.length === 1) {
        return competitions[0].id
    }
    return null
}

/**
 * Der Stand einer Bedingung für diese Person, aufgeschlüsselt nach dem, was sie überhaupt
 * unterscheidet.
 *
 * An der Waage ist „abgehakt: ja/nein" zu wenig, sobald eine Bedingung je Wettkampf gilt: Wer in
 * zwei Wettkämpfen startet, muss zweimal auf die Waage, und die Person am Tablet muss auf einen
 * Blick sehen, was davon schon erledigt ist - ohne dafür die Wettkampf-Auswahl durchzuklicken.
 *
 * Eine Zeile je Wettkampf bei `perCompetition`, sonst genau eine für die Person. `perEventDay`
 * wirkt in beiden Fällen als Einschränkung auf heute; die Regel dafür ist dieselbe wie überall
 * ([covers]).
 */
export type RequirementStatusEntry = {
    /** Der Wettkampf dieser Zeile; null, wenn die Bedingung nicht je Wettkampf gilt. */
    competitionId: string | null
    /** Anzeigename des Wettkampfs; null bei der einzelnen Zeile ohne Wettkampfbezug. */
    competitionLabel: string | null
    fulfilled: boolean
    note?: string | null
}

export const requirementStatus = (
    requirementId: string,
    scope: RequirementScope,
    checked: CheckedParticipantRequirement[],
    competitions: ParticipantScanCompetitionDto[],
    todayEventDayId: string | null | undefined,
): RequirementStatusEntry[] => {
    const entryFor = (
        competitionId: string | null,
        label: string | null,
    ): RequirementStatusEntry => {
        const covering = coveringFulfillment(requirementId, scope, checked, {
            todayEventDayId,
            competitionId,
        })
        return {
            competitionId,
            competitionLabel: label,
            fulfilled: covering !== undefined,
            note: covering?.note,
        }
    }

    if (!scope.perCompetition) {
        return [entryFor(null, null)]
    }
    return competitions.map(competition =>
        entryFor(competition.id, competitionLabel(competition)),
    )
}

/** Anzeigename eines Wettkampfs: Kennung plus Kürzel, sonst der volle Name. */
export const competitionLabel = (competition: ParticipantScanCompetitionDto): string =>
    [competition.identifier, competition.shortName ?? competition.name]
        .filter(part => part != null && part !== '')
        .join(' ')

package de.lambda9.ready2race.backend.app.participantRequirement.boundary

import java.util.UUID

/**
 * Reine Logik der Frage "welche Bedingungen fehlen dieser Person noch?", bewusst ohne
 * Datenbank- und Ktor-Bezug, damit sie ohne laufende Umgebung geprüft werden kann.
 */
object OpenRequirementLogic {

    /**
     * Eine an der Veranstaltung aktive Bedingung mitsamt ihrer Geltung.
     *
     * [namedParticipantId] ist null, wenn die Bedingung für alle gilt, sonst die Rolle, auf die
     * sie eingegrenzt ist. Dieselbe Bedingung kann mehrfach vorkommen, wenn sie mehreren Rollen
     * zugeordnet ist - in `event_has_participant_requirement` ist das je eine Zeile.
     */
    data class RequirementScope(
        val id: UUID,
        val name: String,
        val namedParticipantId: UUID?,
    )

    /**
     * Die Bedingungen, die [registeredRoles] noch offen hat - in der Reihenfolge, in der
     * [requirements] hereinkommt, und je Bedingung höchstens einmal.
     *
     * Die Rollenprüfung ist der Kern: `getParticipantsForEventWithMissingRequirement` fragt nur,
     * ob ein Haken fehlt, nicht ob die Bedingung die Person überhaupt betrifft. Beim Abgleich
     * holt [RequirementMatchLogic.matches] das nach. Ohne dieselbe Prüfung stünde eine
     * rollengebundene Bedingung wie "Waage 55 kg" bei jedem Gemeldeten als offen, obwohl sie
     * nur Steuerleute betrifft.
     */
    fun openFor(
        requirements: List<RequirementScope>,
        registeredRoles: List<UUID>,
        checkedRequirementIds: List<UUID>,
    ): List<RequirementScope> {
        val checked = checkedRequirementIds.toSet()
        val roles = registeredRoles.toSet()

        return requirements
            .filter { it.id !in checked }
            .filter { it.namedParticipantId == null || it.namedParticipantId in roles }
            .distinctBy { it.id }
    }
}

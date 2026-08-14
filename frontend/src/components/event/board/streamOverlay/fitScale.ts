/**
 * Maßstab, mit dem ein Inhalt in eine begrenzte Höhe passt — 1, solange er von selbst
 * hineinpasst, sonst das Verhältnis Platz/Bedarf.
 *
 * Warum überhaupt skalieren: Die Stream-Panels haben eine feste Höhengrenze (TV-Rahmen)
 * und dürfen niemals scrollen. Ohne Maßstab schneidet die Kante mitten durch die letzte
 * Bootszeile und die Boote darunter fehlen ersatzlos — genau das ist an einem Achterfeld
 * passiert. Lieber alle Boote etwas kleiner als vier Boote und eine abgeschnittene Zeile.
 *
 * Bewusst OHNE Untergrenze: Ein halb abgeschnittenes Boot sieht auf Sendung kaputt aus,
 * eine kleine, aber vollständige Zeile nicht. Felder jenseits von zehn Booten gibt es im
 * Coastal Rowing ohnehin nicht, der Maßstab bleibt real über 0,5.
 */
export const fitScale = (available: number, natural: number): number => {
    if (!(available > 0) || !(natural > 0)) return 1
    if (natural <= available) return 1
    // Auf drei Stellen gerundet: ein um Bruchteile eines Pixels schwankender Messwert
    // (Zehntel-Uhr, neu eingetroffene Rundenzeit) soll keinen neuen Renderdurchgang und
    // damit kein sichtbares Zittern auslösen.
    return Math.round((available / natural) * 1000) / 1000
}

/** Ab wann ein neuer Messwert den Maßstab wirklich ersetzt — darunter bleibt der alte stehen. */
export const SCALE_EPSILON = 0.005

/** Der Maßstab, mit dem gerendert wird: der alte, solange sich kaum etwas geändert hat. */
export const steadyScale = (previous: number, next: number): number =>
    Math.abs(previous - next) < SCALE_EPSILON ? previous : next

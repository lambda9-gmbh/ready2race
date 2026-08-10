set search_path to ready2race, pg_catalog, public;

-- Loeschen einer Urkundenvorlage endete in einem 500er, sobald sie Platzhalter hatte - also bei
-- jeder eingerichteten Vorlage. GapDocumentTemplateService.deleteTemplate loescht nur die
-- Vorlagenzeile und verlaesst sich darauf, dass die Datenbank die abhaengigen Zeilen mitnimmt.
-- gap_document_template_data, _font und _usage tun das seit ihrer Anlage; gap_document_placeholder
-- ist als einzige der vier Tabellen ohne "on delete cascade" angelegt worden und liess den
-- Loeschversuch in einer Fremdschluesselverletzung auflaufen.
--
-- Der Fremdschluessel wird deshalb an seine Geschwister angeglichen, statt den Dienst um ein
-- zweites Loeschen zu erweitern: die Zusage gilt dann fuer jeden Aufrufer, nicht nur fuer den
-- einen, der daran gedacht hat.
alter table gap_document_placeholder
    drop constraint gap_document_placeholder_template_fkey;

alter table gap_document_placeholder
    add constraint gap_document_placeholder_template_fkey
        foreign key (template) references gap_document_template on delete cascade;

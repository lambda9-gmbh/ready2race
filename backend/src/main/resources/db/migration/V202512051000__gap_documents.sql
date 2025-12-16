create table gap_document_template
(
    id uuid primary key,
    name text not null,
    type text not null
);

create table gap_document_placeholder
(
    id uuid primary key,
    template uuid not null references gap_document_template,
    type text not null,
    name text,
    page int not null,
    rel_left double precision not null,
    rel_top double precision not null,
    rel_width double precision not null,
    rel_height double precision not null,
    text_align text not null
);

create table gap_document_template_data
(
    template uuid primary key references gap_document_template on delete cascade,
    data bytea not null
);

create table gap_document_template_usage
(
    type text primary key,
    template uuid not null references gap_document_template on delete cascade
);
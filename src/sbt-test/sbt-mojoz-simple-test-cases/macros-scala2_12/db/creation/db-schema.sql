create table foo(
  id bigint,
  name text
);

alter table foo add constraint pk_foo primary key (id);

create table referendum (
  id integer primary key,
  topic text not null,
  locale text not null,
  started_on datetime not null,
  running_until datetime not null,
  result_from number not null,
  result_to number not null
);

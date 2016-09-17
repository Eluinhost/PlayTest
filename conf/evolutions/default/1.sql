# Initial Schema

# --- !Ups

create extension if not exists "uuid-ossp";

create table if not exists polls (
  id uuid primary key default uuid_generate_v1mc(),
  name varchar(128) not null,
  secret uuid not null,
  opened timestamptz not null,
  closes timestamptz not null
);

create table if not exists people (
  id bigserial primary key,
  name varchar(128) not null,
  poll_id uuid not null references polls(id),
  unique (name, poll_id)
);

create table if not exists slots (
  id bigserial primary key,
  day date not null,
  hour int not null constraint valid_hour check (hour >= 0 and hour <= 23),
  choosable boolean not null default true,
  poll_id uuid not null references polls(id),
  unique(day, hour, poll_id)
)

# --- !Downs

drop table responses;
drop table people;

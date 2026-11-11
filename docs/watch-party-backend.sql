-- RAIF watch party backend (parties, members, chat, RPCs, realtime).
-- Run this once in Supabase Dashboard -> SQL Editor -> New query -> Run.
-- It is safe to re-run: every object is created with IF NOT EXISTS / OR REPLACE.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table if not exists public.watch_parties (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    host_member_id uuid,
    host_only boolean not null default false,
    media jsonb not null,
    playback jsonb,
    max_members integer not null default 3,
    status text not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.watch_party_members (
    id uuid primary key default gen_random_uuid(),
    party_id uuid not null references public.watch_parties (id) on delete cascade,
    device_id text not null,
    display_name text not null,
    avatar_url text,
    avatar_color text,
    profile_index integer,
    joined_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    unique (party_id, device_id)
);

create table if not exists public.watch_party_messages (
    id bigserial primary key,
    party_id uuid not null references public.watch_parties (id) on delete cascade,
    member_id uuid not null,
    body text not null check (char_length(body) between 1 and 500),
    client_id text,
    sent_at timestamptz not null default now()
);

create index if not exists watch_party_members_party_idx
    on public.watch_party_members (party_id, joined_at);
create index if not exists watch_party_messages_party_idx
    on public.watch_party_messages (party_id, id);
create index if not exists watch_parties_status_idx
    on public.watch_parties (status, updated_at desc);

-- Tables are only reachable through the security-definer functions below.
alter table public.watch_parties enable row level security;
alter table public.watch_party_members enable row level security;
alter table public.watch_party_messages enable row level security;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create or replace function public.watch_party_now_ms()
returns bigint
language sql
stable
as $$
    select (extract(epoch from clock_timestamp()) * 1000)::bigint;
$$;

create or replace function public.watch_party_new_code()
returns text
language plpgsql
volatile
as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    candidate text;
    attempt integer := 0;
begin
    loop
        exit when attempt >= 40;
        select string_agg(substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1), '')
          into candidate
          from generate_series(1, 6);
        if not exists (select 1 from public.watch_parties p where p.code = candidate) then
            return candidate;
        end if;
        attempt := attempt + 1;
    end loop;
    -- Fallback that cannot collide in practice.
    return upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
end;
$$;

create or replace function public.watch_party_member_of(p_party_id uuid, p_device_id text)
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select m.id
      from public.watch_party_members m
     where m.party_id = p_party_id
       and m.device_id = p_device_id
     limit 1;
$$;

create or replace function public.watch_party_snapshot(p_party_id uuid, p_member_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
    select jsonb_build_object(
        'party', jsonb_build_object(
            'id', p.id,
            'code', p.code,
            'host_member_id', p.host_member_id,
            'host_only', p.host_only,
            'media', p.media,
            'playback', p.playback,
            'max_members', p.max_members
        ),
        'members', coalesce((
            select jsonb_agg(jsonb_build_object(
                       'id', m.id,
                       'display_name', m.display_name,
                       'avatar_url', m.avatar_url,
                       'avatar_color', m.avatar_color
                   ) order by m.joined_at)
              from public.watch_party_members m
             where m.party_id = p.id
        ), '[]'::jsonb),
        'member_id', p_member_id,
        'server_time_ms', public.watch_party_now_ms()
    )
      from public.watch_parties p
     where p.id = p_party_id;
$$;

-- ---------------------------------------------------------------------------
-- Public RPCs used by the iOS app
-- ---------------------------------------------------------------------------

create or replace function public.watch_party_enabled()
returns boolean
language sql
immutable
as $$
    select true;
$$;

create or replace function public.watch_party_clock()
returns bigint
language sql
stable
as $$
    select public.watch_party_now_ms();
$$;

create or replace function public.watch_party_preview(p_code text)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    party public.watch_parties;
    member_count integer;
    host public.watch_party_members;
begin
    select * into party
      from public.watch_parties p
     where upper(p.code) = upper(trim(p_code))
       and p.status = 'active'
     limit 1;

    if party.id is null then
        return jsonb_build_object('error', 'party_not_found');
    end if;

    select count(*) into member_count
      from public.watch_party_members m
     where m.party_id = party.id;

    select * into host
      from public.watch_party_members m
     where m.id = party.host_member_id;

    return jsonb_build_object(
        'media', party.media,
        'host', case when host.id is null then null else jsonb_build_object(
            'id', host.id,
            'display_name', host.display_name,
            'avatar_url', host.avatar_url,
            'avatar_color', host.avatar_color
        ) end,
        'members', member_count,
        'max_members', party.max_members
    );
end;
$$;

create or replace function public.watch_party_create(
    p_device_id text,
    p_display_name text,
    p_avatar_url text,
    p_avatar_color text,
    p_media jsonb,
    p_playback jsonb,
    p_profile_index integer default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    party public.watch_parties;
    member public.watch_party_members;
begin
    if coalesce(trim(p_device_id), '') = '' then
        return jsonb_build_object('error', 'party_not_found');
    end if;

    -- One active party per device: leave any previous one first.
    update public.watch_parties p
       set status = 'ended', updated_at = now()
     where p.status = 'active'
       and exists (
           select 1 from public.watch_party_members m
            where m.party_id = p.id and m.device_id = p_device_id
       );

    insert into public.watch_parties (code, media, playback)
    values (
        public.watch_party_new_code(),
        coalesce(p_media, '{}'::jsonb),
        p_playback
    )
    returning * into party;

    insert into public.watch_party_members (
        party_id, device_id, display_name, avatar_url, avatar_color, profile_index
    )
    values (
        party.id,
        p_device_id,
        coalesce(nullif(trim(p_display_name), ''), 'RAIF'),
        p_avatar_url,
        p_avatar_color,
        p_profile_index
    )
    returning * into member;

    update public.watch_parties
       set host_member_id = member.id
     where id = party.id;

    return public.watch_party_snapshot(party.id, member.id);
end;
$$;

create or replace function public.watch_party_join(
    p_code text,
    p_device_id text,
    p_display_name text,
    p_avatar_url text,
    p_avatar_color text,
    p_profile_index integer default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    party public.watch_parties;
    member public.watch_party_members;
    member_count integer;
begin
    select * into party
      from public.watch_parties p
     where upper(p.code) = upper(trim(p_code))
     limit 1;

    if party.id is null then
        return jsonb_build_object('error', 'party_not_found');
    end if;
    if party.status <> 'active' then
        return jsonb_build_object('error', 'party_ended');
    end if;

    select * into member
      from public.watch_party_members m
     where m.party_id = party.id
       and m.device_id = p_device_id;

    if member.id is null then
        select count(*) into member_count
          from public.watch_party_members m
         where m.party_id = party.id;

        if member_count >= party.max_members then
            return jsonb_build_object('error', 'party_full');
        end if;

        insert into public.watch_party_members (
            party_id, device_id, display_name, avatar_url, avatar_color, profile_index
        )
        values (
            party.id,
            p_device_id,
            coalesce(nullif(trim(p_display_name), ''), 'RAIF'),
            p_avatar_url,
            p_avatar_color,
            p_profile_index
        )
        returning * into member;
    else
        update public.watch_party_members
           set last_seen_at = now(),
               display_name = coalesce(nullif(trim(p_display_name), ''), display_name),
               avatar_url = coalesce(p_avatar_url, avatar_url),
               avatar_color = coalesce(p_avatar_color, avatar_color)
         where id = member.id
        returning * into member;
    end if;

    return public.watch_party_snapshot(party.id, member.id);
end;
$$;

create or replace function public.watch_party_heartbeat(p_party_id uuid, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member_id uuid := public.watch_party_member_of(p_party_id, p_device_id);
begin
    if member_id is null then
        return jsonb_build_object('error', 'not_member');
    end if;
    update public.watch_party_members
       set last_seen_at = now()
     where id = member_id;

    if not exists (
        select 1 from public.watch_parties p
         where p.id = p_party_id and p.status = 'active'
    ) then
        return jsonb_build_object('error', 'party_ended');
    end if;

    return public.watch_party_snapshot(p_party_id, member_id);
end;
$$;

create or replace function public.watch_party_update(
    p_party_id uuid,
    p_device_id text,
    p_media jsonb default null,
    p_playback jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    party public.watch_parties;
    member_id uuid := public.watch_party_member_of(p_party_id, p_device_id);
begin
    if member_id is null then
        return jsonb_build_object('error', 'not_member');
    end if;

    select * into party from public.watch_parties p where p.id = p_party_id;
    if party.id is null or party.status <> 'active' then
        return jsonb_build_object('error', 'party_ended');
    end if;

    -- Only the host controls playback when host-only is on.
    if party.host_only and party.host_member_id is distinct from member_id and p_playback is not null then
        p_playback := null;
    end if;

    update public.watch_parties
       set media = coalesce(p_media, media),
           playback = coalesce(p_playback, playback),
           updated_at = now()
     where id = p_party_id;

    update public.watch_party_members
       set last_seen_at = now()
     where id = member_id;

    return public.watch_party_snapshot(p_party_id, member_id);
end;
$$;

create or replace function public.watch_party_leave(p_party_id uuid, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member_id uuid := public.watch_party_member_of(p_party_id, p_device_id);
    host_id uuid;
begin
    if member_id is null then
        return jsonb_build_object('ok', true);
    end if;

    select host_member_id into host_id from public.watch_parties p where p.id = p_party_id;
    delete from public.watch_party_members where id = member_id;

    if host_id = member_id then
        update public.watch_parties
           set status = 'ended', updated_at = now()
         where id = p_party_id;
    end if;

    return jsonb_build_object('ok', true, 'server_time_ms', public.watch_party_now_ms());
end;
$$;

create or replace function public.watch_party_end(p_party_id uuid, p_device_id text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member_id uuid := public.watch_party_member_of(p_party_id, p_device_id);
    party public.watch_parties;
begin
    select * into party from public.watch_parties p where p.id = p_party_id;
    if party.id is null then
        return jsonb_build_object('error', 'party_not_found');
    end if;
    if party.host_member_id is distinct from member_id then
        return jsonb_build_object('error', 'removed');
    end if;

    update public.watch_parties
       set status = 'ended', updated_at = now()
     where id = p_party_id;
    delete from public.watch_party_members where party_id = p_party_id;

    return jsonb_build_object('ok', true, 'server_time_ms', public.watch_party_now_ms());
end;
$$;

create or replace function public.watch_party_set_host_only(
    p_party_id uuid,
    p_device_id text,
    p_host_only boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member_id uuid := public.watch_party_member_of(p_party_id, p_device_id);
begin
    if member_id is null then
        return jsonb_build_object('error', 'not_member');
    end if;
    if not exists (
        select 1 from public.watch_parties p
         where p.id = p_party_id and p.host_member_id = member_id
    ) then
        return jsonb_build_object('error', 'removed');
    end if;

    update public.watch_parties
       set host_only = coalesce(p_host_only, false), updated_at = now()
     where id = p_party_id;

    return public.watch_party_snapshot(p_party_id, member_id);
end;
$$;

create or replace function public.watch_party_remove_member(
    p_party_id uuid,
    p_device_id text,
    p_member_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member_id uuid := public.watch_party_member_of(p_party_id, p_device_id);
begin
    if member_id is null then
        return jsonb_build_object('error', 'not_member');
    end if;
    if not exists (
        select 1 from public.watch_parties p
         where p.id = p_party_id and p.host_member_id = member_id
    ) then
        return jsonb_build_object('error', 'removed');
    end if;

    delete from public.watch_party_members
     where party_id = p_party_id and id = p_member_id and id <> member_id;

    return public.watch_party_snapshot(p_party_id, member_id);
end;
$$;

create or replace function public.watch_party_link_profile(
    p_device_id text,
    p_profile_index integer,
    p_member_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member public.watch_party_members;
    party_id uuid;
begin
    select m.* into member
      from public.watch_party_members m
      join public.watch_parties p on p.id = m.party_id and p.status = 'active'
     where m.device_id = p_device_id
       and (p_member_id is null or m.id = p_member_id)
     order by m.last_seen_at desc
     limit 1;

    if member.id is null then
        return jsonb_build_object('ok', false);
    end if;

    party_id := member.party_id;
    update public.watch_party_members
       set profile_index = p_profile_index, last_seen_at = now()
     where id = member.id;

    return public.watch_party_snapshot(party_id, member.id);
end;
$$;

-- ---------------------------------------------------------------------------
-- Chat
-- ---------------------------------------------------------------------------

create or replace function public.watch_party_send_message(
    p_party_id uuid,
    p_device_id text,
    p_body text,
    p_client_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member public.watch_party_members;
    message public.watch_party_messages;
begin
    select * into member
      from public.watch_party_members m
     where m.party_id = p_party_id and m.device_id = p_device_id;
    if member.id is null then
        return jsonb_build_object('error', 'not_member');
    end if;
    if coalesce(trim(p_body), '') = '' then
        return jsonb_build_object('error', 'invalid_body');
    end if;

    insert into public.watch_party_messages (party_id, member_id, body, client_id)
    values (p_party_id, member.id, left(trim(p_body), 500), p_client_id)
    returning * into message;

    return jsonb_build_object('message', jsonb_build_object(
        'id', message.id,
        'member_id', member.id,
        'name', member.display_name,
        'avatar_url', member.avatar_url,
        'avatar_color', member.avatar_color,
        'body', message.body,
        'client_id', message.client_id,
        'sent_at', (extract(epoch from message.sent_at) * 1000)::bigint,
        'mine', true
    ));
end;
$$;

create or replace function public.watch_party_list_messages(
    p_party_id uuid,
    p_device_id text,
    p_after bigint default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    member public.watch_party_members;
begin
    select * into member
      from public.watch_party_members m
     where m.party_id = p_party_id and m.device_id = p_device_id;
    if member.id is null then
        return jsonb_build_object('error', 'not_member');
    end if;

    return jsonb_build_object('messages', coalesce((
        select jsonb_agg(jsonb_build_object(
                   'id', msg.id,
                   'member_id', msg.member_id,
                   'name', m.display_name,
                   'avatar_url', m.avatar_url,
                   'avatar_color', m.avatar_color,
                   'body', msg.body,
                   'client_id', msg.client_id,
                   'sent_at', (extract(epoch from msg.sent_at) * 1000)::bigint,
                   'mine', msg.member_id = member.id
               ) order by msg.id)
          from public.watch_party_messages msg
          join public.watch_party_members m on m.id = msg.member_id
         where msg.party_id = p_party_id
           and (p_after is null or msg.id > p_after)
    ), '[]'::jsonb));
end;
$$;

-- ---------------------------------------------------------------------------
-- Realtime: allow the party broadcast channel for signed-in devices
-- ---------------------------------------------------------------------------

do $$
begin
    begin
        execute $policy$
            drop policy if exists "watch party read" on realtime.messages;
        $policy$;
        execute $policy$
            create policy "watch party read"
                on realtime.messages
                for select
                to anon, authenticated
                using (realtime.topic() like 'watch-party:%');
        $policy$;
    exception
        when undefined_table or insufficient_privilege then
            raise notice 'Skipping realtime read policy (realtime.messages not available).';
    end;

    begin
        execute $policy$
            drop policy if exists "watch party write" on realtime.messages;
        $policy$;
        execute $policy$
            create policy "watch party write"
                on realtime.messages
                for insert
                to anon, authenticated
                with check (realtime.topic() like 'watch-party:%');
        $policy$;
    exception
        when undefined_table or insufficient_privilege then
            raise notice 'Skipping realtime write policy (realtime.messages not available).';
    end;
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

grant execute on function
    public.watch_party_enabled(),
    public.watch_party_clock(),
    public.watch_party_preview(text),
    public.watch_party_create(text, text, text, text, jsonb, jsonb, integer),
    public.watch_party_join(text, text, text, text, text, integer),
    public.watch_party_heartbeat(uuid, text),
    public.watch_party_update(uuid, text, jsonb, jsonb),
    public.watch_party_leave(uuid, text),
    public.watch_party_end(uuid, text),
    public.watch_party_set_host_only(uuid, text, boolean),
    public.watch_party_remove_member(uuid, text, uuid),
    public.watch_party_link_profile(text, integer, uuid),
    public.watch_party_send_message(uuid, text, text, text),
    public.watch_party_list_messages(uuid, text, bigint)
to anon, authenticated;

revoke all on public.watch_parties from anon, authenticated;
grant select on public.watch_parties to anon, authenticated;

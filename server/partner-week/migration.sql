-- This schema is intentionally outside the exposed Data API schemas. Access is
-- by a narrow RPC used by the Edge Function. No health or contact fields.
create schema fasting_partners;
revoke all on schema fasting_partners from public, anon, authenticated;
grant usage on schema fasting_partners to service_role;

create table fasting_partners.weeks (
    pair_id uuid primary key default gen_random_uuid(),
    week_id uuid not null unique default gen_random_uuid(),
    invite_hash text not null unique,
    invite_expires_at timestamptz not null default now() + interval '7 days',
    started_at timestamptz,
    finishes_at timestamptz,
    purge_at timestamptz not null default now() + interval '7 days'
);
create index partner_week_cleanup on fasting_partners.weeks(purge_at);
create table fasting_partners.members (
    device_hash text primary key,
    pair_id uuid not null references fasting_partners.weeks on delete cascade,
    seat text not null check (seat in ('host','guest')),
    alias text not null,
    zone text not null,
    role text not null check (role in ('participant','supporter')),
    marks date[] not null default '{}',
    renewed boolean not null default false,
    primary_day_cheers jsonb not null default '{}',
    unique(pair_id,seat)
);
-- Counts only for invite creation and previews. The Edge derives a temporary
-- scoped origin hash. No IP, device credential, invite, or customer payload here.
create table fasting_partners.request_windows (
    kind text not null,
    window_start timestamptz not null,
    origin_hash text not null,
    hits integer not null,
    primary key(window_start,kind,origin_hash)
);
alter table fasting_partners.request_windows enable row level security;
alter table fasting_partners.weeks enable row level security;
alter table fasting_partners.members enable row level security;
revoke all on all tables in schema fasting_partners from public, anon, authenticated;
grant select,insert,update,delete on all tables in schema fasting_partners to service_role;

create function fasting_partners.allow_request(p_kind text,p_origin text,p_clock timestamptz)
returns boolean language plpgsql volatile security invoker set search_path='' as $$
declare
    hits_now integer;
    cap integer := case when p_kind='create' then 30 else 180 end;
begin
    if coalesce(p_origin,'') !~ '^[a-f0-9]{64}$' then return false; end if;
    insert into fasting_partners.request_windows as win(kind,window_start,origin_hash,hits)
        values(p_kind,date_bin(interval '15 minutes',p_clock,timestamptz '2000-01-01'),p_origin,1)
        on conflict(window_start,kind,origin_hash) do update set hits=win.hits+1 where win.hits < cap
        returning hits into hits_now;
    return hits_now is not null;
end;
$$;

create function fasting_partners.purge_expired()
returns void language sql volatile security invoker set search_path='' as $$
    delete from fasting_partners.weeks where purge_at<=now();
    delete from fasting_partners.request_windows where window_start<=now()-interval '1 hour';
$$;

create function fasting_partners.member_json(m fasting_partners.members)
returns jsonb language sql immutable strict security invoker set search_path='' as $$
    select jsonb_build_object('alias',m.alias,'role',m.role,'days',m.marks,'renewed',m.renewed)
$$;

create function fasting_partners.start_week(p_pair uuid, p_clock timestamptz)
returns void language sql security invoker set search_path='' as $$
    -- Each member gets seven calendar dates starting on acceptance in their own
    -- timezone. Both dates must have ended for the joint recap to open.
    update fasting_partners.weeks set
        week_id=case when started_at is null then week_id else gen_random_uuid() end, started_at=p_clock, finishes_at=r.finishes,
        purge_at=r.finishes+interval '30 days'
    from (
        select max((((p_clock at time zone zone)::date+7)::timestamp at time zone zone)) as finishes
        from fasting_partners.members where pair_id=p_pair
    ) r where pair_id=p_pair;
    update fasting_partners.members set marks='{}', renewed=false, primary_day_cheers='{}' where pair_id=p_pair;
$$;

create function fasting_partners.snapshot(p_pair uuid,p_device text,p_clock timestamptz)
returns jsonb language sql stable security invoker set search_path='' as $$
    select jsonb_build_object('week',jsonb_build_object(
        'id',w.week_id,
        'status',case when w.started_at is null then 'pending' when p_clock>=w.finishes_at then 'finished' else 'active' end,
        'self',me.seat,'expiresAt',w.invite_expires_at,
        'startDay',(w.started_at at time zone me.zone)::date,
        'endDay',(w.started_at at time zone me.zone)::date+6,
        'today',(p_clock at time zone me.zone)::date,
        'host',fasting_partners.member_json(h),'guest',fasting_partners.member_json(g),
        'cheers',coalesce((select jsonb_agg(jsonb_build_object('from',m.seat,'day',e.key,'kind',e.value) order by e.key,m.seat)
             from fasting_partners.members m cross join lateral jsonb_each_text(m.primary_day_cheers) e
             where m.pair_id=w.pair_id),'[]'::jsonb)
    )) from fasting_partners.weeks w
    join fasting_partners.members me on me.pair_id=w.pair_id and me.device_hash=p_device
    join fasting_partners.members h on h.pair_id=w.pair_id and h.seat='host'
    left join fasting_partners.members g on g.pair_id=w.pair_id and g.seat='guest'
    where w.pair_id=p_pair and w.purge_at>p_clock
$$;

-- p_request is normalized by the Edge Function. Its 256-bit device and invite
-- capabilities are domain-separated SHA-256 digests before touching PostgREST.
create or replace function public.fasting_partner_week(p_request jsonb)
returns jsonb language plpgsql volatile security invoker set search_path='' as $$
declare
    op text := p_request->>'action';
    device text := p_request->>'deviceHash';
    invite text := p_request->>'inviteHash';
    alias_value text := btrim(p_request->>'alias');
    zone_value text := p_request->>'zone';
    guest_role text := p_request->>'role';
    clock_time timestamptz := statement_timestamp();
    w fasting_partners.weeks%rowtype;
    me fasting_partners.members%rowtype;
    candidate fasting_partners.weeks%rowtype;
    stamp date;
    first_day date;
    no_week jsonb := jsonb_build_object('week',null);
begin
    if op is null or op not in ('create','preview','join','get','check_in','cheer','leave','renew','pause') then
        return '{"error":"invalid"}';
    end if;
    if op in ('create','preview','join') and coalesce(invite,'') !~ '^[a-f0-9]{64}$' then
        return '{"error":"invalid"}';
    end if;
    if op='preview' then
        if not fasting_partners.allow_request(op,p_request->>'originHash',clock_time) then return '{"error":"slow_down"}'; end if;
        select * into w from fasting_partners.weeks where invite_hash=invite and started_at is null and purge_at>clock_time;
        if not found then return '{"error":"gone"}'; end if;
        return jsonb_build_object('alias',(select alias from fasting_partners.members where pair_id=w.pair_id and seat='host'),'expiresAt',w.invite_expires_at);
    end if;
    if coalesce(device,'') !~ '^[a-f0-9]{64}$' then return '{"error":"invalid"}'; end if;
    if op in ('create','join') then
        if coalesce(char_length(alias_value),0) not between 1 and 32 or alias_value ~ '[[:cntrl:]]'
            or length(coalesce(zone_value,'')) not between 1 and 64
            or not exists(select from pg_catalog.pg_timezone_names where name=zone_value) then
            return '{"error":"invalid"}';
        end if;
        if op='join' and coalesce(guest_role,'') not in ('participant','supporter') then return '{"error":"invalid"}'; end if;
    end if;
    -- Serializing commands per device and per pair keeps parallel joins,
    -- offline retries, concurrent votes and leave races consistent.
    perform pg_advisory_xact_lock(hashtextextended(device,0));
    select week.* into w from fasting_partners.weeks week join fasting_partners.members member using(pair_id)
        where member.device_hash=device for update of week;
    if w.purge_at <= clock_time then
        delete from fasting_partners.weeks where pair_id=w.pair_id;
        w:=null;
    end if;
    if op='get' then return coalesce(fasting_partners.snapshot(w.pair_id,device,clock_time),no_week); end if;
    if op='create' then
        if w.pair_id is not null then
            if w.invite_hash=invite and exists(select from fasting_partners.members where device_hash=device and seat='host') then
                return fasting_partners.snapshot(w.pair_id,device,clock_time);
            end if;
            return '{"error":"conflict"}';
        end if;
        if not fasting_partners.allow_request(op,p_request->>'originHash',clock_time) then return '{"error":"slow_down"}'; end if;
        insert into fasting_partners.weeks(invite_hash) values(invite) on conflict do nothing returning * into w;
        if not found then return '{"error":"conflict"}'; end if;
        insert into fasting_partners.members(device_hash,pair_id,seat,alias,zone,role) values(device,w.pair_id,'host',alias_value,zone_value,'participant');
    elsif op='join' then
        if w.pair_id is not null then
            if w.invite_hash=invite and exists(select from fasting_partners.members where device_hash=device and seat='guest') then
                return fasting_partners.snapshot(w.pair_id,device,clock_time);
            end if;
            return '{"error":"conflict"}';
        end if;
        select * into candidate from fasting_partners.weeks where invite_hash=invite for update;
        if not found or candidate.started_at is not null or candidate.invite_expires_at<=clock_time then return '{"error":"gone"}'; end if;
        insert into fasting_partners.members(device_hash,pair_id,seat,alias,zone,role) values(device,candidate.pair_id,'guest',alias_value,zone_value,guest_role);
        w:=candidate;
        perform fasting_partners.start_week(w.pair_id,clock_time);
    else
        if w.pair_id is null then
            if op='leave' then return no_week; end if;
            return '{"error":"gone"}';
        end if;
        if w.week_id::text is distinct from p_request->>'weekId' then return '{"error":"stale"}'; end if;
        if op='leave' then
            delete from fasting_partners.weeks where pair_id=w.pair_id;
            return no_week;
        end if;
        if w.started_at is null then return '{"error":"conflict"}'; end if;
        select * into strict me from fasting_partners.members where device_hash=device and pair_id=w.pair_id;
        if op in ('renew','pause') then
            if clock_time<w.finishes_at then return '{"error":"conflict"}'; end if;
            update fasting_partners.members set renewed=(op='renew') where device_hash=device;
            if op='renew' and not exists(select from fasting_partners.members where pair_id=w.pair_id and not renewed) then
                perform fasting_partners.start_week(w.pair_id,clock_time);
            end if;
        else
            if coalesce(p_request->>'day','') !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' then return '{"error":"invalid"}'; end if;
            begin stamp:=(p_request->>'day')::date; exception when datetime_field_overflow or invalid_datetime_format then return '{"error":"invalid"}'; end;
            first_day:=(w.started_at at time zone me.zone)::date;
            if stamp<first_day or stamp>first_day+6 or stamp>(clock_time at time zone me.zone)::date
                or clock_time>w.finishes_at+interval '48 hours' then return '{"error":"conflict"}'; end if;
            if op='check_in' then
                if me.role<>'participant' then return '{"error":"conflict"}'; end if;
                if not(stamp=any(me.marks)) then
                    if cardinality(me.marks)>=3 then return '{"error":"conflict"}'; end if;
                    update fasting_partners.members set marks=array(select d from unnest(marks||array[stamp]) d order by d) where device_hash=device;
                end if;
            elsif op='cheer' then
                if coalesce(p_request->>'kind','') not in ('glad','here','thanks') then return '{"error":"invalid"}'; end if;
                if not(me.primary_day_cheers ? stamp::text) then
                    update fasting_partners.members set primary_day_cheers=primary_day_cheers||jsonb_build_object(stamp::text,p_request->>'kind') where device_hash=device;
                end if;
            end if;
        end if;
    end if;
    return fasting_partners.snapshot(w.pair_id,device,clock_time);
end;
$$;
revoke all on all functions in schema fasting_partners from public, anon, authenticated;
grant execute on all functions in schema fasting_partners to service_role;
revoke all on function public.fasting_partner_week(jsonb) from public, anon, authenticated;
grant execute on function public.fasting_partner_week(jsonb) to service_role;

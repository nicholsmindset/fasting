// The caller proves possession of a 256-bit device or single-use invite
// capability. No raw capabilities or IP addresses cross the RPC boundary.
const fields: Record<string, string[]> = {
  create: ["deviceKey", "inviteToken", "alias", "zone"],
  preview: ["inviteToken"],
  join: ["deviceKey", "inviteToken", "alias", "zone", "role"],
  get: ["deviceKey"],
  check_in: ["deviceKey", "weekId", "day"],
  cheer: ["deviceKey", "weekId", "day", "kind"],
  leave: ["deviceKey", "weekId"],
  renew: ["deviceKey", "weekId"],
};
const status: Record<string, number> = {
  invalid: 422, gone: 410, conflict: 409, stale: 409, slow_down: 429,
};
const encoder = new TextEncoder();
const capability = /^[a-f0-9]{64}$/;
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
const date = /^\d{4}-\d{2}-\d{2}$/;
const invalidAlias = /[\p{Cc}\u061c\u200e\u200f\u202a-\u202e\u2066-\u2069]/u;
const hex = (bytes: ArrayBuffer) =>
  Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
const digest = async (domain: string, value: string) =>
  hex(await crypto.subtle.digest("SHA-256", encoder.encode("fasting-shift-partner:" + domain + ":" + value)));

function reply(code: number, body: unknown) {
  const headers = new Headers({"Cache-Control": "no-store", "Content-Type":"application/json", "X-Content-Type-Options":"nosniff"});
  if (code === 405) headers.set("Allow", "POST");
  if (code === 429) headers.set("Retry-After", "900");
  return new Response(JSON.stringify(body), {status:code, headers});
}

async function readBody(req: Request) {
  const reader = req.body?.getReader();
  if (!reader) return null;
  let total = 0;
  const parts: Uint8Array[] = [];
  try {
    for (;;) {
      const {done,value} = await reader.read();
      if (done) break;
      total += value.length;
      if (total > 2048) {
        await reader.cancel();
        return reply(413, {error:"too_large"});
      }
      parts.push(value);
    }
    const body = new Uint8Array(total);
    let offset = 0;
    for (const part of parts) { body.set(part,offset); offset += part.length; }
    return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(body));
  } catch { return null; }
  finally { reader.releaseLock(); }
}

Deno.serve(async req => {
  if (req.method !== "POST") return reply(405,{error:"method"});
  if (req.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json")
    return reply(415,{error:"media_type"});
  if (Number(req.headers.get("content-length")) > 2048) return reply(413,{error:"too_large"});
  const raw = await readBody(req);
  if (raw instanceof Response) return raw;
  const bad = () => reply(422,{error:"invalid"});
  if (!raw || typeof raw !== "object" || Array.isArray(raw) || typeof raw.action !== "string" || !Object.hasOwn(fields,raw.action)) return bad();
  const keys = fields[raw.action];
  if (Object.keys(raw).length !== keys.length+1 || keys.some(k=>typeof raw[k]!=="string")) return bad();
  const input: Record<string,string> = {action:raw.action};
  if (keys.includes("deviceKey")) {
    if (!capability.test(raw.deviceKey)) return bad();
    input.deviceHash = await digest("device",raw.deviceKey);
  }
  if (keys.includes("inviteToken")) {
    if (!capability.test(raw.inviteToken)) return bad();
    input.inviteHash = await digest("invite",raw.inviteToken);
  }
  for (const k of ["zone","role","weekId","day","kind"]) if (keys.includes(k)) input[k]=raw[k];
  if (keys.includes("alias")) {
    if (invalidAlias.test(raw.alias)) return bad();
    input.alias = raw.alias.trim();
    if (Array.from(input.alias).length < 1 || Array.from(input.alias).length > 32) return bad();
  }
  if (keys.includes("zone") && (input.zone.length < 1 || input.zone.length>64)) return bad();
  if (keys.includes("role") && !["participant","supporter"].includes(input.role)) return bad();
  if (keys.includes("weekId") && !uuid.test(input.weekId)) return bad();
  if (keys.includes("day") && !date.test(input.day)) return bad();
  if (keys.includes("kind") && !["glad","here","thanks"].includes(input.kind)) return bad();
  try {
    const base = Deno.env.get("SUPABASE_URL");
    const modern = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}").default;
    const secret = typeof modern==="string" && modern.startsWith("sb_secret_") ? modern : Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!base || !secret) return reply(503,{error:"unavailable"});
    if (input.action==="create" || input.action==="preview") {
      // Keep only a rotating keyed origin identifier for temporary throttling.
      const forwarded = req.headers.get("x-forwarded-for")?.split(",").at(-1)?.trim() || "shared";
      const signingKey = await crypto.subtle.importKey("raw",encoder.encode(secret),{name:"HMAC",hash:"SHA-256"},false,["sign"]);
      input.originHash = hex(await crypto.subtle.sign("HMAC",signingKey,encoder.encode("fs-partner-origin/"+new Date().toISOString().slice(0,10)+"/"+forwarded)));
    }
    const headers = new Headers({"apikey":secret, "Content-Type":"application/json"});
    if (!secret.startsWith("sb_secret_")) headers.set("Authorization","Bearer "+secret);
    const result = await fetch(new URL("/rest/v1/rpc/fasting_partner_week",base),{
      method:"POST",headers,body:JSON.stringify({p_request:input}),redirect:"error",signal:AbortSignal.timeout(6500),
    });
    if (!result.ok) return reply(503,{error:"unavailable"});
    const data = await result.json();
    if (!data || typeof data!=="object") return reply(503,{error:"unavailable"});
    return reply(data.error ? (status[data.error] || 503) : 200, data);
  } catch { return reply(503,{error:"unavailable"}); }
});

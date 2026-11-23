// Mirrors ChallengeLinks and ChallengeNames in core/domain. Keep the allowlist in sync with Presets.
const dailyProtocols = new Map([
  ["window_12_12", "12:12"], ["window_14_10", "14:10"], ["window_16_8", "16:8"],
  ["window_18_6", "18:6"], ["window_20_4", "20:4"],
]);
const codePattern = /^[A-Za-z0-9_-]{1,300}$/;
const invalid = { status: "invalid" };
const appPackages = new Map([
  ["beta", "com.fastingshift.app.beta"],
  ["staging", "com.fastingshift.app.staging"],
  ["release", "com.fastingshift.app"],
]);

export function codeFromLocation(path, hash, directory) {
  if (path === directory.slice(0, -1) || path === directory || path === directory + "index.html") {
    const code = hash.slice(1);
    return codePattern.test(code) ? code : null;
  }
  if (!path.startsWith(directory)) return null;
  const code = path.slice(directory.length).replace(/\/$/, "");
  return codePattern.test(code) ? code : null;
}

function challengeNameValid(name) {
  if (name !== name.trim() || [...name].length > 40) return false;
  return !/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/u.test(name.replaceAll("\u200d", ""));
}

function startDate(value) {
  if (!/^\d{8}$/.test(value) || value < "20200101" || value > "21001231") return null;
  const iso = value.slice(0, 4) + "-" + value.slice(4, 6) + "-" + value.slice(6, 8);
  const date = new Date(iso + "T00:00:00Z");
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === iso ? date : null;
}

export function decodeChallenge(code) {
  if (typeof code !== "string" || !codePattern.test(code)) return invalid;
  let binary, payload;
  try {
    binary = atob(code.replaceAll("-", "+").replaceAll("_", "/"));
    const bytes = Uint8Array.from(binary, char => char.charCodeAt(0));
    payload = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(bytes);
  } catch {
    return invalid;
  }
  const fields = payload.split("|");
  const [version, type, protocol, dateText, daysText, ...nameParts] = fields;
  if (!/^\d{1,3}$/.test(version)) return invalid;
  if (Number(version) > 1) return { status: "newer" };
  if (version !== "1" || fields.length < 6) return invalid;
  const canonical = btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
  if (code !== canonical) return invalid;

  let schedule;
  if (type === "D" && dailyProtocols.has(protocol)) schedule = dailyProtocols.get(protocol) + " daily fast";
  else if (type === "M" && protocol === "") schedule = "Monday and Thursday fasts";
  else if (type === "W" && protocol === "") schedule = "White days (Ayyamul Bidh)";
  else return invalid;

  const start = startDate(dateText);
  const days = Number(daysText);
  const name = nameParts.join("|");
  if (!start || !/^[1-9]\d{0,2}$/.test(daysText) || days > 366 || !challengeNameValid(name)) return invalid;
  const end = new Date(start);
  end.setUTCDate(end.getUTCDate() + days - 1);
  return { status: "valid", name, schedule, start, end, days };
}

export function inviteAddress(code, inviteRoot, useFragment) {
  if (typeof code !== "string" || !codePattern.test(code)) throw new Error("Invalid invite code");
  return new URL((useFragment ? "#" : "") + code, inviteRoot).href;
}

// Chrome dispatches the HTTPS VIEW intent to the explicitly selected installed package.
// An unavailable app falls back to the installation instructions; the page never auto-launches.
export function appIntent(code, build, inviteRoot) {
  const pkg = appPackages.get(build);
  if (!pkg || typeof code !== "string" || !codePattern.test(code)) throw new Error("Invalid app or invite code");
  const fallback = new URL("?help#" + code, inviteRoot);
  return "intent://fastingshift.com/c/" + code + "#Intent;scheme=https;package=" + pkg
    + ";S.browser_fallback_url=" + encodeURIComponent(fallback.href) + ";end";
}

import { appIntent, codeFromLocation, decodeChallenge, inviteAddress } from "./invite.mjs";

const byId = id => document.getElementById(id);
// Both the branded host and the GitHub Pages project serve this module beside index.html.
const inviteRoot = new URL("./", import.meta.url);
const dates = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
let code, address;

function render() {
  code = codeFromLocation(location.pathname, location.hash, inviteRoot.pathname);
  const invite = decodeChallenge(code);
  byId("invitation").hidden = true;
  byId("install-help").hidden = true;
  byId("manual-copy").hidden = true;
  byId("status").textContent = "";
  byId("open-app").removeAttribute("href");
  if (invite.status !== "valid") {
    const newer = invite.status === "newer";
    byId("page-title").textContent = newer ? "An updated invitation" : "This invite link is incomplete or invalid";
    byId("description").textContent = newer
      ? "This invitation uses a newer format. Ask the sender for the matching app version."
      : "Ask the person who invited you to send the full challenge link again.";
    return;
  }
  const fragment = location.hash.slice(1) === code;
  address = inviteAddress(code, inviteRoot, fragment);
  byId("page-title").textContent = invite.name || invite.schedule;
  byId("page-title").dir = "auto";
  byId("description").textContent = "Review the invitation in Fasting Shift before you join. Your progress stays on your phone unless you choose to share it.";
  byId("schedule-row").hidden = !invite.name;
  byId("schedule").textContent = invite.schedule;
  let dateRange = dates.format(invite.start);
  if (invite.days > 1) {
    dateRange = typeof dates.formatRange === "function"
      ? dates.formatRange(invite.start, invite.end)
      : dateRange + " – " + dates.format(invite.end);
  }
  byId("dates").textContent = dateRange;
  byId("duration").textContent = invite.days + (invite.days === 1 ? " day" : " days");
  byId("invitation").hidden = false;
  byId("install-help").hidden = false;
  byId("android-actions").hidden = !/Android/i.test(navigator.userAgent);
  updateOpenLink();
  if (new URLSearchParams(location.search).has("help")) byId("install-help").scrollIntoView({ block: "nearest" });
}

function updateOpenLink() {
  byId("open-app").href = appIntent(code, byId("build").value, inviteRoot);
}

async function copy(value, successMessage) {
  byId("manual-copy").hidden = true;
  try {
    await navigator.clipboard.writeText(value);
    byId("status").textContent = successMessage;
  } catch {
    byId("manual-copy").hidden = false;
    byId("status").textContent = "Select the text below and copy it.";
    const field = byId("copy-value");
    field.value = value;
    field.focus();
    field.select();
  }
}

byId("build").addEventListener("change", updateOpenLink);
byId("copy-link").addEventListener("click", () => copy(address, "Invite link copied."));
byId("copy-code").addEventListener("click", () => copy(code, "Invite code copied."));
window.addEventListener("hashchange", render);
render();

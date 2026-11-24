import test from "node:test";
import assert from "node:assert/strict";
import { appIntent, codeFromLocation, decodeChallenge, inviteAddress } from "../web/c/invite.mjs";

const encode = text => Buffer.from(text, "utf8").toString("base64url");
const withFields = (type = "D", protocol = "window_16_8", date = "20261123", length = "7", name = "") =>
  encode(["1", type, protocol, date, length, name].join("|"));
const ok = code => assert.equal(decodeChallenge(code).status, "valid");
const bad = code => assert.equal(decodeChallenge(code).status, "invalid");

test("all and only the five built-in daily windows are accepted", () => {
  for (const hours of [12, 14, 16, 18, 20]) ok(withFields("D", "window_" + hours + "_" + (24 - hours)));
  for (const id of ["", "omad", "custom_16", "duration_24", "weekly_5_2", "weekly_adf", "window_17_7", "window_22_2", "constructor"]) {
    bad(withFields("D", id));
  }
  for (const type of ["M", "W"]) {
    ok(withFields(type, ""));
    bad(withFields(type, "window_12_12"));
  }
  bad(withFields("X", ""));
});

test("canonical dates, duration and inclusive end dates match Android", () => {
  const dateChallenge = (date, days) => withFields("M", "", date, days);
  for (const start of ["20191231", "21010101", "20260229", "20260431", "20260001", "20241312", "2026-02-28"]) bad(dateChallenge(start, "1"));
  ok(dateChallenge("20200229", "1"));
  const last = decodeChallenge(dateChallenge("21001231", "366"));
  assert.equal(last.end.toISOString().slice(0, 10), "2101-12-31");
  for (const days of ["0", "00", "01", "367", "+5", "-5", "2.0", "9999", ""]) bad(dateChallenge("20200101", days));
});

test("names retain permitted Unicode and pipes, rejecting hidden and noncanonical content", () => {
  const named = name => withFields("M", "", "20260101", "7", name);
  for (const name of ["<b>Morning</b>", "A|B", "👩‍👩‍👧‍👦", "سحور", "晨".repeat(40)]) {
    assert.equal(decodeChallenge(named(name)).name, name);
  }
  for (const name of ["晨".repeat(41), " A", "A ", "A\u2066B", "X\u200bY", "A\u0000", "A\u2028B"]) bad(named(name));
});

test("strict UTF-8, base64url and version gates match native parser", () => {
  for (const code of [null, "", "x".repeat(301), "Zm8=", "ab/", "a", Buffer.from([0xff,0x80]).toString("base64url"), encode("\ufeff1|M||20260101|1|")]) bad(code);
  const canonical = encode("1|M||20260101|3|a");
  // Change unused bits in the last digit only; decoded payload remains byte-identical.
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
  const index = alphabet.indexOf(canonical.at(-1));
  const alternate = canonical.slice(0,-1) + alphabet[index + 1];
  assert.equal(Buffer.from(canonical,"base64url").toString(), Buffer.from(alternate,"base64url").toString());
  bad(alternate);
  assert.equal(decodeChallenge(encode("002|unknown")).status, "newer");
  bad(encode("01|M||20260101|3|a"));
});

for (const base of ["https://fastingshift.com/c/", "https://nicholsmindset.github.io/fasting/c/"]) {
  test("legacy and private routes retain the invite directory on " + base, () => {
    const root = new URL(base);
    const directory = root.pathname;
    const read = (path, hash) => codeFromLocation(path, hash, directory);
    const code = withFields();
    assert.equal(read(directory + code, ""), code);
    assert.equal(read(directory + code + "/", ""), code);
    for (const path of [directory, directory.slice(0, -1), directory + "index.html"]) {
      assert.equal(read(path, "#" + code), code);
      for (const hash of ["", "#a/b", "#a&b", "#%20"]) assert.equal(read(path, hash), null);
    }
    for (const path of [directory + "x/other", directory + "%41", directory + "a".repeat(301), "/other/" + code]) {
      assert.equal(read(path, ""), null);
    }
    assert.equal(inviteAddress(code, root, true), base + "#" + code);
    assert.equal(inviteAddress(code, root, false), base + code);
    for (const [build, suffix] of [["beta", ".beta"], ["staging", ".staging"], ["release", ""]]) {
      const launch = appIntent(code, build, root);
      assert.ok(launch.startsWith("intent://fastingshift.com/c/" + code + "#Intent;scheme=https;package=com.fastingshift.app" + suffix + ";"));
      const fallback = new URL(decodeURIComponent(launch.match(/S\.browser_fallback_url=([^;]+)/)[1]));
      assert.equal(fallback.origin, root.origin);
      assert.equal(fallback.pathname, directory);
      assert.equal(fallback.search, "?help");
      assert.equal(fallback.hash, "#" + code);
    }
    assert.throws(() => appIntent(code, "unknown", root));
  });
}

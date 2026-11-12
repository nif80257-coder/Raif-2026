/*
 * Node harness for iosApp/Sources/nuvio-compat.js: checks the shims against
 * known vectors and a few scraping patterns before they ship in the iOS bundle.
 */
const fs = require("fs");
const path = require("path");
const vm = require("vm");

const source = fs.readFileSync(
  path.join(__dirname, "..", "iosApp", "Sources", "nuvio-compat.js"),
  "utf8"
);
const sandbox = { console };
sandbox.globalThis = sandbox;
vm.createContext(sandbox);
vm.runInContext(source, sandbox);

let failures = 0;
function check(label, actual, expected) {
  const ok = String(actual) === String(expected);
  if (!ok) failures += 1;
  console.log((ok ? "ok   " : "FAIL ") + label + (ok ? "" : "\n  expected: " + expected + "\n  actual:   " + actual));
}

const CryptoJS = sandbox.CryptoJS;
check("md5('')", CryptoJS.MD5("").toString(), "d41d8cd98f00b204e9800998ecf8427e");
check("md5('abc')", CryptoJS.MD5("abc").toString(), "900150983cd24fb0d6963f7d28e17f72");
check("md5('The quick brown fox jumps over the lazy dog')",
  CryptoJS.MD5("The quick brown fox jumps over the lazy dog").toString(),
  "9e107d9d372bb6826bd81d3542a419d6");
check("sha256('abc')", CryptoJS.SHA256("abc").toString(),
  "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
check("sha256('')", CryptoJS.SHA256("").toString(),
  "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

const cheerio = sandbox.require("cheerio");
const html = `
<html><body>
  <div class="poster"><a href="/watch/tt1" class="link">Film <b>One</b></a></div>
  <div class="poster"><a href="/watch/tt2" class="link">Film Two</a></div>
  <script type="application/ld+json">{"name":"Film One"}</script>
</body></html>`;
const $ = cheerio.load(html);
check("link count", $("a.link").length, 2);
check("first href", $("a.link").first().attr("href"), "/watch/tt1");
check("text", $("div.poster").first().find("a").text().replace(/\s+/g, " ").trim(), "Film One");
check("nth via eq", $("a.link").eq(1).text().trim(), "Film Two");
check("attr selector", $('script[type="application/ld+json"]').text(), '{"name":"Film One"}');
let collected = [];
$("div.poster").each(function () {
  collected.push($(this).find("a").attr("href"));
});
check("each", collected.join(","), "/watch/tt1,/watch/tt2");
check("class match", $("div").filter(".poster").length, 2);
check("id/data absent", $("div.poster").data("id"), undefined);
check("unsupported module throws", (function () {
  try { sandbox.require("fs"); return "no throw"; } catch (error) { return "throws"; }
})(), "throws");

console.log(failures === 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
process.exit(failures === 0 ? 0 : 1);

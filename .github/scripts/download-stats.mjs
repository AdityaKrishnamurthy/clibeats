// Counts CLIBeats APK downloads from GitHub Releases and draws the README charts.
//
// Only .apk files are counted: SHA256SUMS.txt is also fetched by the in-app updater, so counting every
// asset (as the stock shields.io badge does) inflates the number. These are downloads, not unique users:
// one person who installs and later updates twice counts three times.
//
// Usage: node download-stats.mjs <owner/repo> <output dir>   (GITHUB_TOKEN optional, raises the rate limit)
// Writes into <output dir>: downloads.csv (date,total; one row per day), downloads-badge.json (shields
// endpoint), downloads-history.svg and downloads-per-release.svg.

import fs from "node:fs";
import path from "node:path";

const [repo, outDir] = process.argv.slice(2);
if (!repo || !outDir) {
  console.error("usage: node download-stats.mjs <owner/repo> <output dir>");
  process.exit(2);
}

const GREEN = "#1DB954";
const MUTED = "#8B949E";
const GRID = "#8B949E33";
const FONT = "ui-monospace, SFMono-Regular, 'JetBrains Mono', Menlo, Consolas, monospace";

async function fetchReleases() {
  const headers = { Accept: "application/vnd.github+json", "User-Agent": "clibeats-download-stats" };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  const all = [];
  for (let page = 1; ; page++) {
    const res = await fetch(`https://api.github.com/repos/${repo}/releases?per_page=100&page=${page}`, { headers });
    if (!res.ok) throw new Error(`GitHub API ${res.status}: ${await res.text()}`);
    const batch = await res.json();
    all.push(...batch);
    if (batch.length < 100) return all;
  }
}

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const compact = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1e4 ? `${Math.round(n / 1e3)}k` : n >= 1e3 ? `${(n / 1e3).toFixed(1)}k` : `${n}`);

function svg(width, height, body, title) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(title)}">
<title>${esc(title)}</title>
<style>text{font-family:${FONT};fill:${MUTED};font-size:12px}.v{fill:${GREEN};font-weight:600}.t{font-size:13px}</style>
${body}
</svg>
`;
}

function perReleaseChart(releases) {
  const n = releases.length;
  const barW = 44;
  const gap = 28;
  const left = 16;
  const top = 40;
  const plotH = 140;
  const width = Math.max(360, left * 2 + n * barW + (n - 1) * gap);
  const height = top + plotH + 36;
  const max = Math.max(1, ...releases.map((r) => r.apk));
  let body = `<text class="t" x="${left}" y="20">&gt; downloads --per-release</text>`;
  body += `<line x1="${left}" y1="${top + plotH}" x2="${width - left}" y2="${top + plotH}" stroke="${GRID}"/>`;
  releases.forEach((r, i) => {
    const h = Math.max(2, Math.round((r.apk / max) * (plotH - 18)));
    const x = left + i * (barW + gap);
    const y = top + plotH - h;
    body += `<rect x="${x}" y="${y}" width="${barW}" height="${h}" rx="2" fill="${GREEN}"/>`;
    body += `<text class="v" x="${x + barW / 2}" y="${y - 6}" text-anchor="middle">${compact(r.apk)}</text>`;
    body += `<text x="${x + barW / 2}" y="${top + plotH + 18}" text-anchor="middle">${esc(r.tag)}</text>`;
  });
  return svg(width, height, body, "CLIBeats APK downloads per release");
}

function historyChart(rows) {
  const width = 640;
  const height = 220;
  const left = 48;
  const right = 20;
  const top = 40;
  const bottom = 36;
  const plotW = width - left - right;
  const plotH = height - top - bottom;
  const last = rows[rows.length - 1];
  let body = `<text class="t" x="16" y="20">&gt; downloads --history</text>`;
  body += `<text class="v" x="${width - right}" y="20" text-anchor="end">${compact(last.total)} total</text>`;
  const t0 = Date.parse(rows[0].date);
  const t1 = Date.parse(last.date);
  const span = Math.max(1, t1 - t0);
  const max = Math.max(1, ...rows.map((r) => r.total));
  const xOf = (r) => (rows.length === 1 ? left + plotW / 2 : left + ((Date.parse(r.date) - t0) / span) * plotW);
  const yOf = (r) => top + plotH - (r.total / max) * plotH;
  for (const f of [0, 0.5, 1]) {
    const y = top + plotH - f * plotH;
    body += `<line x1="${left}" y1="${y}" x2="${width - right}" y2="${y}" stroke="${GRID}"/>`;
    body += `<text x="${left - 8}" y="${y + 4}" text-anchor="end">${compact(Math.round(f * max))}</text>`;
  }
  const pts = rows.map((r) => `${xOf(r).toFixed(1)},${yOf(r).toFixed(1)}`);
  if (rows.length > 1) {
    body += `<polygon points="${xOf(rows[0]).toFixed(1)},${top + plotH} ${pts.join(" ")} ${xOf(last).toFixed(1)},${top + plotH}" fill="${GREEN}" fill-opacity="0.15"/>`;
    body += `<polyline points="${pts.join(" ")}" fill="none" stroke="${GREEN}" stroke-width="2" stroke-linejoin="round"/>`;
  }
  body += `<circle cx="${xOf(last).toFixed(1)}" cy="${yOf(last).toFixed(1)}" r="4" fill="${GREEN}"/>`;
  if (rows.length > 1) {
    body += `<text x="${left}" y="${height - 12}">${esc(rows[0].date)}</text>`;
    body += `<text x="${width - right}" y="${height - 12}" text-anchor="end">${esc(last.date)}</text>`;
  } else {
    body += `<text x="${left + plotW / 2}" y="${height - 12}" text-anchor="middle">tracking since ${esc(last.date)}</text>`;
  }
  return svg(width, height, body, "CLIBeats APK downloads over time");
}

const releases = (await fetchReleases())
  .filter((r) => !r.draft)
  .map((r) => ({
    tag: r.tag_name,
    published: r.published_at,
    apk: r.assets.filter((a) => a.name.toLowerCase().endsWith(".apk")).reduce((sum, a) => sum + a.download_count, 0),
  }))
  .sort((a, b) => Date.parse(a.published) - Date.parse(b.published));
const total = releases.reduce((sum, r) => sum + r.apk, 0);
const today = new Date().toISOString().slice(0, 10);

fs.mkdirSync(outDir, { recursive: true });
const csvPath = path.join(outDir, "downloads.csv");
const rows = fs.existsSync(csvPath)
  ? fs.readFileSync(csvPath, "utf8").trim().split("\n").slice(1).filter(Boolean).map((line) => {
      const [date, n] = line.split(",");
      return { date, total: Number(n) };
    })
  : [];
const kept = rows.filter((r) => r.date !== today);
kept.push({ date: today, total });
fs.writeFileSync(csvPath, "date,total\n" + kept.map((r) => `${r.date},${r.total}`).join("\n") + "\n");
fs.writeFileSync(
  path.join(outDir, "downloads-badge.json"),
  JSON.stringify({ schemaVersion: 1, label: "downloads", message: compact(total), color: "1DB954" }) + "\n",
);
fs.writeFileSync(path.join(outDir, "downloads-history.svg"), historyChart(kept));
fs.writeFileSync(path.join(outDir, "downloads-per-release.svg"), perReleaseChart(releases));
console.log(`APK downloads: ${total} across ${releases.length} releases`);

// Counts CLIBeats APK downloads from GitHub Releases and draws the README charts.
//
// Only .apk files are counted: SHA256SUMS.txt is also fetched by the in-app updater, so counting every
// asset (as the stock shields.io badge does) inflates the number. These are downloads, not unique users:
// one person who installs and later updates twice counts three times.
//
// Usage: node download-stats.mjs <owner/repo> <output dir>   (GITHUB_TOKEN optional, raises the rate limit)
// Writes into <output dir>: downloads.csv (date,total; one row per day), downloads-badge.json (shields
// endpoint) and downloads-history.svg (the running total over time).

import fs from "node:fs";
import path from "node:path";

const [repo, outDir] = process.argv.slice(2);
if (!repo || !outDir) {
  console.error("usage: node download-stats.mjs <owner/repo> <output dir>");
  process.exit(2);
}

const GREEN = "#1DB954";
const MUTED = "#8B949E";
const GRID = "#8B949E40";
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
<defs>
  <linearGradient id="fill" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="${GREEN}" stop-opacity="0.35"/>
    <stop offset="1" stop-color="${GREEN}" stop-opacity="0"/>
  </linearGradient>
  <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
    <feGaussianBlur stdDeviation="3"/>
  </filter>
</defs>
<style>
  text{font-family:${FONT};fill:${MUTED};font-size:13px}
  .title{font-size:20px;font-weight:700}
  .axis{font-size:14px;font-weight:600}
  .value{fill:${GREEN};font-weight:700;font-size:15px}
  .small{font-size:12px}
</style>
${body}
</svg>
`;
}

/** A round step (1, 2, 5 × 10^n) that splits [0, max] into about [count] parts. */
function niceStep(max, count) {
  const raw = Math.max(1, max / count);
  const pow = 10 ** Math.floor(Math.log10(raw));
  const unit = [1, 2, 5, 10].find((m) => m * pow >= raw);
  return unit * pow;
}

/** Smooth line through the points that never overshoots (monotone cubic), so a running total never dips. */
function smoothPath(pts) {
  if (pts.length < 3) return `M${pts.map((p) => `${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" L")}`;
  const n = pts.length;
  const d = [];
  const m = [];
  for (let i = 0; i < n - 1; i++) d.push((pts[i + 1][1] - pts[i][1]) / (pts[i + 1][0] - pts[i][0] || 1));
  m.push(d[0]);
  for (let i = 1; i < n - 1; i++) m.push(d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2);
  m.push(d[n - 2]);
  for (let i = 0; i < n - 1; i++) {
    if (d[i] === 0) {
      m[i] = 0;
      m[i + 1] = 0;
      continue;
    }
    const a = m[i] / d[i];
    const b = m[i + 1] / d[i];
    const h = a * a + b * b;
    if (h > 9) {
      const t = 3 / Math.sqrt(h);
      m[i] = t * a * d[i];
      m[i + 1] = t * b * d[i];
    }
  }
  let path = `M${pts[0][0].toFixed(1)},${pts[0][1].toFixed(1)}`;
  for (let i = 0; i < n - 1; i++) {
    const dx = (pts[i + 1][0] - pts[i][0]) / 3;
    path += ` C${(pts[i][0] + dx).toFixed(1)},${(pts[i][1] + m[i] * dx).toFixed(1)} ${(pts[i + 1][0] - dx).toFixed(1)},${(pts[i + 1][1] - m[i + 1] * dx).toFixed(1)} ${pts[i + 1][0].toFixed(1)},${pts[i + 1][1].toFixed(1)}`;
  }
  return path;
}

const DAY = 86_400_000;
const fmtDate = (t, withYear) =>
  new Date(t).toLocaleDateString("en-US", { month: "short", day: "numeric", ...(withYear ? { year: "numeric" } : {}), timeZone: "UTC" });

function historyChart(rows) {
  const width = 800;
  const height = 480;
  const left = 84;
  const right = 40;
  const top = 76;
  const bottom = 80;
  const plotW = width - left - right;
  const plotH = height - top - bottom;
  const last = rows[rows.length - 1];
  const t0 = Date.parse(rows[0].date);
  const t1 = Math.max(Date.parse(last.date), t0 + DAY);
  const step = niceStep(Math.max(1, last.total), 4);
  const max = Math.max(step, Math.ceil(Math.max(1, ...rows.map((r) => r.total)) / step) * step);
  const xOf = (t) => left + ((t - t0) / (t1 - t0)) * plotW;
  const yOf = (v) => top + plotH - (v / max) * plotH;

  // Title, like star-history's: a download glyph and the name, centred.
  let body = `<g transform="translate(${width / 2 - 108},22)" fill="none" stroke="${GREEN}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
    <path d="M10 2v12M5 9l5 5 5-5M3 18h14"/></g>`;
  body += `<text class="title" x="${width / 2 + 12}" y="40" text-anchor="middle">Download History</text>`;

  // Grid and the y axis (downloads).
  for (let v = 0; v <= max; v += step) {
    const y = yOf(v);
    body += `<line x1="${left}" y1="${y}" x2="${width - right}" y2="${y}" stroke="${GRID}" ${v === 0 ? "" : 'stroke-dasharray="4 6"'}/>`;
    body += `<text x="${left - 12}" y="${y + 4}" text-anchor="end">${compact(v)}</text>`;
  }
  body += `<text class="axis" transform="translate(22,${top + plotH / 2}) rotate(-90)" text-anchor="middle">APK downloads</text>`;

  // The x axis (dates): about five evenly spaced labels, or one per day over a short span.
  const days = Math.round((t1 - t0) / DAY);
  const ticks = days < 4 ? days + 1 : 5;
  const longSpan = t1 - t0 > 300 * DAY;
  for (let i = 0; i < ticks; i++) {
    const t = t0 + ((t1 - t0) * i) / (ticks - 1);
    const anchor = i === 0 ? "start" : i === ticks - 1 ? "end" : "middle";
    body += `<line x1="${xOf(t)}" y1="${top + plotH}" x2="${xOf(t)}" y2="${top + plotH + 6}" stroke="${MUTED}" stroke-opacity="0.6"/>`;
    body += `<text x="${xOf(t)}" y="${top + plotH + 24}" text-anchor="${anchor}">${esc(fmtDate(t, longSpan))}</text>`;
  }
  body += `<text class="axis" x="${left + plotW / 2}" y="${height - 22}" text-anchor="middle">Date</text>`;

  // Legend, top left of the plot.
  body += `<g transform="translate(${left + 16},${top + 8})">
    <rect width="214" height="30" rx="6" fill="none" stroke="${MUTED}" stroke-opacity="0.5"/>
    <rect x="12" y="11" width="10" height="10" rx="2" fill="${GREEN}"/>
    <text class="small" x="30" y="20">Omprakash-p06/clibeats</text></g>`;

  // The line, its shaded area and the latest point.
  const pts = rows.map((r) => [xOf(Date.parse(r.date)), yOf(r.total)]);
  const line = smoothPath(pts);
  const baseY = yOf(0);
  body += `<path d="${line} L${pts[pts.length - 1][0].toFixed(1)},${baseY} L${pts[0][0].toFixed(1)},${baseY} Z" fill="url(#fill)"/>`;
  body += `<path d="${line}" fill="none" stroke="${GREEN}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>`;
  const [lx, ly] = pts[pts.length - 1];
  body += `<circle cx="${lx}" cy="${ly}" r="9" fill="${GREEN}" opacity="0.5" filter="url(#glow)"/>`;
  body += `<circle cx="${lx}" cy="${ly}" r="5" fill="${GREEN}"/>`;
  body += `<text class="value" x="${lx - 12}" y="${ly - 14}" text-anchor="end">${compact(last.total)}</text>`;
  body += `<text class="small" x="${width - right}" y="${height - 22}" text-anchor="end">updated ${esc(fmtDate(Date.parse(last.date), true))}</text>`;
  return svg(width, height, body, `CLIBeats APK downloads over time: ${last.total} total`);
}

const releases = (await fetchReleases())
  .filter((r) => !r.draft)
  .map((r) => ({
    published: r.published_at,
    apk: r.assets.filter((a) => a.name.toLowerCase().endsWith(".apk")).reduce((sum, a) => sum + a.download_count, 0),
  }));
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
// Before the first release there was nothing to download: the line starts there at 0 (a real data point),
// so the chart reads well from the first day of tracking.
const firstRelease = releases.map((r) => r.published?.slice(0, 10)).filter(Boolean).sort()[0];
const chartRows = firstRelease && firstRelease < kept[0].date ? [{ date: firstRelease, total: 0 }, ...kept] : kept;
fs.writeFileSync(path.join(outDir, "downloads-history.svg"), historyChart(chartRows));
console.log(`APK downloads: ${total} across ${releases.length} releases`);

// Lists interface texts that stay in English when Spanish is chosen: node tests/i18n_coverage.mjs [html]
// Drives the main screens in English, switches each one to Spanish and collects leftover English texts.
import { chromium } from 'playwright';
import path from 'node:path';

const file = path.resolve(process.argv[2] || new URL('../dist/smart-garden-planner.html', import.meta.url).pathname);
const browser = await chromium.launch();
const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage();
const btn = name => page.getByRole('button', { name, exact: true });
async function toScreen(x, y) {
  return page.evaluate(([x, y]) => { const m = document.getElementById('sgp-svg').getScreenCTM(); return { x: m.a * x + m.c * y + m.e, y: m.b * x + m.d * y + m.f }; }, [x, y]);
}
async function drag(x0, y0, x1, y1) {
  const a = await toScreen(x0, y0), b = await toScreen(x1, y1);
  await page.mouse.move(a.x, a.y); await page.mouse.down(); await page.mouse.move(b.x, b.y, { steps: 6 }); await page.mouse.up();
}
const ENGLISH = /\b(the|and|of|to|is|are|with|for|your|this|plants?|plot|area|season|water|sun|shade|add|open|save|click|tap|choose|days?|row|rows|from|not|you|it|in|on|at|by)\b/i;
const left = new Map();
async function collect(where) {
  const texts = await page.evaluate(() => {
    const out = [];
    const w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    while (w.nextNode()) { const t = w.currentNode.nodeValue.trim(); const p = w.currentNode.parentElement; if (t && p && !['SCRIPT', 'STYLE'].includes(p.tagName) && p.offsetParent !== null) out.push(t); }
    document.querySelectorAll('[title],[placeholder]').forEach(e => { if (e.offsetParent !== null) { if (e.title) out.push(e.title); if (e.placeholder) out.push(e.placeholder); } });
    const es = window.sgpTranslate('es', out);
    return out.filter((t, i) => es[i] === t);
  });
  texts.filter(t => process.env.ALL ? /[A-Za-z]{2}/.test(t) : ENGLISH.test(t)).forEach(t => { if (!left.has(t)) left.set(t, where); });
}

await page.goto('file://' + file);
await page.waitForSelector('.modal');
await collect('disclaimer');
await btn('I understand').click();
await page.waitForSelector('.modal');
await collect('help');
await btn('Close').click();
// Measure from a photo: each step, the result with its warning, and a refused reference.
const cam = (() => {
  const C = [4, -5, 3.5], T = [4, 2.5, 0], n = Math.hypot(T[0] - C[0], T[1] - C[1], T[2] - C[2]);
  const f = [(T[0] - C[0]) / n, (T[1] - C[1]) / n, (T[2] - C[2]) / n], r = [1, 0, 0], u = [0, -f[2], f[1]];
  return ([X, Y]) => { const d = [X - C[0], Y - C[1], -C[2]]; const dot = v => d[0] * v[0] + d[1] * v[1] + d[2] * v[2]; return [800 + 1000 * dot(r) / dot(f), 600 - 1000 * dot(u) / dot(f)]; };
})();
const bedPx = [[1, 1], [3.4384, 1], [3.4384, 2.2192], [1, 2.2192]].map(cam);
const lPx = [[0, 0], [8, 0], [8, 2], [4, 2], [4, 5], [0, 5]].map(cam);
const photo = await page.evaluate(() => { const c = document.createElement('canvas'); c.width = 1600; c.height = 1200; const g = c.getContext('2d'); g.fillStyle = '#4d7c0f'; g.fillRect(0, 0, 1600, 1200); return c.toDataURL('image/png').split(',')[1]; });
async function clickPhoto([x, y]) {
  await page.evaluate(() => document.querySelector('.modal svg.measure-photo').scrollIntoView({ block: 'center' }));
  const p = await page.evaluate(([x, y]) => { const m = document.querySelector('.modal svg.measure-photo').getScreenCTM(); return { x: m.a * x + m.c * y + m.e, y: m.b * x + m.d * y + m.f }; }, [x, y]);
  await page.mouse.click(p.x, p.y);
}
await btn('New plot').first().click();
const [measureChooser] = await Promise.all([page.waitForEvent('filechooser'), btn('Measure from a photo…').click()]);
await measureChooser.setFiles({ name: 'yard.png', mimeType: 'image/png', buffer: Buffer.from(photo, 'base64') });
await page.locator('.modal svg.measure-photo').waitFor();
await collect('measure step 1');
for (const p of bedPx) await clickPhoto(p);
await collect('measure step 2');
await clickPhoto(lPx[0]); await clickPhoto(lPx[1]);
await collect('measure distance');
for (const p of lPx.slice(2)) await clickPhoto(p);
await collect('measure result');
await page.locator('.modal select').first().selectOption('CUSTOM');
await collect('measure custom reference');
await page.locator('.modal select').first().selectOption('RAISED_BED_4X8');
await btn('Start over').click();
for (const i of [0, 2, 1, 3]) await clickPhoto(bedPx[i]);
await clickPhoto(lPx[0]); await clickPhoto(lPx[1]);
await collect('measure refused');
await btn('Cancel').click();

await btn('New plot').first().click();
const modal = page.locator('.modal');
await modal.locator('input').nth(0).fill('Test bed');
await modal.locator('input').nth(1).fill('12');
await modal.locator('input').nth(2).fill('9');
await modal.locator('input').nth(3).fill('48104');
await modal.locator('input').nth(3).dispatchEvent('input');
await modal.getByRole('button', { name: 'S', exact: true }).click();
await modal.locator('label.chip-check', { hasText: 'Deer' }).locator('input').check();
await collect('new plot');
await btn('Create plot').click();
for (const tab of ['Plants', 'Plot', 'Care', 'Food', 'Harmony']) {
  const t = page.locator('.tabs .tab', { hasText: new RegExp('^' + tab + '$') });
  if (await t.count()) { await t.click(); await collect('tab ' + tab); }
}
await page.locator('.tabs .tab', { hasText: /^Plants$/ }).click();
await page.locator('nav.tools').getByRole('button', { name: 'Plan an area for me' }).click();
await drag(0.3, 0.3, 11.7, 8.7);
await page.locator('.modal').waitFor();
const rows = [['Sweet Corn - Honey Select', 20], ['Tomato - San Marzano', 6], ['Winter Squash - Spaghetti', 3], ['Summer Squash Mix - Mid-Season', 3], ['Carrot - Danvers', 30]];
for (let i = 0; i < rows.length; i++) {
  if (i > 0) await page.locator('.modal').getByRole('button', { name: /Add another variety|\+ Add/ }).first().click().catch(() => {});
  const inp = page.locator('.plan-row .grow input').nth(i);
  if (!(await inp.count())) break;
  await inp.fill(rows[i][0]); await inp.dispatchEvent('change');
  await page.locator('.plan-row input.count').nth(i).fill(String(rows[i][1])); await page.locator('.plan-row input.count').nth(i).dispatchEvent('input');
}
await collect('plan dialog');
await btn('Plan it').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
await collect('proposal');
await btn('Keep this plan').click();
for (const tab of ['Care', 'Food', 'Harmony', 'Plot']) {
  const t = page.locator('.tabs .tab', { hasText: new RegExp('^' + tab + '$') });
  if (await t.count()) { await t.click(); await collect('planted, tab ' + tab); }
}
await btn('Planting calendar…').click().catch(() => {});
if (await page.locator('.modal').count()) { await collect('planting calendar'); await btn('Close').click(); }
for (const [t, w] of left) console.log(w.padEnd(22), '|', t.slice(0, 220));
console.log(left.size, 'texts left in English');
await browser.close();
if (!process.env.ALL && left.size > 0) process.exit(1);

// Browser smoke test for the portable planner: node tests/smoke.mjs [path-to-html]
// Drives the real single-file page in headless Chromium: create a plot, plant, add/move/undo a tree,
// plan an area, save a plan file, reload (draft restore) and re-open the saved file.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const file = path.resolve(process.argv[2] || new URL('../dist/smart-garden-planner.html', import.meta.url).pathname);
const shots = process.env.SGP_SHOTS || os.tmpdir();
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1400, height: 900 }, acceptDownloads: true });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', e => errors.push('pageerror: ' + e.message));
page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
// Force the download / <input type=file> paths, which every browser supports.
await page.addInitScript(() => { delete window.showSaveFilePicker; delete window.showOpenFilePicker; window.showSaveFilePicker = undefined; window.showOpenFilePicker = undefined; });

let failures = 0;
function check(cond, msg) { console.log((cond ? 'PASS ' : 'FAIL ') + msg); if (!cond) failures++; }

async function draft() {
  return page.evaluate(() => JSON.parse(localStorage.getItem('sgp.draft') || 'null'));
}
async function clickMetres(x, y, opts = {}) {
  const p = await toScreen(x, y);
  await page.mouse.click(p.x, p.y, opts);
}
async function toScreen(x, y) {
  return page.evaluate(([x, y]) => {
    const svg = document.getElementById('sgp-svg');
    const m = svg.getScreenCTM();
    return { x: m.a * x + m.c * y + m.e, y: m.b * x + m.d * y + m.f };
  }, [x, y]);
}
async function drag(x0, y0, x1, y1) {
  const a = await toScreen(x0, y0), b = await toScreen(x1, y1);
  await page.mouse.move(a.x, a.y); await page.mouse.down();
  await page.mouse.move((a.x + b.x) / 2, (a.y + b.y) / 2, { steps: 4 });
  await page.mouse.move(b.x, b.y, { steps: 4 }); await page.mouse.up();
}
const btn = name => page.getByRole('button', { name, exact: true });

await page.goto('file://' + file);
await page.waitForSelector('.modal');
check(await page.locator('.modal-title').innerText() === 'How to use Smart Garden Planner', 'help opens on first visit');
await btn('Close').click();

// New plot with ZIP -> zone lookup and orientation.
await btn('New plot').first().click();
const modal = page.locator('.modal');
await modal.locator('input').nth(0).fill('Test bed');
await modal.locator('input').nth(1).fill('8');
await modal.locator('input').nth(2).fill('5');
await modal.locator('input').nth(3).fill('48104');
await modal.locator('input').nth(3).dispatchEvent('input');
const zone = await modal.locator('select').inputValue();
check(zone === '6a' || zone === '6b', `ZIP 48104 fills zone (${zone})`);
check((await modal.innerText()).includes('latitude 42.'), 'ZIP fills latitude');
await modal.getByRole('button', { name: 'S', exact: true }).click();
await btn('Create plot').click();
let d = await draft();
check(d && d.plots.length === 1 && d.plots[0].name === 'Test bed', 'plot created and autosaved');
check(d.plots[0].orientation.set === true && d.plots[0].orientation.topFacesDeg === 180, 'orientation saved');

// Plant a tomato.
await page.getByPlaceholder(/Search .* varieties/).fill('Brandywine');
await page.locator('button.seed').first().click();
await clickMetres(4, 4);
d = await draft();
check(d.plots[0].plants.length === 1, 'planted one tomato');
await clickMetres(4.05, 4.05);
d = await draft();
check(d.plots[0].plants.length === 1, 'spacing rule blocks a second plant on top');
check((await page.locator('.status').innerText()).startsWith('Too close'), 'status explains the block');

// Tree: add, move, undo.
await page.locator('nav.tools').getByRole('button', { name: 'Tree', exact: true }).click();
await clickMetres(1, 1);
await page.locator('.modal').waitFor();
await btn('Add').click();
d = await draft();
check(d.plots[0].siteFeatures.length === 1, 'tree added');
const before = d.plots[0].siteFeatures[0].points;
await page.locator('nav.tools').getByRole('button', { name: 'Select / move' }).click();
await drag(1, 1, 2.5, 1.5);
d = await draft();
const after = d.plots[0].siteFeatures[0].points;
check(JSON.stringify(before) !== JSON.stringify(after), `tree moved (${JSON.stringify(after)})`);
await page.keyboard.press('Control+z');
d = await draft();
check(JSON.stringify(d.plots[0].siteFeatures[0].points) === JSON.stringify(before), 'undo puts the tree back');
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].siteFeatures.length === 0, 'undo removes the tree');
await page.keyboard.press('Control+y');
d = await draft();
check(d.plots[0].siteFeatures.length === 1, 'redo brings it back');

// Shade overlay renders.
await page.getByRole('button', { name: /Shade:/ }).click();
check(await page.locator('#sgp-svg rect[fill="#000"]').count() > 0, 'shade overlay drawn');

// Plan an area for me.
await page.locator('nav.tools').getByRole('button', { name: 'Plan an area for me' }).click();
await drag(0.3, 2.2, 7.7, 4.8);
await page.locator('.modal').waitFor();
const rows = await page.locator('.plan-row').count();
check(rows >= 1, `plan dialog suggests ${rows} rows`);
await btn('Plan it').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
check((await page.locator('#sgp-preview').innerText()).includes('Placed:'), 'preview shows placed plants');
await btn('Keep this plan').click();
d = await draft();
const planted = d.plots[0].plants.length;
check(planted > 1, `plan kept (${planted} plants total)`);
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].plants.length === 1, 'one undo removes the whole plan');
await page.keyboard.press('Control+y');

// Tabs render without errors.
for (const t of ['Plot', 'Harmony', 'Care', 'Food', 'Plants']) {
  await page.locator('.tabs .tab', { hasText: new RegExp('^' + t + '$') }).click();
  check(await page.locator('.panel-body').innerText() !== '', `${t} tab renders`);
}
await page.screenshot({ path: path.join(shots, 'sgp-web.png') });

// Save (download) and check the file.
const [download] = await Promise.all([page.waitForEvent('download'), btn('Save').click()]);
const saved = path.join(shots, 'sgp-smoke.sgp.json');
await download.saveAs(saved);
const json = JSON.parse(fs.readFileSync(saved, 'utf8'));
check(json.format === 'smart-garden-plan' && json.version === 1, 'saved file has the plan-file header');
check(json.plots.length === 1 && json.plots[0].plants.length === planted, 'saved file has all plants');

// Reload: draft restored.
await page.reload();
await page.waitForSelector('.panel');
check((await page.locator('.status').innerText()).includes('Restored'), 'draft restored after reload');

// Open the saved file.
page.once('dialog', dlg => dlg.accept());
const [chooser] = await Promise.all([page.waitForEvent('filechooser'), (async () => {
  await btn('Open…').click();
  if (await page.locator('.modal').count()) await btn('Open anyway').click();
})()]);
await chooser.setFiles(saved);
await page.waitForFunction(() => document.querySelector('.status').textContent.startsWith('Opened'));
d = await draft();
check(d.plots[0].plants.length === planted, 're-opened file has the same plants');

// Phone-size layout.
await page.setViewportSize({ width: 412, height: 915 });
await page.waitForTimeout(200);
const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
check(!overflow, 'no horizontal scroll at phone width');
await page.screenshot({ path: path.join(shots, 'sgp-web-phone.png') });

check(errors.length === 0, 'no script errors' + (errors.length ? ': ' + errors.join(' | ') : ''));
await browser.close();
console.log(failures ? `${failures} check(s) failed` : 'all checks passed');
process.exit(failures ? 1 : 0);

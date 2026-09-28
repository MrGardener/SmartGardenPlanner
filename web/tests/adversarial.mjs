// Adversarial browser tests for the computer planner (SGP-TCS-001 §4): bad values in dialogs, hostile plan files,
// script injection in names, undo/redo storms and a seeded random "monkey" run. node tests/adversarial.mjs [html]
// Every check prints PASS/FAIL; any page error fails the run.
import { chromium } from 'playwright';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const file = path.resolve(process.argv[2] || new URL('../dist/smart-garden-planner.html', import.meta.url).pathname);
const browser = await chromium.launch();
const page = await (await browser.newContext({ viewport: { width: 1400, height: 900 } })).newPage();
const errors = [];
page.on('pageerror', e => errors.push('pageerror: ' + e.message));
page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
await page.addInitScript(() => { window.showSaveFilePicker = undefined; window.showOpenFilePicker = undefined; });
let failures = 0;
const check = (cond, msg) => { console.log((cond ? 'PASS ' : 'FAIL ') + msg); if (!cond) failures++; };
const btn = name => page.getByRole('button', { name, exact: true });
const draft = () => page.evaluate(() => JSON.parse(localStorage.getItem('sgp.draft') || 'null'));
const status = () => page.locator('.status').innerText();
// A small seeded random generator, so a failing monkey run can be repeated exactly.
let seed = Number(process.env.SGP_SEED || 20260928);
const rnd = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; };

await page.goto('file://' + file);
await page.waitForSelector('.modal');
await btn('I understand').click();
await page.waitForSelector('.modal');
await btn('Close').click();

// ------------------------------------------------------------------ New plot: every bad value is refused with a reason.
// Number boxes refuse letters as you type; a tampered or pasted value is forced in to check the page still refuses it.
async function fillAny(loc, v) { try { await loc.fill(v, { timeout: 1000 }); } catch { await loc.evaluate((e, val) => { e.value = val; e.dispatchEvent(new Event('input', { bubbles: true })); }, v); } }
async function tryPlot(fields, expectOk, why) {
  await btn('New plot').first().click();
  const m = page.locator('.modal');
  const inputs = m.locator('input');
  const values = { name: 'Bed', length: '8', width: '5', zip: '', ...fields };
  await fillAny(inputs.nth(0), values.name); await fillAny(inputs.nth(1), values.length); await fillAny(inputs.nth(2), values.width); await fillAny(inputs.nth(3), values.zip);
  if (values.ph !== undefined || values.organic !== undefined || values.sand !== undefined) {
    await m.locator('details.soil summary').click();
    const soil = m.locator('details.soil input');
    if (values.sand !== undefined) await fillAny(soil.nth(0), values.sand);
    if (values.organic !== undefined) await fillAny(soil.nth(3), values.organic);
    if (values.ph !== undefined) await fillAny(soil.nth(4), values.ph);
  }
  const before = ((await draft())?.plots || []).length;
  await btn('Create plot').click();
  const after = ((await draft())?.plots || []).length;
  const ok = after === before + 1;
  if (!ok && await page.locator('.modal').count()) await btn('Cancel').click();
  check(ok === expectOk, `new plot ${why}: ${expectOk ? 'accepted' : 'refused'}${ok ? '' : ' (' + (await status()).slice(0, 60) + ')'}`);
}
for (const [f, ok, why] of [
  [{ name: '' }, false, 'empty name'], [{ name: '   ' }, false, 'blank name'],
  [{ length: '0.49' }, false, 'length 0.49 m'], [{ length: '1000.01' }, false, 'length 1000.01 m'], [{ length: 'NaN' }, false, 'length NaN'],
  [{ length: 'abc' }, false, 'length abc'], [{ width: '-5' }, false, 'negative width'], [{ width: 'Infinity' }, false, 'width Infinity'],
  [{ zip: 'abcde' }, false, 'ZIP abcde'], [{ zip: '1234' }, false, 'ZIP 1234'], [{ ph: '11' }, false, 'pH 11'], [{ organic: '101' }, false, 'organic 101 %'],
  [{ sand: '40' }, false, 'only sand given'],
  [{ length: '0.5', width: '0.5' }, true, 'minimum size 0.5 m'], [{ length: '1000', width: '1000' }, true, 'maximum size 1000 m'],
  [{ length: '3.5', width: '2.25' }, true, 'decimals'], [{ zip: '48104' }, true, 'valid ZIP'],
  [{ name: '<img src=x onerror="window.__xss=1">' }, true, 'name with HTML'],
]) await tryPlot(f, ok, why);
check(await page.evaluate(() => window.__xss === undefined), 'HTML in a plot name is shown as text, never run');
const plotsNow = (await draft()).plots;
check(plotsNow.every(p => p.lengthM >= 0.5 && p.lengthM <= 1000 && Number.isFinite(p.lengthM)), 'every stored plot has a real size');

// ------------------------------------------------------------------ Hostile plan files are refused without harm.
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sgp-adv-'));
const hostile = {
  'deep.sgp.json': '['.repeat(200000) + ']'.repeat(200000),
  'truncated.sgp.json': '{"format":"smart-garden-plan","version":1,"plots":[{"name":"X","lengthM":8',
  'wrong.sgp.json': '{"format":"something-else","version":1,"plots":[]}',
  'newer.sgp.json': '{"format":"smart-garden-plan","version":99,"plots":[]}',
  'numbers.sgp.json': '{"format":"smart-garden-plan","version":1,"plots":[{"name":"N","lengthM":1e999,"widthM":-3}]}',
  'binary.sgp.json': Buffer.from(Array.from({ length: 5000 }, (_, i) => (i * 7919) % 256)),
};
for (const [name, content] of Object.entries(hostile)) {
  const p = path.join(tmp, name); fs.writeFileSync(p, content);
  const before = JSON.stringify((await draft()).plots.map(x => x.name));
  page.once('dialog', d => d.accept());
  const [chooser] = await Promise.all([page.waitForEvent('filechooser'), (async () => { await btn('Open…').click(); if (await page.locator('.modal').count()) await btn('Open anyway').click(); })()]);
  await chooser.setFiles(p);
  await page.locator('.modal').waitFor({ timeout: 5000 });
  const said = await page.locator('.modal').innerText();
  const after = JSON.stringify((await draft()).plots.map(x => x.name));
  check(before === after && errors.length === 0 && said.includes("Couldn't open"), `hostile file ${name} refused with a reason, plan unchanged (${said.split('\n').slice(1, 2).join('').slice(0, 70)})`);
  await page.keyboard.press('Escape');
  if (await page.locator('.modal').count()) await btn('OK').click().catch(() => {});
}

// ------------------------------------------------------------------ Undo/redo storm returns to the same state.
await page.locator('.tabs .tab', { hasText: /^Plants$/ }).click();
await page.getByPlaceholder(/Search .* varieties/).fill('Carrot - Danvers');
await page.locator('button.seed').first().click();
const svgBox = await page.locator('#sgp-svg').boundingBox();
for (let i = 0; i < 25; i++) await page.mouse.click(svgBox.x + 60 + (i % 5) * 40, svgBox.y + 60 + Math.floor(i / 5) * 40);
const full = JSON.stringify((await draft()).plots);
for (let i = 0; i < 40; i++) await page.keyboard.press('Control+z');
for (let i = 0; i < 40; i++) await page.keyboard.press('Control+y');
check(JSON.stringify((await draft()).plots) === full, 'undo 40× then redo 40× gives the same plan');

// ------------------------------------------------------------------ Monkey: random tools, clicks, drags and keys.
const tools = await page.locator('nav.tools button').all();
const keys = ['Escape', 'Enter', 'Delete', 'Control+z', 'Control+y', 'Tab', 'ArrowLeft', '+', '-'];
for (let step = 0; step < 400; step++) {
  const r = rnd();
  try {
    if (await page.locator('.modal').count() && rnd() < 0.7) { await page.keyboard.press('Escape'); continue; }
    if (r < 0.2 && tools.length) await tools[Math.floor(rnd() * tools.length)].click({ timeout: 500 });
    else if (r < 0.55) await page.mouse.click(svgBox.x + rnd() * svgBox.width, svgBox.y + rnd() * svgBox.height);
    else if (r < 0.75) { const x = svgBox.x + rnd() * svgBox.width, y = svgBox.y + rnd() * svgBox.height; await page.mouse.move(x, y); await page.mouse.down(); await page.mouse.move(x + (rnd() - 0.5) * 300, y + (rnd() - 0.5) * 300, { steps: 3 }); await page.mouse.up(); }
    else if (r < 0.9) await page.keyboard.press(keys[Math.floor(rnd() * keys.length)]);
    else { const tabs = await page.locator('.tabs .tab').all(); if (tabs.length) await tabs[Math.floor(rnd() * tabs.length)].click({ timeout: 500 }); }
  } catch (e) { /* an element went away between choosing and clicking: fine for a monkey */ }
  if (errors.length) { check(false, `monkey step ${step}: ${errors[0]}`); break; }
}
const end = await draft();
check(end && Array.isArray(end.plots) && end.plots.length >= 1, 'after 400 random actions the plan is still readable');
check(end.plots.every(p => (p.plants || []).every(pl => Number.isFinite(pl.x) && Number.isFinite(pl.y) && pl.x >= 0 && pl.y >= 0 && pl.x <= p.lengthM + 1e-6 && pl.y <= p.widthM + 1e-6)), 'every plant is inside its plot after the monkey run');
check(errors.length === 0, 'no script errors' + (errors.length ? ': ' + errors.slice(0, 3).join(' | ') : ''));
await browser.close();
console.log(failures ? `${failures} check(s) failed` : 'all adversarial checks passed');
process.exit(failures ? 1 : 0);

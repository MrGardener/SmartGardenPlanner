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
check((await page.locator('.modal-title').innerText()).includes('not a guarantee'), 'disclaimer shown before first use');
check((await page.locator('.modal').innerText()).includes('does not guarantee'), 'disclaimer says results are not guaranteed');
await btn('I understand').click();
await page.waitForSelector('.modal');
check(await page.locator('.modal-title').innerText() === 'How to use Smart Garden Planner', 'help opens on first visit');
check((await page.locator('.modal').innerText()).includes('Disclaimer'), 'help repeats the disclaimer');
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
check((await modal.innerText()).includes('What pests or animals do you see'), 'new plot asks which pests visit the yard');
await modal.locator('label.chip-check', { hasText: 'Deer' }).locator('input').check();
await modal.locator('label.chip-check', { hasText: 'Raccoons' }).locator('input').check();
await btn('Create plot').click();
let d = await draft();
check(d && d.plots.length === 1 && d.plots[0].name === 'Test bed', 'plot created and autosaved');
check(d.plots[0].orientation.set === true && d.plots[0].orientation.topFacesDeg === 180, 'orientation saved');
check(JSON.stringify(d.plots[0].pests) === '["DEER","RACCOON"]', `pests saved with the plot (${JSON.stringify(d.plots[0].pests)})`);
// Rulers: numbers on the ticks, the unit once.
check(await page.locator('#sgp-svg .ruler-unit').count() === 1 && (await page.locator('#sgp-svg .ruler-tick').evaluateAll(els => els.map(e => e.textContent))).every(t => /^\d+$/.test(t)), 'ruler ticks show numbers only, unit once');
// Growing season from the ZIP's nearest NOAA station.
await page.locator('.tabs .tab', { hasText: /^Plot$/ }).click();
const plotTab = await page.locator('.panel-body').innerText();
check(plotTab.includes('Growing season') && /Last spring frost: around May \d+/.test(plotTab) && plotTab.includes('frost-free days'), 'growing season and frost dates shown for the ZIP');
await btn('Planting calendar…').click();
await page.locator('.modal').waitFor();
check((await page.locator('.modal').innerText()).includes('plant out'), 'planting calendar lists planting windows');
await btn('Close').click();
await page.locator('.tabs .tab', { hasText: /^Plants$/ }).click();

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
check(await page.locator('#sgp-svg g.shade rect').count() > 0, 'shade overlay drawn');
check(await page.locator('#sgp-svg g.shade rect[data-band="FULL_SHADE"], #sgp-svg g.shade rect[data-band="PART_SHADE"]').count() > 0, 'tree casts part shade or shade');
check(await page.locator('#sgp-svg g.shade rect[data-band="FULL_SUN"]').count() > 0, 'open ground shows as full sun');
check(await page.locator('#sgp-legend:not(.hidden)').count() === 1, 'shade legend shown');
await page.emulateMedia({ colorScheme: 'dark' });
await page.screenshot({ path: path.join(shots, 'sgp-web-shade-dark.png') });
await page.emulateMedia({ colorScheme: 'light' });
await page.screenshot({ path: path.join(shots, 'sgp-web-shade-light.png') });

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

// Plan again: the list comes back (remembered), "Change selections" returns to it, "Discard" changes nothing.
d = await draft();
const plantsBefore = d.plots[0].plants.length;
await page.locator('nav.tools').getByRole('button', { name: 'Plan an area for me' }).click();
await drag(4.3, 0.3, 7.7, 1.9);
await page.locator('.modal').waitFor();
check(await page.locator('.plan-row').count() === rows, `plan list remembered (${await page.locator('.plan-row').count()} rows)`);
check(await page.locator('.modal').getByRole('button', { name: 'Clumps (recommended)' }).count() === 1, 'clumps / rows choice offered');
await page.screenshot({ path: path.join(shots, 'sgp-web-plan-dialog.png') });
await page.locator('.plan-row input.count').first().fill('2');
await btn('Plan it').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
check((await page.locator('#sgp-preview').innerText()).includes('clump'), 'proposal explains clumps');
await page.screenshot({ path: path.join(shots, 'sgp-web-proposal.png') });
await btn('Change selections').click();
await page.locator('.modal').waitFor();
check(await page.locator('.plan-row input.count').first().inputValue() === '2', 'change selections keeps the edited list');
await btn('Plan it').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
await btn('Discard').click();
d = await draft();
check(d.plots[0].plants.length === plantsBefore, 'discard leaves the plot unchanged');
check((await page.locator('.status').innerText()).includes('Nothing on the plot changed'), 'discard says nothing changed');

// Variety details: search by everyday words, names on the layout.
await page.locator('.tabs .tab', { hasText: /^Plants$/ }).click();
await page.getByPlaceholder(/Search .* varieties/).fill('sweet bell');
check((await page.locator('.panel-body').innerText()).includes('California Wonder'), 'search "sweet bell" finds bell peppers');
await page.getByPlaceholder(/Search .* varieties/).fill('spring onion');
check(await page.locator('button.seed').count() === 3, 'three spring onions in the catalog');
await page.getByPlaceholder(/Search .* varieties/).fill('cherry tomato');
check((await page.locator('.panel-body').innerText()).includes('Sweet 100'), 'search "cherry tomato" finds cherry tomatoes');
check((await page.locator('button.seed .kind').allInnerTexts()).some(t => t.includes('Cherry tomato')), 'variety kind shown in the list');
check(await page.locator('#sgp-svg .plant-label').count() > 0, 'plant names shown on the layout');
await page.getByPlaceholder(/Search .* varieties/).fill('');

// Seasons: close this season, history kept and shown, undo works, rotation note when planting in the same spot.
await page.locator('.tabs .tab', { hasText: /^Plot$/ }).click();
await btn('Start a new season (empty)…').click();
await page.locator('.modal').waitFor();
const closing = await page.locator('.modal input').inputValue();
await btn('Start new season').click();
d = await draft();
check(d.plots[0].plants.length === 0 && d.plots[0].history.length === plantsBefore, `season ${closing} closed: ${d.plots[0].history.length} plants in history`);
check(d.plots[0].siteFeatures.length === 1, 'tree kept for next season');
check(await page.locator('#sgp-svg g.history circle').count() === plantsBefore, 'past season shown faded on the layout');
await page.screenshot({ path: path.join(shots, 'sgp-web-seasons.png') });
check((await page.locator('.panel-body').innerText()).includes('Seasons & crop rotation'), 'seasons section shown');
check((await page.locator('.panel-body').innerText()).includes('nightshades'), 'rotation advice names last year\'s families');
await page.locator('.tabs .tab', { hasText: /^Plants$/ }).click();
await page.getByPlaceholder(/Search .* varieties/).fill('Brandywine');
await page.locator('button.seed').first().click();
await clickMetres(4, 4);
check((await page.locator('.status').innerText()).includes('Rotation note'), 'rotation note when a tomato goes where tomatoes grew');
await page.keyboard.press('Control+z');
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].plants.length === plantsBefore && (d.plots[0].history || []).length === 0, 'undo reopens the closed season');
await page.keyboard.press('Control+y');
d = await draft();
check(d.plots[0].history.length === plantsBefore, 'redo closes it again');
let historyCount = d.plots[0].history.length;

// Organised clumps, legend + find, plant editor, outline editing, compass.
check(await page.locator('#sgp-svg g.compass polygon').count() === 8, 'compass has four arrowheads');
await page.locator('nav.tools').getByRole('button', { name: 'Plan an area for me' }).click();
await drag(3.3, 0.3, 7.9, 4.9);
await page.locator('.modal').waitFor();
await page.locator('.plan-row .grow input').first().fill('Sweet Corn - Silver Queen');
await page.locator('.plan-row .grow input').first().dispatchEvent('change');
await page.locator('.plan-row input.count').first().fill('20');
await page.locator('.plan-row input.count').first().dispatchEvent('input');
await page.locator('.plan-row .star').first().click();
const checksText = await page.locator('.plan-checks').innerText();
check(checksText.includes('Checks before planning') && checksText.includes('Space:'), 'checks shown before planning');
check(checksText.includes('Most important: Sweet Corn'), 'starred plant listed as most important');
check(checksText.includes('raccoons go for Sweet Corn'), 'checks warn about the yard\'s pests');
const arrange = page.locator('.plan-shape select').first();
const arrangeOpts = await arrange.locator('option').allInnerTexts();
check(arrangeOpts.some(t => t === '2 rows of 10') && arrangeOpts.some(t => t === '5 rows of 4'), `clump arrangements offered (${arrangeOpts.length})`);
await arrange.selectOption({ label: '4 rows of 5' });
await page.screenshot({ path: path.join(shots, 'sgp-web-checks.png') });
check(await page.locator('.modal button:not([title])').count() === 0, 'every button in the plan dialog has hover help');
await btn('Plan it').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
const proposal = await page.locator('#sgp-preview').innerText();
check(proposal.includes('Most important first: Sweet Corn'), 'proposal says the most important plants went first');
check(proposal.includes('4 rows of 5'), 'organized clump: 20 corn in 4 rows of 5');
check(proposal.includes('walkway'), 'proposal mentions walkways for watering');
// Several layouts to choose from, and a card that folds and moves.
check(proposal.includes('Option 1: Suggested') && proposal.includes('plants placed'), 'proposal shows option 1 with its summary');
await btn('Option ▶').click();
check((await page.locator('#sgp-preview').innerText()).includes('Option 2:'), 'Option ▶ shows another layout');
await btn('◀ Option').click();
check((await page.locator('#sgp-preview').innerText()).includes('Option 1:'), '◀ Option goes back');
const cardBox = await page.locator('#sgp-preview .pv-head').boundingBox();
await page.mouse.move(cardBox.x + 30, cardBox.y + 10); await page.mouse.down();
await page.mouse.move(cardBox.x - 300, cardBox.y - 200, { steps: 5 }); await page.mouse.up();
const cardMoved = await page.locator('#sgp-preview').boundingBox();
check(cardMoved.y < cardBox.y - 100, 'proposal card can be dragged out of the way');
await page.locator('#sgp-preview .pv-head').getByRole('button', { name: '−' }).click();
check(!(await page.locator('#sgp-preview').innerText()).includes('Keep this plan'), 'proposal card folds to its header');
await page.locator('#sgp-preview .pv-head').getByRole('button', { name: '+' }).click();
await btn('Keep this plan').click();
d = await draft();
check(d.plots[0].plants.length === 20, `corn planted (${d.plots[0].plants.length})`);
check(await page.locator('.plant-legend .pl-row').count() === 1, 'legend lists what is planted');
await page.locator('.plant-legend .pl-row').first().click();
check(await page.locator('#sgp-svg .find-ring').count() === 20, 'find circles every corn plant');
await page.keyboard.press('Escape');
check(await page.locator('#sgp-svg .find-ring').count() === 0, 'Esc clears find');
// A whole group: select it, rearrange its rows, undo.
{
  d = await draft();
  const c0 = d.plots[0].plants[0];
  await page.locator('nav.tools').getByRole('button', { name: 'Select / move' }).click();
  await clickMetres(c0.x, c0.y);
  await page.locator('nav.tools').getByRole('button', { name: 'Select its group' }).click();
  check((await page.locator('.status').innerText()).startsWith('20 plants selected'), 'Select its group selects the whole clump');
  await page.locator('nav.tools').getByRole('button', { name: 'Rearrange group…' }).click();
  await page.locator('.modal').waitFor();
  const shapes = await page.locator('.modal .planb').allInnerTexts();
  check(shapes.some(t => t.startsWith('2 rows of 10')) && shapes.some(t => t.startsWith('5 rows of 4')), `rearrange offers other rows and columns (${shapes.length})`);
  const fit = page.locator('.modal .planb', { hasText: '— fits' }).filter({ hasNotText: '4 rows of 5' }).first();
  const label = (await fit.innerText()).split(' — ')[0];
  await fit.click();
  d = await draft();
  const rowsNow = new Set(d.plots[0].plants.map(p => Math.round(p.y * 100))).size;
  check(d.plots[0].plants.length === 20 && (await page.locator('.status').innerText()).includes('Rearranged 20 plants as ' + label), `group rearranged as ${label} (${rowsNow} rows)`);
  await page.keyboard.press('Control+z');
  await page.keyboard.press('Escape');
}
// Replace all corn with another variety, then undo.
await page.locator('.plant-legend .pl-line').first().getByRole('button', { name: 'Replace…' }).click();
await page.locator('.modal').waitFor();
await page.locator('.modal input').first().fill('Sweet Corn - Golden Bantam');
await page.locator('.modal').getByRole('button', { name: /^Replace all/ }).click();
d = await draft();
check(d.plots[0].plants.every(p => p.variety === 'Sweet Corn - Golden Bantam'), 'replace all changes every corn plant at once');
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].plants.every(p => p.variety === 'Sweet Corn - Silver Queen'), 'one undo puts the old variety back');
// Planning over plants already there: keep them, or start from a blank area (replaced only when kept, one undo).
{
  const before = d.plots[0].plants.map(p => `${p.x.toFixed(2)},${p.y.toFixed(2)}`).sort().join(';');
  await page.locator('nav.tools').getByRole('button', { name: 'Plan an area for me' }).click();
  await drag(3.3, 0.3, 7.9, 4.9);
  await page.locator('.modal').waitFor();
  const keepText = await page.locator('.modal .keep-choice').innerText();
  check(keepText.includes('20 plants are already in this area') && keepText.includes('Start from a blank area'), 'plan dialog asks to keep or replace the plants there');
  await page.locator('.modal .keep-choice label', { hasText: 'Start from a blank area' }).locator('input').check();
  await btn('Plan it').click();
  await page.locator('#sgp-preview:not(.hidden)').waitFor();
  check((await page.locator('#sgp-preview').innerText()).includes('replaces the 20 plants already in this area'), 'proposal warns how many plants it replaces');
  d = await draft();
  check(d.plots[0].plants.length === 20, 'nothing is removed before the plan is kept');
  await btn('Keep this plan').click();
  d = await draft();
  check(d.plots[0].plants.length === 20 && d.plots[0].plants.every(p => p.variety === 'Sweet Corn - Silver Queen'), `blank area: new plan replaced the old plants (${d.plots[0].plants.length})`);
  await page.keyboard.press('Control+z');
  d = await draft();
  check(d.plots[0].plants.map(p => `${p.x.toFixed(2)},${p.y.toFixed(2)}`).sort().join(';') === before, 'one undo brings the replaced plants back');
}
// The "On this plot" box can be dragged out of the way.
const headBox = await page.locator('.plant-legend .pl-head').boundingBox();
await page.mouse.move(headBox.x + 40, headBox.y + 8); await page.mouse.down();
await page.mouse.move(headBox.x + 340, headBox.y - 300, { steps: 6 }); await page.mouse.up();
const moved = await page.locator('.plant-legend').boundingBox();
check(moved.y < headBox.y - 200 && await page.evaluate(() => !!localStorage.getItem('sgp.legendPos')), 'On this plot box dragged and its place remembered');
check(await page.locator('button:not([title])').count() === 0, 'every button has hover help');
await page.locator('.tabs .tab', { hasText: /^Food$/ }).click();
await page.locator('.panel-body .findable').first().click();
check((await page.locator('.status').innerText()).startsWith('Showing 20'), 'harvest line finds its plants');
await page.keyboard.press('Escape');
// Edit a planted plant: select it, change the variety.
await page.locator('nav.tools').getByRole('button', { name: 'Select / move' }).click();
const first = d.plots[0].plants[0];
await clickMetres(first.x, first.y);
await page.locator('nav.tools').getByRole('button', { name: 'Edit plant…' }).click();
await page.locator('.modal').waitFor();
await page.locator('.modal input').first().fill('Sweet Corn - Golden Bantam');
await page.locator('.modal').getByRole('button', { name: 'Save', exact: true }).click();
d = await draft();
check(d.plots[0].plants.some(p => p.variety === 'Sweet Corn - Golden Bantam'), 'plant variety changed');
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].plants.every(p => p.variety !== 'Sweet Corn - Golden Bantam'), 'undo reverts the variety change');
// Plan B: a plant died; replace it with a variety that catches up with the others.
await clickMetres(first.x, first.y);
await page.locator('nav.tools').getByRole('button', { name: 'Edit plant…' }).click();
await page.locator('.modal').waitFor();
await page.locator('.modal').getByRole('button', { name: 'Plan B…' }).click();
await page.waitForFunction(() => document.querySelector('.modal-title')?.textContent.startsWith('Plan B for'));
const planBText = await page.locator('.modal').innerText();
check(planBText.includes('should be ready around') && await page.locator('.modal .planb').count() > 0, `Plan B suggests replacements (${await page.locator('.modal .planb').count()})`);
check((await page.locator('.modal .planb').first().innerText()).includes('days:'), 'Plan B shows days to harvest and timing');
await page.locator('.modal').getByRole('button', { name: 'Plant Plan B' }).click();
d = await draft();
check(d.plots[0].plants.filter(p => p.variety !== first.variety).length === 1, 'Plan B replaced the lost plant');
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].plants.every(p => p.variety === first.variety), 'undo reverts Plan B');
// Outline: draw, drag a corner, delete.
await page.locator('nav.tools').getByRole('button', { name: 'Plot outline' }).click();
for (const [x, y] of [[0.2, 0.2], [7.8, 0.2], [7.8, 4.8], [0.2, 4.8]]) await clickMetres(x, y);
await page.keyboard.press('Enter');
d = await draft();
check(d.plots[0].outline && d.plots[0].outline.length === 4, 'outline drawn');
await page.locator('nav.tools').getByRole('button', { name: 'Plot outline' }).click();
check(await page.locator('#sgp-svg .handle').count() === 4, 'outline corners have handles');
await drag(7.8, 4.8, 6.5, 4.5);
d = await draft();
check(Math.abs(d.plots[0].outline[2][0] - 6.5) < 0.1, `outline corner moved (${JSON.stringify(d.plots[0].outline[2])})`);
await page.locator('nav.tools').getByRole('button', { name: 'Delete outline' }).click();
d = await draft();
check(!d.plots[0].outline, 'outline deleted');
await page.keyboard.press('Control+z');
d = await draft();
check(d.plots[0].outline && d.plots[0].outline.length === 4, 'undo restores the outline');
await page.screenshot({ path: path.join(shots, 'sgp-web-legend.png') });
await page.locator('nav.tools').getByRole('button', { name: 'Select / move' }).click();

// Seasons: look back at a past season (read only), plan next season with rotation, a 5-season rotation plan.
await page.locator('.tabs .tab', { hasText: /^Plot$/ }).click();
const seasonSelect = page.locator('.panel-body label.field', { hasText: 'Season shown on the layout' }).locator('select');
const pastYear = await seasonSelect.locator('option').nth(1).getAttribute('value');
await seasonSelect.selectOption(pastYear);
d = await draft();
check(await page.locator('#sgp-svg g.plant').count() === d.plots[0].history.filter(h => String(h.season) === pastYear).length, `season ${pastYear} shown read-only`);
await clickMetres(1.5, 4.5);
check((await page.locator('.status').innerText()).includes('read only'), 'past season cannot be edited');
await page.locator('.legend').getByRole('button', { name: /^Back to/ }).click();
d = await draft();
const cornBefore = d.plots[0].plants.map(p => [p.x, p.y]);
const historyBefore = (d.plots[0].history || []).length;
await btn('Plan next season (rotate)…').click();
await page.locator('.modal').waitFor();
check((await page.locator('.modal-title').innerText()).startsWith('Plan next season'), 'plan next season dialog');
check(await page.locator('.plan-row input.count').first().inputValue() === '20', 'next season starts from this season\'s list');
await btn('Plan it').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
await btn('Start next season with this plan').click();
d = await draft();
check(d.plots[0].history.length === historyBefore + 20 && d.plots[0].plants.length === 20, 'this season archived, next season planted');
const tooClose = d.plots[0].plants.filter(p => cornBefore.some(([x, y]) => Math.hypot(p.x - x, p.y - y) < 0.69));
check(tooClose.length === 0, `no corn where corn grew last season (${tooClose.length} too close)`);
await btn('Rotation plan for several seasons…').click();
await page.locator('.modal').waitFor();
await page.locator('.modal input').first().fill('12');
await btn('Make the plan').click();
await page.locator('#sgp-preview:not(.hidden)').waitFor();
check((await page.locator('#sgp-preview h3').innerText()).includes('(1 of 12)'), 'rotation plan for 12 seasons');
await btn('Year ▶').click();
check((await page.locator('#sgp-preview h3').innerText()).includes('(2 of 12)'), 'rotation plan steps to year 2');
await btn('Change a variety…').click();
await page.locator('.modal').waitFor();
await page.locator('.modal input').first().fill('Sweet Corn - Golden Bantam');
await page.locator('.modal').getByRole('button', { name: 'Change', exact: true }).click();
check((await page.locator('#sgp-preview h3').innerText()).includes('(2 of 12)'), 'variety change keeps the year shown');
check((await page.locator('.status').innerText()).includes('→ Sweet Corn - Golden Bantam'), 'variety swapped from that year on');
await page.screenshot({ path: path.join(shots, 'sgp-web-rotation.png') });
await btn('Close').click();
d = await draft();
check(d.plots[0].plants.length === 20, 'looking at the rotation plan changes nothing');
historyCount = d.plots[0].history.length;

// Fill the whole plot: How many fit?
await page.locator('.tabs .tab', { hasText: /^Plants$/ }).click();
await btn('Fill the whole plot…').click();
await page.locator('.modal').waitFor();
await btn('How many fit?').click();
check((await page.locator('.status').innerText()).startsWith('About'), 'how many fit estimates the numbers');
await page.locator('.modal').getByRole('button', { name: 'Cancel', exact: true }).click();

// Shade at a time of day, with plants casting shade.
if (!(await page.getByRole('button', { name: 'Shade: on' }).count())) await page.getByRole('button', { name: /Shade:/ }).click();
await page.locator('.legend select[aria-label="Shade mode"]').selectOption('time');
check(await page.locator('.legend input[type=range]').count() === 1, 'time-of-day slider shown');
check(await page.locator('#sgp-svg g.shade rect[data-shadow]').count() > 0, 'shade at the chosen time drawn');
await page.locator('.legend select[aria-label="Shade mode"]').selectOption('day');
await page.getByRole('button', { name: 'Shade: on' }).click();

// Irrigation: a sprinkler and the water map.
await page.locator('.tabs .tab', { hasText: /^Plot$/ }).click();
await page.locator('.panel-body').getByRole('button', { name: 'Sprinkler', exact: true }).click();
await clickMetres(7.5, 1);
await page.locator('.modal').waitFor();
await btn('Add').click();
d = await draft();
check(d.plots[0].siteFeatures.some(f => f.type === 'SPRINKLER'), 'sprinkler added');
// Drip line: click points, Finish, and the tool stops adding points.
await page.locator('.panel-body').getByRole('button', { name: 'Drip line / soaker hose', exact: true }).click();
await clickMetres(1, 4.6); await clickMetres(3, 4.6); await clickMetres(5, 4.6);
check(await page.locator('nav.tools').getByRole('button', { name: 'Finish (Enter)' }).isVisible(), 'drip line shows a Finish button');
await page.locator('nav.tools').getByRole('button', { name: 'Finish (Enter)' }).click();
await page.locator('.modal').waitFor();
await btn('Add').click();
d = await draft();
check(d.plots[0].siteFeatures.some(f => f.type === 'DRIP_LINE' && f.points.length === 3), 'drip line added with its 3 points');
check(await page.locator('nav.tools .tool.on').first().innerText() === 'Select / move', 'after Finish the tool goes back to Select / move');
await page.getByRole('button', { name: 'Water: off' }).click();
check(await page.locator('#sgp-svg g.water rect[data-water="SPRINKLER"]').count() > 0, 'water map shows the sprinkler\'s wet area');
check(await page.locator('#sgp-svg .dry-ring').count() > 0, 'plants out of reach are circled');
await page.screenshot({ path: path.join(shots, 'sgp-web-water.png') });
await page.getByRole('button', { name: 'Water: on' }).click();
await page.locator('nav.tools').getByRole('button', { name: 'Select / move' }).click();

// Care: pests in the yard and watering advice.
await page.locator('.tabs .tab', { hasText: /^Care$/ }).click();
const care = await page.locator('.panel-body').innerText();
check(care.includes('Pests and animals in your yard') && care.includes('2.4 m (8 ft)'), 'Care shows deer fencing advice');
check(care.includes('Raccoons — goes for your Sweet Corn'), 'Care names the plants raccoons go for');
check(care.includes('Watering and irrigation') && care.includes('Water map'), 'Care shows watering with the irrigation tools');
check(care.includes('A planning aid only'), 'Care repeats the short disclaimer');
await page.locator('.panel-body').getByRole('button', { name: 'Rabbits' }).click();
d = await draft();
check(d.plots[0].pests.includes('RABBIT'), 'pests can be changed on the Care tab');
await page.screenshot({ path: path.join(shots, 'sgp-web-care.png'), fullPage: true });

// Satellite photo: add a picture, set its scale from two points, move it.
const png = await page.evaluate(() => { const c = document.createElement('canvas'); c.width = 400; c.height = 300; const g = c.getContext('2d'); g.fillStyle = '#4d7c0f'; g.fillRect(0, 0, 400, 300); g.fillStyle = '#78716c'; g.fillRect(40, 40, 120, 60); return c.toDataURL('image/png').split(',')[1]; });
const pngPath = path.join(shots, 'sgp-yard.png');
fs.writeFileSync(pngPath, Buffer.from(png, 'base64'));
await page.locator('.tabs .tab', { hasText: /^Plot$/ }).click();
check((await page.locator('.panel-body').innerText()).includes('Satellite photo'), 'Plot tab has a satellite photo section');
check(await btn('Open Google Maps (satellite)').isVisible(), 'button to open Google Maps in satellite view');
const [photoChooser] = await Promise.all([page.waitForEvent('filechooser'), btn('Add photo…').click()]);
await photoChooser.setFiles(pngPath);
await page.waitForFunction(() => (JSON.parse(localStorage.getItem('sgp.draft') || '{}').plots || [])[0]?.backdrop);
d = await draft();
check(d.plots[0].backdrop.image.startsWith('data:image/jpeg;base64,') && Math.abs(d.plots[0].backdrop.widthM - 8) < 0.01, 'photo added across the plot width');
check(await page.locator('#sgp-svg image.backdrop-photo').count() === 1, 'photo drawn under the plot');
await clickMetres(1, 1);
await clickMetres(3, 1);
await page.locator('.modal').waitFor();
await page.locator('.modal input').fill('4');
await btn('Set scale').click();
d = await draft();
check(Math.abs(d.plots[0].backdrop.widthM - 16) < 0.01 && Math.abs(d.plots[0].backdrop.x + 1) < 0.01, `scale set from two points (${d.plots[0].backdrop.widthM} m wide)`);
await drag(2, 2, 3, 2.5);
d = await draft();
check(Math.abs(d.plots[0].backdrop.x - 0) < 0.05 && Math.abs(d.plots[0].backdrop.y + 0.5) < 0.05, `photo moved by dragging (${d.plots[0].backdrop.x}, ${d.plots[0].backdrop.y})`);
const turnBox = page.locator('.panel-body input[type=number]').last();
await turnBox.fill('-30'); await turnBox.dispatchEvent('change');
d = await draft();
check(Math.abs(d.plots[0].backdrop.rotationDeg + 30) < 0.01 && (await page.locator('.panel-body').innerText()).includes('30° counterclockwise'), 'photo turned 30° counterclockwise from the number box');
await turnBox.fill('180'); await turnBox.dispatchEvent('change');
d = await draft();
check(Math.abs(d.plots[0].backdrop.rotationDeg - 180) < 0.01 && await page.locator('.panel-body input[type=range]').last().inputValue() === '180', 'photo turns to 180° and the slider follows');
check(await btn('Open in Google Maps').isVisible(), 'Open in Google Maps button on the Plot tab');
await page.screenshot({ path: path.join(shots, 'sgp-web-photo.png') });
await page.locator('nav.tools').getByRole('button', { name: 'Select / move' }).click();

// Duplicate the plot (with its history), then delete the copy.
await page.locator('.tabs .tab', { hasText: /^Plot$/ }).click();
check(await btn('Duplicate…').isVisible(), 'Duplicate… button at the top of the Plot tab');
await btn('Duplicate plot…').click();
await page.locator('.modal').waitFor();
await btn('Duplicate').click();
d = await draft();
check(d.plots.length === 2 && d.plots[1].history.length === historyCount && d.plots[1].name.endsWith('(copy)'), 'duplicate carries the site and history');
check(d.plots[1].backdrop && d.plots[1].pests.includes('DEER'), 'duplicate carries the photo and pests');
await btn('Delete plot').click();
await page.locator('.modal').getByRole('button', { name: 'Delete', exact: true }).click();
d = await draft();
check(d.plots.length === 1, 'copy deleted, original kept');

// Tabs render without errors.
for (const t of ['Plot', 'Harmony', 'Care', 'Food', 'Plants']) {
  await page.locator('.tabs .tab', { hasText: new RegExp('^' + t + '$') }).click();
  check(await page.locator('.panel-body').innerText() !== '', `${t} tab renders`);
}
await page.screenshot({ path: path.join(shots, 'sgp-web.png') });

// Interface language: Spanish from the dictionary, then back to English.
await page.locator('select[aria-label="Language"], select[aria-label="Idioma"]').first().selectOption('es');
check(await page.getByRole('button', { name: 'Nueva parcela' }).count() === 1 && await page.locator('.tabs .tab', { hasText: /^Parcela$/ }).count() === 1, 'interface switches to Spanish from the dictionary');
check((await page.locator('button[title]').first().getAttribute('title')) !== null, 'hover help still present in Spanish');
await page.locator('select[aria-label="Language"], select[aria-label="Idioma"]').first().selectOption('en');
check(await btn('New plot').count() === 1, 'and back to English');

// Save (download) and check the file.
const [download] = await Promise.all([page.waitForEvent('download'), btn('Save').click()]);
const saved = path.join(shots, 'sgp-smoke.sgp.json');
await download.saveAs(saved);
const json = JSON.parse(fs.readFileSync(saved, 'utf8'));
check(json.format === 'smart-garden-plan' && json.version === 1, 'saved file has the plan-file header');
check(json.plots.length === 1 && json.plots[0].history.length === historyCount, 'saved file has the season history');
check(json.plots[0].backdrop?.image?.startsWith('data:image/') && json.plots[0].pests.length === 3, 'saved file has the satellite photo and pests');

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
check(d.plots[0].history.length === historyCount, 're-opened file has the same history');

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

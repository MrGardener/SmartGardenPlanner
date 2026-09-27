#!/usr/bin/env python3
"""Builds the single portable HTML file: page styles, the compiled planner (Kotlin/JS) and the data tables
(seed catalog, ZIP -> zone, ZIP -> location), all inline so the file works offline from any folder.

usage: bundle.py <compiled sgp.mjs> <output .html>
"""
import html
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(ROOT, 'Android App', 'app', 'src', 'main', 'assets')

CSS = r"""
:root{--bg:#eef1ec;--panel:#ffffff;--card:#f3f4f6;--line:#cbd5e1;--text:#1f2937;--muted:#5b6474;--accent:#059669;--accent2:#10b981;--on-bg:#d1fae5;--on-text:#064e3b;--input:#ffffff;--soft:#f8fafc;--rule:#e5e7eb;--stage:#e4e7e0;--warn:#b45309;--danger:#b91c1c;--danger-text:#991b1b;--badge-bg:#e0e7ff;--badge-text:#3730a3;--sel:#f97316;color-scheme:light}
@media (prefers-color-scheme: dark){:root:not([data-theme="light"]){--bg:#0f172a;--panel:#111827;--card:#1f2937;--line:#334155;--text:#e5e7eb;--muted:#94a3b8;--accent:#10b981;--accent2:#059669;--on-bg:#064e3b;--on-text:#ecfdf5;--input:#0b1220;--soft:#0b1220;--rule:#1f2937;--stage:#0b1220;--warn:#f59e0b;--danger:#ef4444;--danger-text:#fecaca;--badge-bg:#312e81;--badge-text:#c7d2fe;color-scheme:dark}}
:root[data-theme="dark"]{--bg:#0f172a;--panel:#111827;--card:#1f2937;--line:#334155;--text:#e5e7eb;--muted:#94a3b8;--accent:#10b981;--accent2:#059669;--on-bg:#064e3b;--on-text:#ecfdf5;--input:#0b1220;--soft:#0b1220;--rule:#1f2937;--stage:#0b1220;--warn:#f59e0b;--danger:#ef4444;--danger-text:#fecaca;--badge-bg:#312e81;--badge-text:#c7d2fe;color-scheme:dark}
*{box-sizing:border-box}
html,body{margin:0;height:100%;background:var(--bg);color:var(--text);font:14px/1.4 system-ui,-apple-system,"Segoe UI",Roboto,Ubuntu,sans-serif}
#app{display:flex;flex-direction:column;height:100vh;height:100dvh}
.top{display:flex;align-items:center;gap:12px;padding:6px 12px;background:var(--panel);border-bottom:1px solid var(--line);flex-wrap:wrap}
.brand{display:flex;gap:6px;align-items:center;font-weight:700;font-size:16px;white-space:nowrap}
.actions{display:flex;gap:6px;align-items:center;flex-wrap:wrap;flex:1}
.file{color:var(--muted);margin-left:auto;font-size:12px;max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.main{flex:1;display:grid;grid-template-columns:150px 1fr 360px;min-height:0}
.tools{display:flex;flex-direction:column;gap:4px;padding:8px;background:var(--panel);border-right:1px solid var(--line);overflow:auto}
.tools .sep{height:8px}
.stage{position:relative;min-width:0;min-height:0;background:var(--stage)}
.canvas{position:absolute;inset:0}
#sgp-svg{width:100%;height:100%;display:block;color:var(--muted);touch-action:none;user-select:none;cursor:crosshair}
.panel{background:var(--panel);border-left:1px solid var(--line);display:flex;flex-direction:column;min-height:0}
.tabs{display:flex;border-bottom:1px solid var(--line)}
.tab{flex:1;background:none;border:0;border-bottom:2px solid transparent;color:var(--muted);padding:10px 4px;cursor:pointer;font:inherit}
.tab.on{color:var(--text);border-bottom-color:var(--accent)}
.panel-body{padding:10px 12px;overflow:auto;display:flex;flex-direction:column;gap:6px}
.status{padding:6px 12px;background:var(--panel);border-top:1px solid var(--line);color:var(--muted);min-height:30px}
.btn,.tool,.chip{background:var(--card);color:var(--text);border:1px solid var(--line);border-radius:6px;padding:6px 10px;cursor:pointer;font:inherit;text-align:left}
.btn:hover,.tool:hover,.chip:hover{border-color:var(--muted)}
.btn:disabled{opacity:.4;cursor:default}
.btn.primary,.tool.primary{background:var(--accent);border-color:var(--accent);color:#fff}
.btn.on,.tool.on,.chip.on{border-color:var(--accent);box-shadow:inset 0 0 0 1px var(--accent);background:var(--on-bg);color:var(--on-text)}
.btn.danger,.tool.danger{border-color:var(--danger);color:var(--danger-text)}
.btn.icon,.tool.icon{min-width:34px;text-align:center}
.btn.small{padding:2px 8px;font-size:12px}
.tool{font-size:13px}
.inp{background:var(--input);color:var(--text);border:1px solid var(--line);border-radius:6px;padding:6px 8px;font:inherit;width:100%}
select.inp{width:auto;max-width:100%}
.actions select.inp{max-width:200px}
.field{display:flex;flex-direction:column;gap:3px;margin:4px 0}
.lbl{color:var(--muted);font-size:12px}
.check{display:flex;gap:8px;align-items:center;margin:2px 0}
.row{display:flex;gap:6px;align-items:center}
.row.wrap{flex-wrap:wrap}
.grid2{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.grid3{display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}
.chips{display:flex;gap:4px;flex-wrap:wrap}
.chip{padding:4px 10px;min-width:44px;text-align:center}
.h{margin:10px 0 2px;font-size:14px;color:var(--text)}
.p{margin:2px 0}
.hint{color:var(--muted);font-size:12px;margin:1px 0;display:block}
.warn{color:var(--warn);font-size:13px;margin:4px 0}
.ok{color:var(--accent);margin:4px 0}
.kv{display:flex;justify-content:space-between;gap:10px;border-bottom:1px dashed var(--rule);padding:2px 0}
.kv .k{color:var(--muted)}
.list-item{display:flex;gap:8px;align-items:center;padding:4px 0;border-bottom:1px solid var(--rule)}
.list-item.col{flex-direction:column;align-items:flex-start}
.grow{flex:1;min-width:0;display:flex;flex-direction:column}
.seed{display:flex;gap:8px;align-items:flex-start;width:100%;background:none;border:1px solid transparent;border-radius:6px;padding:5px 6px;color:var(--text);cursor:pointer;font:inherit;text-align:left}
.seed:hover{background:var(--card)}
.seed.on{border-color:var(--accent);background:var(--on-bg)}
.seed.dim{opacity:.55}
.seed .name{font-weight:600}
.kind{display:block;font-size:12px;color:var(--accent);font-weight:600}
.dot{width:12px;height:12px;border-radius:50%;flex:none;margin-top:4px}
.badge{display:inline-block;background:var(--badge-bg);color:var(--badge-text);border-radius:10px;padding:0 8px;font-size:11px;margin-top:2px;width:max-content}
.active{display:flex;gap:8px;background:var(--card);border:1px solid var(--accent);border-radius:8px;padding:8px}
.issue{padding:4px 6px;border-left:3px solid var(--muted);background:var(--soft);margin:2px 0}
.issue.high{border-color:var(--danger)}.issue.medium{border-color:var(--warn)}
details.pest,details.soil{background:var(--soft);border:1px solid var(--line);border-radius:6px;padding:4px 8px;margin:2px 0}
summary{cursor:pointer}
.preview{position:absolute;right:12px;bottom:12px;max-width:420px;max-height:60%;overflow:auto;background:var(--panel);border:1px solid var(--accent);border-radius:10px;padding:10px 12px;box-shadow:0 10px 30px rgba(0,0,0,.5)}
.hidden{display:none}
.legend{position:absolute;left:10px;top:10px;display:flex;gap:10px;align-items:center;flex-wrap:wrap;background:rgba(255,255,255,.92);color:#292524;border:1px solid #d6d3d1;border-radius:8px;padding:5px 10px;font-size:12px;max-width:calc(100% - 20px)}
.legend b{margin-right:2px}
.legend .sw{display:inline-block;width:14px;height:14px;border:1px solid #57534e;vertical-align:-3px;margin-right:4px}
.backdrop{position:fixed;inset:0;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;z-index:10;padding:16px}
.modal{background:var(--panel);border:1px solid var(--line);border-radius:12px;width:min(480px,100%);max-height:100%;display:flex;flex-direction:column}
.modal.wide{width:min(680px,100%)}
.modal-title{margin:0;padding:14px 16px 6px;font-size:18px}
.modal-body{padding:4px 16px;overflow:auto;display:flex;flex-direction:column;gap:4px}
.modal-actions{display:flex;justify-content:flex-end;gap:8px;padding:12px 16px}
.plan-rows{display:flex;flex-direction:column;gap:6px;margin-top:6px}
.plan-row{display:flex;gap:6px;align-items:flex-start}
.plan-row .count{width:80px}
.months{display:grid;grid-template-columns:repeat(6,1fr);gap:4px}
.month{display:flex;gap:4px;align-items:center;font-size:12px}
@media (max-width:900px){
 .main{grid-template-columns:1fr;grid-template-rows:auto minmax(300px,1fr) auto}
 .tools{flex-direction:row;flex-wrap:wrap;border-right:0;border-bottom:1px solid var(--line)}
 .panel{border-left:0;border-top:1px solid var(--line);max-height:45vh}
 .file{display:none}
 .top{gap:6px;padding:6px 8px}
 .brand{width:100%}
 .actions .btn{padding:5px 8px}
}
"""


def data_block(block_id: str, path: str) -> str:
    text = open(path, encoding='utf-8').read()
    # Plain text inside <script type="text/plain">: only "</script" would end the block early.
    if '</script' in text.lower():
        raise SystemExit(f'{path} contains "</script"')
    return f'<script type="text/plain" id="{block_id}">\n{text}\n</script>\n'


def main() -> None:
    js_path, out_path = sys.argv[1], sys.argv[2]
    js = open(js_path, encoding='utf-8').read().replace('</script', '<\\/script')
    version = os.environ.get('SGP_WEB_VERSION', '')
    parts = [
        '<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n',
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n',
        '<meta name="color-scheme" content="light dark">\n',
        '<title>Smart Garden Planner</title>\n',
        '<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22%3E%3Ctext y=%22.9em%22 font-size=%2290%22%3E%F0%9F%8C%B1%3C/text%3E%3C/svg%3E">\n',
        f'<!-- Smart Garden Planner (portable). One file, no install, works offline. {html.escape(version)} -->\n',
        '<style>', CSS, '</style>\n</head>\n<body>\n',
        '<div id="app"><p style="padding:20px">Loading Smart Garden Planner… (this page needs JavaScript and a browser from 2020 or newer)</p></div>\n',
        data_block('sgp-catalog', os.path.join(ASSETS, 'seed_catalog_pro.txt')),
        data_block('sgp-zipzones', os.path.join(ASSETS, 'zip_zones.txt')),
        data_block('sgp-ziplocs', os.path.join(ASSETS, 'zip_locations.txt')),
        '<script type="module">\n', js, '\n</script>\n</body>\n</html>\n',
    ]
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(''.join(parts))
    print(f'wrote {out_path} ({os.path.getsize(out_path) // 1024} KB)')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Neon v0.3.6 design boards: bottom nav (对话 速览 空间 我的). Renders HTML -> PNG via playwright."""
import re, os, pathlib, asyncio
from playwright.async_api import async_playwright

ROOT = pathlib.Path(__file__).parent
LUC = pathlib.Path(os.environ.get('LUCIDE_DIR', ROOT / 'lucide'))  # lucide-static 0.460.0 icons/*.svg
FONT = (ROOT / '../../../../android/app/src/main/res/font').resolve()
OUT = ROOT / 'out'; OUT.mkdir(exist_ok=True)

_ic = {}
def ic(name, size=20, sw=1.75, cls=''):
    if name not in _ic:
        s = (LUC / f'{name}.svg').read_text()
        s = re.sub(r'<!--.*?-->', '', s, flags=re.S)
        inner = re.search(r'<svg[^>]*>(.*)</svg>', s, re.S).group(1)
        _ic[name] = inner
    return (f'<svg class="ic {cls}" width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" '
            f'stroke="currentColor" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round">{_ic[name]}</svg>')

CSS = f"""
@font-face {{ font-family: GS; src: url(file://{FONT}/googlesans_regular.ttf); font-weight: 400; }}
@font-face {{ font-family: GS; src: url(file://{FONT}/googlesans_medium.ttf); font-weight: 500 700; }}
@font-face {{ font-family: SC; src: url(file://{FONT}/notosanssc_regular.otf); font-weight: 400; }}
@font-face {{ font-family: SC; src: url(file://{FONT}/notosanssc_medium.otf); font-weight: 500 700; }}
@font-face {{ font-family: SC; src: url(file://{FONT}/notosanssc_light.otf); font-weight: 300; }}
@font-face {{ font-family: GSC; src: url(file://{FONT}/googlesanscode_regular.ttf); }}
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
body {{ background: transparent; font-family: GS, SC, sans-serif; -webkit-font-smoothing: antialiased; }}
.dark {{ --bg:#0B0B0C; --sf:#161616; --sub:#1E1E20; --tile:#222224; --hair:#2E2E31; --card:#232325;
  --ink:#F2F1EC; --ink2:#8A8A8E; --ink3:#5E5E63; --ac:#DFF84B; --onac:#0B0B0C; --chip:#2B2D1C; --onchip:#DFF84B;
  --chipb:#4A5222; --live:#3FBF6F; --danger:#FF5C5C; --warn:#F08A24; --glass:rgba(24,24,26,.72); --glow:rgba(223,248,75,.38);
  --bubble:#1F1F21; --term:#101011; --deck0:#1B1B1E; --deck1:#232327; --deck2:#2C2C31; --fill:color-mix(in srgb,#DFF84B 38%,#2B2D1C); }}
.light {{ --bg:#F4F3EE; --sf:#FFFFFF; --sub:#ECEAE3; --tile:#F4F3EE; --hair:#D6D3CA; --card:#E4E2DB;
  --ink:#101113; --ink2:#6B6F76; --ink3:#A3A5AA; --ac:#101113; --onac:#FFFFFF; --chip:#E4EEFF; --onchip:#1B5FD9;
  --chipb:#C3D6FA; --live:#2E9E5B; --danger:#E5484D; --warn:#F08A24; --glass:rgba(255,255,255,.74); --glow:rgba(16,17,19,.22);
  --bubble:#E9E7E0; --term:#141415; --deck0:#E9E7E0; --deck1:#F3F2ED; --deck2:#FFFFFF; --fill:#C3D6FA; }}
.phone {{ width:390px; height:844px; background:var(--bg); color:var(--ink); position:relative; overflow:hidden; }}
.sb {{ height:48px; display:flex; align-items:center; justify-content:space-between; padding:0 26px 0 30px; font-weight:500; font-size:15px; }}
.sb .isl {{ width:118px; height:34px; border-radius:20px; background:#000; }}
.sb .r {{ display:flex; gap:6px; align-items:center; }}
.bat {{ width:24px; height:12px; border:1.5px solid var(--ink); border-radius:4px; position:relative; opacity:.9 }}
.bat:after {{ content:''; position:absolute; left:1.5px; top:1.5px; bottom:1.5px; width:15px; background:var(--ink); border-radius:1.5px }}
.bars {{ display:flex; gap:2px; align-items:flex-end; }} .bars i {{ width:3px; background:var(--ink); border-radius:1px; display:block }}
.ic {{ display:block; flex:none }}
.muted {{ color:var(--ink2) }} .dim {{ color:var(--ink3) }}
.mono {{ font-family: GSC, monospace }}
/* nav */
.nav {{ position:absolute; left:20px; right:20px; bottom:18px; height:66px; border-radius:33px; background:var(--glass);
  backdrop-filter: blur(22px); border:1px solid var(--hair); display:flex; align-items:center; padding:0 8px; gap:4px;
  box-shadow: 0 10px 30px rgba(0,0,0,.28); }}
.light .nav {{ box-shadow: 0 10px 30px rgba(59,53,35,.12); }}
.nav .t {{ height:50px; flex:1; border-radius:25px; display:flex; align-items:center; justify-content:center; color:var(--ink2); position:relative }}
.nav .t.on {{ flex:1.9; background:var(--ac); color:var(--onac); gap:8px; font-weight:500; font-size:15px;
  box-shadow: 0 0 26px var(--glow), inset 0 1px 0 rgba(255,255,255,.35); }}
.dark .nav .t.on {{ background: radial-gradient(120% 140% at 30% 20%, #F1FF8A 0%, #DFF84B 45%, #B9D62A 100%); }}
.badge {{ position:absolute; top:9px; right:calc(50% - 16px); min-width:16px; height:16px; border-radius:8px; background:var(--danger);
  color:#fff; font-size:10px; font-weight:700; display:flex; align-items:center; justify-content:center; padding:0 4px; border:2px solid var(--bg) }}
.dotlive {{ width:7px; height:7px; border-radius:50%; background:var(--live); box-shadow:0 0 8px var(--live); display:inline-block }}
/* avatar orb */
.orb {{ border-radius:50%; position:relative; background: radial-gradient(circle at 34% 30%, #FBFFD0 0%, #E6FA62 30%, #BEDB2C 70%, #8FA816 100%);
  box-shadow: 0 0 22px rgba(223,248,75,.35); flex:none }}
.orb:before, .orb:after {{ content:''; position:absolute; top:42%; width:12%; height:20%; background:#16180A; border-radius:50% }}
.orb:before {{ left:33% }} .orb:after {{ right:33% }}
.light .orb {{ box-shadow: 0 4px 16px rgba(143,168,22,.35) }}
.btnc {{ width:40px; height:40px; border-radius:20px; background:var(--sf); border:1px solid var(--hair); display:flex; align-items:center; justify-content:center; color:var(--ink) }}
.card {{ background:var(--sf); border:1px solid var(--card); border-radius:22px; }}
.chip {{ height:30px; padding:0 12px; border-radius:15px; display:inline-flex; align-items:center; gap:6px; font-size:13px; border:1px solid var(--hair); color:var(--ink) }}
.chip.on {{ background:var(--chip); color:var(--onchip); border-color:var(--chipb) }}
.h1 {{ font-size:30px; font-weight:500; letter-spacing:-.3px }}
.sec {{ font-size:13px; color:var(--ink2); margin:18px 4px 10px; display:flex; justify-content:space-between }}
.ring {{ border-radius:50%; display:flex; align-items:center; justify-content:center; position:relative }}
"""

def status_bar():
    return f"""<div class="sb"><span>09:41</span><div class="isl"></div><div class="r">
    <div class="bars"><i style="height:5px"></i><i style="height:7px"></i><i style="height:9px"></i><i style="height:11px"></i></div>
<svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.5 1 4.5a10 10 0 0 1 14 0z" fill="currentColor"/></svg>
    <div class="bat"></div></div></div>"""

TABS = [('对话', 'message-circle'), ('速览', 'layout-dashboard'), ('空间', 'boxes'), ('我的', 'circle-user-round')]
def nav(active, badge=None):
    out = []
    for i, (label, icon) in enumerate(TABS):
        if i == active:
            out.append(f'<div class="t on">{ic(icon, 21, 2)}<span>{label}</span></div>')
        else:
            b = f'<span class="badge">{badge}</span>' if (badge and i == 0) else ''
            out.append(f'<div class="t">{ic(icon, 23, 1.75)}{b}</div>')
    return f'<div class="nav">{"".join(out)}</div>'

# ---------------- 对话 ----------------
def chat():
    steps_done = [('读取 Gmail 本月账单', '4 封'), ('在 Alpine 沙箱里解析成表格', 'python3'), ('写入 Drive 的 Neon 文件夹', '1 个文件')]
    done_html = ''
    for n, (t, m) in enumerate(steps_done):
        op = [0.38, 0.55, 0.75][n]
        done_html += f'''<div style="display:flex;align-items:center;gap:10px;opacity:{op};height:26px;font-size:{13+n}px">
          <span style="width:18px;height:18px;border-radius:9px;background:var(--tile);display:flex;align-items:center;justify-content:center;color:var(--ac)">{ic('check',12,2.5)}</span>
          <span style="flex:1">{t}</span><span class="dim mono" style="font-size:11px">{m}</span></div>'''
    return f"""<div class="phone">{status_bar()}
    <div style="display:flex;align-items:center;justify-content:space-between;padding:6px 18px 0">
      <div class="btnc">{ic('message-square-text',19)}</div>
      <div style="display:flex;flex-direction:column;align-items:center;gap:3px">
        <div style="display:flex;align-items:center;gap:8px"><div class="orb" style="width:30px;height:30px"></div>
          <span style="font-size:18px;font-weight:500">Neon</span></div>
        <div style="font-size:12px" class="muted"><span class="dotlive"></span>&nbsp; 正在用 Hands 查物流</div>
      </div>
      <div class="btnc">{ic('square-pen',18)}</div>
    </div>
    <div style="padding:16px 18px 0">
      <div style="margin-left:56px;background:var(--bubble);border-radius:22px 22px 6px 22px;padding:12px 16px;font-size:15px;line-height:1.55">
        帮我把本月账单整理成表格存到 Drive，再查一下菜鸟快递到哪了</div>
      <div style="display:flex;align-items:center;gap:6px;margin:16px 2px 10px;font-size:13px" class="muted">{ic('sparkles',15)} 深度思考了 3.8 秒 {ic('chevron-down',14)}</div>
      <div class="card" style="padding:14px 14px 12px">
        {done_html}
        <div style="margin-top:8px;display:flex;align-items:center;gap:10px">
          <span style="width:22px;height:22px;border-radius:11px;border:2px solid var(--ac);border-top-color:transparent;display:block"></span>
          <span style="font-size:18px;font-weight:500">用 Hands 打开菜鸟查物流</span></div>
        <div style="margin:10px 0 2px 32px;height:34px;border-radius:17px;background:var(--tile);position:relative;overflow:hidden;display:flex;align-items:center;padding:0 14px;font-size:12.5px">
          <div style="position:absolute;inset:0;width:62%;background:linear-gradient(90deg,var(--chip),var(--fill));border-radius:17px"></div>
          <span style="position:relative;display:flex;gap:7px;align-items:center;color:var(--onchip)">{ic('hand',14,2)} 已点开「我的包裹」，正在读取轨迹</span>
          <span class="mono" style="position:relative;margin-left:auto;color:var(--onchip)">3/5</span></div>
        <div style="margin:10px 0 0 32px;font-size:13px" class="dim">接下来 整理回复</div>
      </div>
      <div class="card" style="margin-top:12px;padding:12px;display:flex;gap:12px;align-items:center">
        <div style="width:56px;height:56px;border-radius:14px;background:var(--tile);padding:8px;display:grid;grid-template-columns:repeat(3,1fr);gap:3px">
          {''.join('<i style="background:var(--hair);border-radius:2px;display:block"></i>' for _ in range(9))}</div>
        <div style="flex:1"><div style="font-size:15px;font-weight:500">账单 2026 年 11 月.csv</div>
          <div class="muted" style="font-size:12.5px;margin-top:3px">4 行，合计 ¥2,186.40，已存到 Drive</div></div>
        <div class="btnc" style="width:34px;height:34px">{ic('arrow-up-right',16)}</div>
      </div>
    </div>
    <div style="position:absolute;left:14px;right:14px;bottom:96px" class="card">
      <div style="padding:14px 16px 6px;font-size:15px" class="dim">接着说，或输入 / 调用技能</div>
      <div style="display:flex;align-items:center;gap:7px;padding:8px 10px 10px">
        <div class="btnc" style="width:34px;height:34px">{ic('plus',17)}</div>
        <span class="chip on" style="box-shadow:0 0 14px var(--glow)">{ic('sparkles',14,2)} 深度思考</span>
        <span class="chip">{ic('hand',14)} Hands</span>
        <div style="margin-left:auto" class="btnc" >{ic('mic',17)}</div>
        <div style="width:40px;height:40px;border-radius:20px;background:var(--ac);display:flex;align-items:center;justify-content:center"><i style="width:13px;height:13px;border-radius:3px;background:var(--onac);display:block"></i></div>
      </div>
    </div>
    {nav(0)}
    </div>"""

# ---------------- 速览 ----------------
def glance():
    def xpost(name, handle, t, text, why, stats, img=False, hue='#7A8CFF'):
        im = f'<div style="height:64px;border-radius:12px;margin-top:10px;background:radial-gradient(circle at 25% 30%,{hue},#1b1d2e 75%);position:relative"><span style="position:absolute;left:10px;bottom:8px;font-size:11px;color:#E8EAFF" class="mono">spring 0.72</span></div>' if img else ''
        return f"""<div class="card" style="padding:14px;margin-top:10px">
          <div style="display:flex;align-items:center;gap:10px">
            <div style="width:34px;height:34px;border-radius:17px;background:linear-gradient(135deg,{hue},#222)"></div>
            <div style="flex:1;line-height:1.25"><div style="font-size:14px;font-weight:500">{name}</div><div class="dim" style="font-size:12px">{handle}  {t}</div></div>
            <span style="width:26px;height:26px;border-radius:8px;background:var(--tile);display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px">𝕏</span></div>
          <div style="font-size:14.5px;line-height:1.6;margin-top:10px">{text}</div>{im}
          <div style="display:flex;align-items:center;gap:6px;margin-top:10px;font-size:12px;color:var(--onchip);background:var(--chip);border-radius:10px;padding:6px 10px">{ic('sparkles',13,2)}<span>{why}</span></div>
          <div style="display:flex;align-items:center;gap:16px;margin-top:10px;font-size:12px" class="muted">
            <span style="display:flex;gap:4px;align-items:center">{ic('message-circle',14)}{stats[0]}</span>
            <span style="display:flex;gap:4px;align-items:center">{ic('repeat',14)}{stats[1]}</span>
            <span style="display:flex;gap:4px;align-items:center">{ic('thumbs-up',14)}{stats[2]}</span>
            <span style="margin-left:auto;display:flex;gap:6px"><span class="chip" style="height:28px">{ic('bookmark',13)}</span><span class="chip" style="height:28px;background:var(--ac);color:var(--onac);border-color:var(--ac)">问 Neon</span></span></div></div>"""
    spark = ''.join(f'<i style="width:5px;height:{h}px;border-radius:2px;background:var(--ac);opacity:{.35+i*.05};display:block"></i>' for i, h in enumerate([6,8,7,10,12,11,16,20,26,34,30,40]))
    gh = f"""<div class="card" style="padding:14px;margin-top:10px">
        <div style="display:flex;align-items:center;gap:10px">
          <div style="width:34px;height:34px;border-radius:10px;background:var(--tile);display:flex;align-items:center;justify-content:center">{ic('git-branch',17)}</div>
          <div style="flex:1;line-height:1.25"><div style="font-size:14px;font-weight:500" class="mono">tinyhands/droid-agent</div><div class="dim" style="font-size:12px">GitHub 今日飙升</div></div>
          <div style="display:flex;align-items:flex-end;gap:2px;height:40px">{spark}</div></div>
        <div style="font-size:14px;line-height:1.6;margin-top:10px">在 Android 上跑完整 Linux 沙箱的 agent 运行时，proot 加 MCP，不用 root</div>
        <div style="display:flex;align-items:center;gap:14px;margin-top:10px;font-size:12.5px">
          <span style="display:flex;align-items:center;gap:5px"><i style="width:9px;height:9px;border-radius:5px;background:#A97BFF;display:block"></i>Kotlin</span>
          <span class="muted">★ 4.8k</span><span style="color:var(--live);font-weight:500">+1.2k 今天</span></div>
        <div style="display:flex;align-items:center;gap:6px;margin-top:10px;font-size:12px;color:var(--onchip);background:var(--chip);border-radius:10px;padding:6px 10px">{ic('sparkles',13,2)}<span>和 Neon 的沙箱方案几乎一样，可以对照它的 talloc 处理</span></div></div>"""
    return f"""<div class="phone">{status_bar()}
    <div style="padding:4px 20px 0;display:flex;justify-content:space-between;align-items:center">
      <div><div class="muted" style="font-size:13px">11 月 22 日 星期日</div><div class="h1" style="margin-top:2px">速览</div></div>
      <div style="display:flex;gap:8px"><div class="btnc">{ic('sliders-horizontal',17)}</div><div class="btnc">{ic('refresh-cw',17)}</div></div></div>
    <div style="display:flex;gap:8px;padding:12px 16px 0;overflow:hidden;white-space:nowrap">
      <span class="chip on">全部</span><span class="chip">信息差</span><span class="chip">X</span><span class="chip">GitHub</span><span class="chip dim" style="border-style:dashed">{ic('plus',13)} 兴趣</span></div>
    <div style="padding:12px 16px 0">
      <div class="card" style="padding:16px;position:relative;overflow:hidden;background:var(--term);border-color:var(--term);color:#F2F1EC">
        <div style="position:absolute;right:-50px;top:-60px;width:190px;height:190px;border-radius:50%;background:radial-gradient(circle,rgba(223,248,75,.35),transparent 70%)"></div>
        <div style="display:flex;align-items:center;gap:8px;font-size:12.5px;color:#DFF84B;position:relative">{ic('zap',14,2)} 信息差  3 条</div>
        <div style="font-size:18px;font-weight:500;margin-top:8px;line-height:1.45;position:relative">一个国产模型的 API 昨晚悄悄降价一半，官网公告还没发</div>
        <div style="font-size:13px;margin-top:6px;color:#9b9a96;position:relative">来自 3 条 X 帖子和它的定价页，Neon 已经核对过</div>
        <div style="display:flex;gap:5px;margin-top:12px;position:relative"><i style="width:18px;height:4px;border-radius:2px;background:#DFF84B;display:block"></i><i style="width:6px;height:4px;border-radius:2px;background:#444;display:block"></i><i style="width:6px;height:4px;border-radius:2px;background:#444;display:block"></i></div>
      </div>
      {xpost('Compose 动效笔记', '@motion_jetpack', '2 小时前', '把 Figma Riffs 里那个弹性 tab 用 Compose 复刻了，关键是 spring 的 dampingRatio 设 0.72，宽度和位移分开做动画', '你在做 Neon 的底部导航', ['86', '210', '1.9k'], img=True)}
      {gh}
    </div>
    {nav(1, badge=2)}
    </div>"""

# ---------------- 空间 ----------------
def space():
    orbit_icons = ['mail', 'calendar', 'globe', 'server', 'cloud', 'git-branch']
    import math
    orbit = ''
    for k, n in enumerate(orbit_icons):
        a = k / len(orbit_icons) * 2 * math.pi - math.pi / 2
        x = 50 + 36 * math.cos(a); y = 50 + 36 * math.sin(a)
        orbit += f'<div style="position:absolute;left:calc({x:.1f}% - 15px);top:calc({y:.1f}% - 15px);width:30px;height:30px;border-radius:15px;background:var(--tile);border:1px solid var(--hair);display:flex;align-items:center;justify-content:center">{ic(n,15)}</div>'
    deck = ''
    for k, (t, s) in enumerate([('周报生成', '/weekly'), ('查快递', '/track'), ('账单整理', '/bills')]):
        off = (2 - k)
        deck += f'''<div style="position:absolute;left:{off*8}px;right:{off*8}px;bottom:{off*16}px;height:62px;border-radius:14px;
          background:var(--deck{k});border:1px solid var(--hair);padding:10px 12px;box-shadow:0 -4px 12px rgba(0,0,0,.12)">
          {f'<div style="font-size:14px;font-weight:500">{t}</div><div class="mono dim" style="font-size:11px;margin-top:3px">{s}</div>' if k==2 else ''}</div>'''
    return f"""<div class="phone">{status_bar()}
    <div style="padding:4px 20px 0;display:flex;justify-content:space-between;align-items:center">
      <div class="h1">空间</div><div style="display:flex;gap:8px"><div class="btnc">{ic('search',18)}</div><div class="btnc">{ic('plus',18)}</div></div></div>
    <div style="display:flex;gap:8px;padding:12px 16px 0;overflow:hidden">
      <span class="chip on">全部</span><span class="chip">文件</span><span class="chip">技能</span><span class="chip">连接器</span><span class="chip">记忆</span></div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:12px 16px 0">
      <div class="card" style="grid-column:span 2;padding:14px">
        <div style="display:flex;justify-content:space-between;align-items:center"><span style="font-size:15px;font-weight:500">文件</span><span class="muted" style="font-size:12.5px;display:flex;align-items:center;gap:2px">128 个 {ic('chevron-right',13)}</span></div>
        <div style="display:flex;gap:8px;margin-top:10px">
          <div style="flex:1;height:84px;border-radius:12px;background:var(--tile);padding:8px;display:flex;flex-direction:column;gap:4px">
            {''.join(f'<i style="height:7px;border-radius:2px;background:var(--hair);display:block;width:{w}%"></i>' for w in (100,90,95,70,85))}
            <span style="font-size:10.5px;margin-top:auto" class="muted">账单.csv</span></div>
          <div style="flex:1;height:84px;border-radius:12px;background:linear-gradient(160deg,#2B2D1C,#0F100A);padding:8px;display:flex;flex-direction:column;color:#DFF84B">
            <span style="font-size:13px;font-weight:500">Weekly</span><i style="height:4px;width:60%;border-radius:2px;background:#DFF84B;opacity:.5;display:block;margin-top:5px"></i>
            <span style="font-size:10.5px;margin-top:auto;color:#cfd7a0">weekly.html</span></div>
          <div style="flex:1;height:84px;border-radius:12px;background:radial-gradient(circle at 30% 30%,#7A8CFF,#2A2F55);padding:8px;display:flex;align-items:flex-end">
            <span style="font-size:10.5px;color:#E8EAFF">收据.jpg</span></div></div>
      </div>
      <div class="card" style="padding:14px;height:178px;position:relative">
        <div style="display:flex;justify-content:space-between"><span style="font-size:15px;font-weight:500">技能</span><span class="muted" style="font-size:12.5px">12</span></div>
        <div style="position:absolute;left:12px;right:12px;bottom:14px;height:100px">{deck}</div>
      </div>
      <div class="card" style="padding:14px;height:178px;position:relative;overflow:hidden">
        <div style="display:flex;justify-content:space-between"><span style="font-size:15px;font-weight:500">连接器</span><span class="muted" style="font-size:12.5px">6 已连</span></div>
        <div style="position:absolute;left:14px;right:14px;top:40px;bottom:6px">
          <div style="position:absolute;inset:14%;border-radius:50%;border:1px dashed var(--hair)"></div>{orbit}
          <div class="orb" style="position:absolute;left:calc(50% - 17px);top:calc(50% - 17px);width:34px;height:34px"></div></div>
      </div>
      <div class="card" style="padding:14px;background:var(--term);border-color:var(--term);color:#E9E8E3">
        <div style="display:flex;justify-content:space-between;align-items:center"><span style="font-size:15px;font-weight:500">沙箱</span><span class="dotlive"></span></div>
        <div class="mono" style="font-size:11px;margin-top:10px;line-height:1.7;color:#9b9a96">alpine 3.22<br><span style="color:#DFF84B">~ $</span> <span style="color:#E9E8E3">python3 parse.py</span><br>4 rows written<span style="display:inline-block;width:6px;height:11px;background:#DFF84B;vertical-align:middle;margin-left:3px"></span></div>
      </div>
      <div class="card" style="padding:14px">
        <div style="display:flex;justify-content:space-between"><span style="font-size:15px;font-weight:500">记忆</span><span class="muted" style="font-size:12.5px">34 条</span></div>
        <div style="display:flex;flex-direction:column;gap:6px;margin-top:10px">
          {''.join(f'<div class="mono" style="font-size:11.5px;display:flex;gap:6px;align-items:center;color:var(--ink2)">{ic("file-text",13)}{n}</div>' for n in ('SOUL.md','USER.md','GLOBAL.md'))}</div>
      </div>
      <div class="card" style="grid-column:span 2;padding:12px 14px;display:flex;align-items:center;gap:12px">
        <div style="width:38px;height:38px;border-radius:12px;background:var(--chip);color:var(--onchip);display:flex;align-items:center;justify-content:center">{ic('hand',19)}</div>
        <div style="flex:1"><div style="font-size:15px;font-weight:500">Hands</div><div class="muted" style="font-size:12.5px;margin-top:2px">手机在线</div></div>
        <div style="display:flex;gap:6px"><span class="chip on" style="height:28px">{ic('smartphone',13)} 手机</span><span class="chip" style="height:28px;opacity:.6">{ic('monitor',13)} 电脑</span></div>
      </div>
    </div>
    {nav(2, badge=2)}
    </div>"""

# ---------------- 我的 ----------------
def ring(pct, size, label, val):
    deg = pct * 3.6
    m = "radial-gradient(farthest-side,transparent calc(100% - 8px),#000 calc(100% - 7px))"
    return f'''<div style="display:flex;flex-direction:column;align-items:center;gap:8px">
      <div style="width:{size}px;height:{size}px;position:relative;display:flex;align-items:center;justify-content:center">
        <div style="position:absolute;inset:0;border-radius:50%;background:conic-gradient(var(--ac) {deg}deg, var(--tile) 0);-webkit-mask:{m};mask:{m}"></div>
        <span style="position:relative;font-size:17px;font-weight:500">{val}</span></div>
      <span class="muted" style="font-size:12.5px">{label}</span></div>'''

def seg(items, on):
    return '<div style="display:flex;background:var(--tile);border-radius:14px;padding:3px;gap:2px">' + ''.join(
        f'<span style="flex:1;height:30px;border-radius:11px;display:flex;align-items:center;justify-content:center;font-size:13px;'
        + ('background:var(--sf);font-weight:500;box-shadow:0 1px 3px rgba(0,0,0,.15)' if i == on else 'color:var(--ink2)') + f'">{t}</span>'
        for i, t in enumerate(items)) + '</div>'

def me():
    def goal(t, done, total, sub):
        p = done / total * 360
        return f'''<div style="display:flex;gap:12px;align-items:center;height:56px">
          <div style="width:28px;height:28px;border-radius:50%;background:conic-gradient(var(--ac) {p}deg, var(--tile) 0);-webkit-mask:radial-gradient(farthest-side,transparent calc(100% - 4px),#000 calc(100% - 3px));flex:none"></div>
          <div style="flex:1"><div style="font-size:15px">{t}</div><div class="muted" style="font-size:12px;margin-top:2px">{sub}</div></div>
          <span class="mono muted" style="font-size:12px">{done}/{total}</span></div>'''
    def row(icn, t, right=''):
        return f'<div style="display:flex;align-items:center;gap:12px;height:48px">{ic(icn,18)}<span style="flex:1;font-size:15px">{t}</span><span class="muted" style="font-size:13px">{right}</span>{ic("chevron-right",15)}</div>'
    return f"""<div class="phone">{status_bar()}
    <div style="padding:0 16px">
      <div style="display:flex;align-items:center;gap:14px;padding:10px 4px 0">
        <div class="orb" style="width:64px;height:64px"></div>
        <div style="flex:1"><div style="font-size:24px;font-weight:500">Neon</div><div class="muted" style="font-size:13.5px;margin-top:2px">替你跑腿，动手前先问你</div></div>
        <div class="btnc">{ic('pencil',16)}</div></div>
      <div style="display:flex;gap:8px;margin-top:12px;padding:0 4px"><span class="chip">{ic('cpu',13)} DeepSeek V4 Pro</span><span class="chip">{ic('shield',13)} Sentinel 平衡</span></div>
      <div class="card" style="margin-top:14px;padding:16px 10px;display:flex;justify-content:space-around">
        {ring(68,74,'今日额度','68%')}{ring(41,74,'本周','41%')}{ring(30,74,'存储','2.1G')}</div>
      <div class="sec"><span>目标与例行</span><span>{ic('plus',15)}</span></div>
      <div class="card" style="padding:0 14px">
        {goal('每周健身 3 次', 2, 3, '周一 09:00 跟你确认')}
        <div style="height:1px;background:var(--card)"></div>
        {goal('11 月记账', 4, 6, '每天 22:00 汇总')}
        <div style="height:1px;background:var(--card)"></div>
        <div style="display:flex;gap:12px;align-items:center;height:50px">{ic('repeat',18)}<span style="flex:1;font-size:15px">晨间早报</span><span class="mono muted" style="font-size:12px">每天 08:00</span></div>
      </div>
      <div class="card" style="margin-top:10px;padding:12px 14px">
        <div style="font-size:13px;margin-bottom:8px" class="muted">主动程度</div>{seg(['关','低','默认','高'],2)}
        <div style="font-size:13px;margin:12px 0 8px" class="muted">外观</div>{seg(['跟随系统','浅色','深色'],THEME_IDX)}
      </div>
      <div class="card" style="margin-top:10px;padding:0 14px">{row('key-round','模型与密钥')}{row('bell','通知','3 项')}</div>
    </div>
    {nav(3, badge=2)}
    </div>"""

THEME_IDX=2
SCREENS = {'T1-chat': chat, 'T2-glance': glance, 'T3-space': space, 'T4-me': me}

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(device_scale_factor=2, viewport={'width': 390, 'height': 844})
        global THEME_IDX
        for theme in ('dark', 'light'):
            THEME_IDX = 2 if theme=='dark' else 1
            for name, fn in SCREENS.items():
                html = f'<html><head><meta charset="utf-8"><style>{CSS}</style></head><body class="{theme}">{fn()}</body></html>'
                f = ROOT / f'{name}-{theme}.html'; f.write_text(html)
                await pg.goto(f'file://{f}'); await pg.wait_for_timeout(250)
                await pg.locator('.phone').screenshot(path=str(OUT / f'{name}-{theme}.png'))
        await b.close()

if __name__ == '__main__':
    asyncio.run(main())

def motion():
    def cell(title, ref, body):
        return f'''<div class="card" style="padding:16px;display:flex;flex-direction:column;gap:12px">
          <div style="display:flex;justify-content:space-between;align-items:baseline"><span style="font-size:16px;font-weight:500">{title}</span><span class="dim" style="font-size:11.5px">参考 {ref}</span></div>
          <div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:10px">{body}</div></div>'''
    def pill(left, width, label, icon):
        slots = ''.join(f'<div style="position:absolute;left:{8+i*80}px;top:8px;width:72px;height:40px;display:flex;align-items:center;justify-content:center;color:var(--ink2)">{ic(TABS[i][1],19)}</div>' for i in range(4))
        return f'''<div style="position:relative;height:56px;width:336px;border-radius:28px;background:var(--glass);border:1px solid var(--hair)">{slots}
          <div style="position:absolute;left:{left}px;top:7px;width:{width}px;height:42px;border-radius:21px;background:var(--ac);color:var(--onac);display:flex;align-items:center;justify-content:center;gap:6px;font-size:13px;font-weight:500;box-shadow:0 0 20px var(--glow)">{ic(icon,17,2)}{label}</div></div>'''
    navb = pill(8, 100, '对话', 'message-circle') + pill(8, 186, '', 'layout-dashboard').replace('justify-content:center;gap:6px','justify-content:flex-end;padding-right:44px;gap:6px') + pill(92, 104, '速览', 'layout-dashboard')
    navb += '<div class="dim" style="font-size:12px">点下一个 tab：胶囊先向目标拉长，再从原位收回，弹簧阻尼 0.72</div>'
    def tp(fill, label, done=False, pend=False):
        if pend:
            return f'<div style="height:38px;border-radius:19px;border:1px dashed var(--hair);display:flex;align-items:center;padding:0 14px;font-size:13px;gap:7px;color:var(--ink2)">{ic("circle-dashed",15)} {label}</div>'
        if done:
            return f'<div style="height:38px;border-radius:19px;background:var(--ac);color:var(--onac);display:flex;align-items:center;padding:0 14px;font-size:13px;gap:7px;font-weight:500;box-shadow:0 0 18px var(--glow)">{ic("check",15,2.5)} {label}</div>'
        return f'<div style="height:38px;border-radius:19px;background:var(--tile);position:relative;overflow:hidden;display:flex;align-items:center;padding:0 14px;font-size:13px"><div style="position:absolute;left:0;top:0;bottom:0;width:{fill}%;background:var(--fill);border-radius:19px"></div><span style="position:relative;display:flex;gap:7px;align-items:center;color:var(--onchip)">{ic("loader-circle",15,2)} {label}</span><span class="mono" style="position:relative;margin-left:auto;color:var(--onchip);font-size:12px">{fill}%</span></div>'
    toolb = tp(0, '查物流 等待中', pend=True) + tp(58, '读取轨迹中') + tp(100, '查到了 预计 15:00 送达', done=True)
    stepb = ''.join(f'<div style="font-size:{s}px;opacity:{o};font-weight:{w}">{t}</div>' for t, s, o, w in
                    [('读取 Gmail 账单', 13, .3, 400), ('解析成表格', 14, .5, 400), ('写入 Drive', 15, .72, 400), ('用 Hands 查物流', 21, 1, 500), ('整理回复', 14, .25, 400)])
    cnt = '''<div style="display:flex;gap:28px;align-items:flex-end">
      <div style="display:flex;align-items:baseline"><span style="font-size:56px;font-weight:500;line-height:1">67</span><span class="muted" style="font-size:20px">%</span></div>
      <div style="display:flex;align-items:baseline;position:relative"><span style="font-size:56px;font-weight:500;line-height:1">6</span>
        <span style="position:relative;display:inline-block;font-size:56px;font-weight:500;line-height:1"><span style="position:absolute;left:0;top:-40px;opacity:.22;filter:blur(1.5px)">8</span><span style="opacity:.55;filter:blur(.6px);display:inline-block;transform:translateY(14px)">7</span></span><span class="muted" style="font-size:20px">%</span></div>
      <div style="display:flex;align-items:baseline"><span style="font-size:56px;font-weight:500;line-height:1;text-shadow:0 0 22px var(--glow)">68</span><span class="muted" style="font-size:20px">%</span></div></div>
      <div class="dim" style="font-size:12px">额度和 token 数变化时逐位下落，新数字从上方带一点模糊进来</div>'''
    deckb = '''<div style="position:relative;height:120px">
      <div style="position:absolute;left:30px;right:30px;top:30px;height:78px;border-radius:16px;background:var(--tile);border:1px solid var(--hair);opacity:.5"></div>
      <div style="position:absolute;left:16px;right:16px;top:18px;height:84px;border-radius:16px;background:var(--sub);border:1px solid var(--hair);padding:12px;font-size:14px;font-weight:500">查快递</div>
      <div style="position:absolute;left:60px;right:0;top:-2px;height:84px;border-radius:16px;background:var(--sf);border:1px solid var(--hair);padding:12px;transform:rotate(7deg);opacity:.85;box-shadow:0 12px 30px rgba(0,0,0,.25);font-size:14px;font-weight:500">账单整理</div></div>
      <div class="dim" style="font-size:12px">技能卡左滑飞出，后面的卡前移一层</div>'''
    ticks = ''.join(f'<i style="position:absolute;left:50%;top:0;width:2px;height:8px;margin-left:-1px;border-radius:1px;transform-origin:1px 46px;transform:rotate({k*7.5}deg);background:{"var(--ac)" if k<10 else "var(--hair)"};display:block"></i>' for k in range(48))
    listen = f'''<div style="display:flex;align-items:center;gap:16px"><div style="position:relative;width:92px;height:92px">{ticks}
      <div style="position:absolute;inset:16px;display:flex;align-items:center;justify-content:center;font-size:18px;font-weight:500" class="mono">00:04</div></div>
      <div style="font-size:14px;line-height:1.6">说吧，我在听<div class="dim" style="font-size:12px">松开发送，上滑取消</div></div></div>'''
    return f'''<div class="phone" style="width:780px;height:1040px;padding:28px">
      <div style="font-size:26px;font-weight:500">动效</div><div class="muted" style="font-size:13px;margin:4px 0 18px">从 Figma Riffs 挑出来的六个，全部落在现有组件上</div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:14px;height:900px">
        {cell('底部导航', 'Crafttor', navb)}{cell('工具胶囊', 'Jorge Molina', toolb)}
        {cell('执行步骤', 'Hijrat', stepb)}{cell('数字滚落', 'Nastaran Jamali', cnt)}
        {cell('技能叠卡', 'Alejandro Vasquez', deckb)}{cell('语音输入', 'Hijrat', listen)}</div></div>'''

async def motion_main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(device_scale_factor=2, viewport={'width': 780, 'height': 1040})
        for theme in ('dark', 'light'):
            html = f'<html><head><meta charset="utf-8"><style>{CSS}</style></head><body class="{theme}">{motion()}</body></html>'
            f = ROOT / f'T5-motion-{theme}.html'; f.write_text(html)
            await pg.goto(f'file://{f}'); await pg.wait_for_timeout(250)
            await pg.locator('.phone').screenshot(path=str(OUT / f'T5-motion-{theme}.png'))
        await b.close()

if __name__ == '__main__':
    asyncio.run(motion_main())

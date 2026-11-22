#!/usr/bin/env python3
"""Neon v0.3.6 glass concept: 4 tabs + interaction states, dark and light."""
import asyncio, pathlib, subprocess
import gen
from gen import ic, status_bar, nav, CSS, TABS

ROOT = pathlib.Path(__file__).parent
OUT = ROOT / 'out2'; OUT.mkdir(exist_ok=True)

GLASS = """
.dark { --bg:#07080A; --sf:rgba(255,255,255,.06); --tile:rgba(255,255,255,.07); --sub:rgba(255,255,255,.09);
  --card:rgba(255,255,255,.11); --hair:rgba(255,255,255,.13); --bubble:rgba(255,255,255,.10); --glass:rgba(28,28,32,.42);
  --term:rgba(0,0,0,.50); --sheet:rgba(22,22,26,.62); --scrim:rgba(0,0,0,.38); --hl:rgba(255,255,255,.38); --hl2:rgba(255,255,255,.14); --sh:0 14px 36px rgba(0,0,0,.42); }
.light { --bg:#EFEEE9; --sf:rgba(255,255,255,.50); --tile:rgba(255,255,255,.55); --sub:rgba(255,255,255,.7);
  --card:rgba(255,255,255,.8); --hair:rgba(16,17,19,.09); --bubble:rgba(255,255,255,.62); --glass:rgba(255,255,255,.48);
  --term:rgba(16,17,19,.80); --sheet:rgba(255,255,255,.66); --scrim:rgba(40,36,24,.18); --hl:rgba(255,255,255,.95); --hl2:rgba(255,255,255,.5); --sh:0 14px 34px rgba(80,70,40,.12); }
.phone > *:not(.bgfx) { position:relative; z-index:2 }
.phone > .nav { position:absolute; z-index:20 }
.phone > .scrim { position:absolute; z-index:30 }
.phone > .ov { position:absolute; z-index:40 }
.bgfx { position:absolute; inset:0; z-index:0; overflow:hidden }
.bgfx i { position:absolute; border-radius:50%; display:block; filter:blur(46px) }
.card, .btnc, .chip, .nav, .glassy { backdrop-filter: blur(26px) saturate(180%); -webkit-backdrop-filter: blur(26px) saturate(180%); }
.card { box-shadow: inset 0 1px 0 var(--hl2), var(--sh); position:relative; }
.card::before, .nav::before, .btnc::before, .glassy::before { content:''; position:absolute; inset:0; border-radius:inherit; padding:1px; pointer-events:none;
  background:linear-gradient(145deg,var(--hl),rgba(255,255,255,0) 32%,rgba(255,255,255,0) 68%,var(--hl2));
  -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0); -webkit-mask-composite:xor; mask-composite:exclude; }
.card::after { content:''; position:absolute; inset:0; border-radius:inherit; pointer-events:none;
  background:radial-gradient(130% 70% at 15% -10%, rgba(255,255,255,.10), transparent 55%); }
.light .card::after { background:radial-gradient(130% 70% at 15% -10%, rgba(255,255,255,.55), transparent 55%); }
.btnc { position:relative; background:var(--sf) }
.chip { background:var(--tile) }
.chip.on { background:var(--chip) }
.light .chip.on { background:rgba(228,238,255,.85) }
.dark .chip.on { background:rgba(43,45,28,.75) }
.glassy { position:relative; background:var(--sheet); box-shadow: inset 0 1px 0 var(--hl2), 0 -10px 40px rgba(0,0,0,.25); }
.scrim { position:absolute; inset:0; background:var(--scrim); backdrop-filter: blur(6px); z-index:30 }
.ov { position:absolute; z-index:40 }
.grab { width:38px; height:5px; border-radius:3px; background:var(--hair); margin:8px auto 4px }
.lbl { font-size:12px; color:var(--ink2) }
.btn { height:46px; border-radius:23px; display:flex; align-items:center; justify-content:center; gap:7px; font-size:15px; font-weight:500 }
.btn.pri { background:var(--ac); color:var(--onac); box-shadow:0 0 22px var(--glow) }
.btn.sec { background:var(--tile); border:1px solid var(--hair) }
.toggle { width:44px; height:26px; border-radius:13px; background:var(--tile); border:1px solid var(--hair); position:relative; flex:none }
.toggle:after { content:''; position:absolute; left:3px; top:3px; width:18px; height:18px; border-radius:9px; background:var(--ink3) }
.toggle.on { background:var(--ac); border-color:var(--ac) }
.toggle.on:after { left:21px; background:var(--onac) }
"""

BG = {
 'dark': [('#DFF84B', .30, 280, '62%', '-6%'), ('#6A5CFF', .42, 320, '-30%', '38%'), ('#16C2A0', .24, 260, '55%', '70%'), ('#FF6A3D', .14, 200, '-10%', '88%')],
 'light': [('#E4FF5C', .55, 280, '60%', '-6%'), ('#B4ACFF', .55, 320, '-30%', '36%'), ('#9FF0DA', .45, 260, '55%', '68%'), ('#FFCFA8', .5, 220, '-12%', '86%')],
}
THEME = 'dark'

def bgfx():
    return '<div class="bgfx">' + ''.join(
        f'<i style="background:{c};opacity:{o};width:{s}px;height:{s}px;left:{x};top:{y}"></i>' for c, o, s, x, y in BG[THEME]) + '</div>'

def wrap(html):
    return html.replace('<div class="phone">', '<div class="phone">' + bgfx(), 1)

def overlay(base_html, inner, scrim=True):
    i = base_html.rstrip().rfind('</div>')
    add = ('<div class="scrim"></div>' if scrim else '') + inner
    return base_html[:i] + add + base_html[i:]

def sheet(body, h):
    return f'<div class="ov glassy" style="left:0;right:0;bottom:0;height:{h}px;border-radius:30px 30px 0 0;padding:0 18px">{"<div class=grab></div>"}{body}</div>'

# ---------- interaction screens ----------
def sessions():
    rows = [('主对话', '正在用 Hands 查物流', True, 'message-circle'), ('Neon 底部导航', '刚刚 设计稿已更新', False, 'layout-dashboard'),
            ('房东邮件', '等你确认发送', False, 'mail'), ('周报', '昨天', False, 'file-text'), ('@电脑 整理下载文件夹', '电脑离线', False, 'monitor')]
    lst = ''.join(f'''<div style="display:flex;align-items:center;gap:12px;height:60px;padding:0 12px;border-radius:18px;{'background:var(--chip);' if on else ''}">
        <div style="width:36px;height:36px;border-radius:12px;background:var(--tile);display:flex;align-items:center;justify-content:center;{'color:var(--onchip)' if on else ''}">{ic(icn,17)}</div>
        <div style="flex:1"><div style="font-size:15px;{'font-weight:500;color:var(--onchip)' if on else ''}">{t}</div><div class="lbl" style="margin-top:2px">{s}</div></div>
        {'<span class="dotlive"></span>' if on else ''}{'<span class="badge" style="position:static;border:none">1</span>' if t=='房东邮件' else ''}</div>''' for t, s, on, icn in rows)
    panel = f'''<div class="ov glassy" style="left:0;top:0;bottom:0;width:318px;border-radius:0 30px 30px 0;padding:58px 14px 0">
      <div style="display:flex;align-items:center;justify-content:space-between;padding:0 6px"><span style="font-size:22px;font-weight:500">会话</span>
        <div class="btnc">{ic('square-pen',17)}</div></div>
      <div style="margin:14px 0 10px;height:42px;border-radius:21px;background:var(--tile);border:1px solid var(--hair);display:flex;align-items:center;gap:8px;padding:0 14px" class="dim">{ic('search',16)}<span style="font-size:14px">搜索会话</span></div>
      <div style="display:flex;flex-direction:column;gap:4px">{lst}</div>
      <div class="lbl" style="margin:16px 8px 0">左滑归档，长按置顶</div></div>'''
    return overlay(wrap(gen.chat()), panel)

def approval():
    body = f'''<div style="display:flex;align-items:center;gap:10px;margin-top:8px"><span style="color:var(--danger)">{ic('shield-alert',22)}</span>
        <span style="font-size:19px;font-weight:500">发送前需要你确认</span></div>
      <div class="lbl" style="margin-top:4px">Neon 要替你发一封邮件，发出去就收不回了</div>
      <div class="card" style="padding:14px;margin-top:14px;border-radius:20px">
        <div style="display:flex;gap:10px;font-size:13.5px"><span class="muted" style="width:44px">收件人</span><span>王先生 landlord@163.com</span></div>
        <div style="display:flex;gap:10px;font-size:13.5px;margin-top:8px"><span class="muted" style="width:44px">主题</span><span>11 月房租和水电</span></div>
        <div style="font-size:13.5px;line-height:1.6;margin-top:10px;padding-top:10px;border-top:1px solid var(--card)" class="muted">王先生您好，11 月房租 4,200 元已转账，水电账单附在后面，麻烦查收。</div></div>
      <div class="lbl" style="margin:14px 2px 8px">允许范围</div>
      <div style="display:flex;background:var(--tile);border-radius:16px;padding:4px;gap:3px">
        {''.join(f'<span style="flex:1;height:34px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:13px;'+('background:var(--sub);font-weight:500;box-shadow:0 1px 4px rgba(0,0,0,.2)' if i==0 else 'color:var(--ink2)')+f'">{t}</span>' for i,t in enumerate(['仅这次','本对话','24 小时','总是']))}</div>
      <div style="display:flex;gap:10px;margin-top:18px"><div class="btn sec" style="flex:1">{ic('x',17)} 不发</div><div class="btn pri" style="flex:1.4">{ic('fingerprint',18)} 指纹确认发送</div></div>'''
    return overlay(wrap(gen.chat()), sheet(body, 470))

def tooldetail():
    body = f'''<div style="display:flex;align-items:center;gap:10px;margin-top:8px">
        <div style="width:38px;height:38px;border-radius:12px;background:var(--chip);color:var(--onchip);display:flex;align-items:center;justify-content:center">{ic('hand',18)}</div>
        <div style="flex:1"><div style="font-size:17px;font-weight:500">Hands 点按</div><div class="lbl">菜鸟 App 第 3 步，用时 2.4 秒</div></div><span class="chip on">{ic('check',13,2.5)} 完成</span></div>
      <div style="display:flex;gap:6px;margin:16px 0 10px"><span class="chip on">参数</span><span class="chip">输出</span><span class="chip">截图</span></div>
      <div class="card mono" style="padding:14px;font-size:12px;line-height:1.75;border-radius:18px;background:var(--term);color:#D8D7D2">
        <span style="color:#8A8A8E">tool</span>  hands.tap<br><span style="color:#8A8A8E">app</span>   com.cainiao.wireless<br>
        <span style="color:#8A8A8E">target</span> <span style="color:#DFF84B">"我的包裹"</span><br><span style="color:#8A8A8E">bounds</span> 412,1680 668,1792</div>
      <div style="height:150px;border-radius:18px;margin-top:12px;background:linear-gradient(180deg,#FF7A30,#FF5A1F 30%,#F4F4F4 30%);position:relative;overflow:hidden">
        <div style="position:absolute;left:26%;top:52%;width:120px;height:34px;border-radius:17px;border:2px solid #DFF84B;box-shadow:0 0 0 6px rgba(223,248,75,.25)"></div>
        <span style="position:absolute;left:12px;top:10px;color:#fff;font-size:13px;font-weight:500">菜鸟</span></div>
      <div style="display:flex;gap:10px;margin-top:14px"><div class="btn sec" style="flex:1">{ic('copy',16)} 复制</div><div class="btn sec" style="flex:1">{ic('rotate-cw',16)} 重试这步</div></div>'''
    return overlay(wrap(gen.chat()), sheet(body, 540))

def voice():
    ticks = ''.join(f'<i style="position:absolute;left:50%;top:0;width:3px;height:12px;margin-left:-1.5px;border-radius:2px;transform-origin:1.5px 80px;transform:rotate({k*6}deg);background:{"var(--ac)" if k<14 else "var(--hair)"};display:block"></i>' for k in range(60))
    wave = ''.join(f'<i style="width:4px;border-radius:2px;background:var(--ink);opacity:{.4+.6*(abs(((k*37)%11)-5)/5):.2f};height:{8+((k*53)%26)}px;display:block"></i>' for k in range(26))
    card = f'''<div class="ov card" style="left:22px;right:22px;top:210px;padding:26px 20px;border-radius:32px;display:flex;flex-direction:column;align-items:center">
      <div style="position:relative;width:160px;height:160px">{ticks}
        <div class="orb" style="position:absolute;left:44px;top:44px;width:72px;height:72px"></div></div>
      <div class="mono" style="font-size:26px;margin-top:14px">00:07</div>
      <div style="display:flex;align-items:center;gap:3px;height:36px;margin-top:10px">{wave}</div>
      <div style="font-size:16px;margin-top:12px;line-height:1.6;text-align:center">把这个月的账单也发一份给小林</div>
      <div class="lbl" style="margin-top:14px;display:flex;gap:6px;align-items:center">{ic('chevron-up',14)} 上滑取消，松开发送</div></div>
      <div class="ov" style="left:50%;bottom:118px;margin-left:-38px;width:76px;height:76px;border-radius:38px;background:var(--ac);color:var(--onac);display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 14px rgba(223,248,75,.16),0 0 40px var(--glow)">{ic('mic',30,2)}</div>'''
    return overlay(wrap(gen.chat()), card)

def post_detail():
    return wrap(f'''<div class="phone">{status_bar()}
    <div style="display:flex;align-items:center;justify-content:space-between;padding:4px 18px 0">
      <div class="btnc">{ic('arrow-left',18)}</div><span style="font-size:16px;font-weight:500">帖子</span><div class="btnc">{ic('external-link',17)}</div></div>
    <div style="padding:14px 16px 0">
      <div class="card" style="padding:16px">
        <div style="display:flex;align-items:center;gap:10px"><div style="width:40px;height:40px;border-radius:20px;background:linear-gradient(135deg,#7A8CFF,#222)"></div>
          <div style="flex:1"><div style="font-size:15px;font-weight:500">Compose 动效笔记</div><div class="lbl">@motion_jetpack  2 小时前</div></div><span style="font-weight:700">𝕏</span></div>
        <div style="font-size:15.5px;line-height:1.7;margin-top:12px">把 Figma Riffs 里那个弹性 tab 用 Compose 复刻了，关键是 spring 的 dampingRatio 设 0.72，宽度和位移分开做动画。代码放在回复里。</div>
        <div style="height:150px;border-radius:16px;margin-top:12px;background:radial-gradient(circle at 25% 30%,#7A8CFF,#1b1d2e 75%)"></div>
        <div style="display:flex;gap:16px;margin-top:12px;font-size:12.5px" class="muted"><span>86 回复</span><span>210 转发</span><span>1.9k 喜欢</span></div></div>
      <div class="card" style="padding:14px 16px;margin-top:12px">
        <div style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--onchip)"><div class="orb" style="width:22px;height:22px"></div> Neon 帮你读了</div>
        <div style="font-size:14px;line-height:1.75;margin-top:8px">宽度用 spring，位移用 tween 120ms，两段错开就有拉伸感。作者回复里的代码可以直接搬到 NavPill，大约 40 行。</div></div>
    </div>
    <div class="card" style="position:absolute;left:14px;right:14px;bottom:24px;padding:10px;display:flex;align-items:center;gap:8px;z-index:3">
      <span class="chip on" style="max-width:150px;overflow:hidden;white-space:nowrap">{ic('message-square-text',13)} 引用帖子</span>
      <span class="dim" style="flex:1;font-size:14px">照这个改我们的导航</span>
      <div style="width:40px;height:40px;border-radius:20px;background:var(--ac);color:var(--onac);display:flex;align-items:center;justify-content:center">{ic('arrow-up',19,2.2)}</div></div>
    </div>''')

def interests():
    chips = [('AI Agent', 1), ('Jetpack Compose', 1), ('独立开发', 1), ('信息差', 1), ('Rust', 0), ('开源模型', 1), ('设计动效', 1), ('出海', 0)]
    ch = ''.join(f'<span class="chip {"on" if o else ""}" style="height:34px;font-size:13.5px">{"" if not o else ic("check",13,2.5)}{t}</span>' for t, o in chips)
    def src(icn, t, s, on=True):
        return f'<div style="display:flex;align-items:center;gap:12px;height:58px"><div style="width:36px;height:36px;border-radius:12px;background:var(--tile);display:flex;align-items:center;justify-content:center;font-weight:700">{icn}</div><div style="flex:1"><div style="font-size:15px">{t}</div><div class="lbl">{s}</div></div><div class="toggle {"on" if on else ""}"></div></div>'
    body = f'''<div style="display:flex;justify-content:space-between;align-items:center;margin-top:6px"><span style="font-size:20px;font-weight:500">推送什么给你</span><span class="chip">{ic('plus',13)} 自定义</span></div>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:14px">{ch}</div>
      <div style="height:42px;border-radius:21px;background:var(--tile);border:1px solid var(--hair);display:flex;align-items:center;padding:0 14px;margin-top:12px;font-size:13.5px" class="dim">也可以直接说，比如 少推融资新闻，多推能直接用的开源项目</div>
      <div class="lbl" style="margin:16px 2px 2px">来源</div>
      {src('𝕏','X','已登录 @qigai，读你的关注和喜欢')}{src(ic('git-branch',17),'GitHub','趋势榜和你 star 过的方向')}
      <div class="lbl" style="margin:10px 2px 8px">推送时间</div>
      <div style="display:flex;background:var(--tile);border-radius:16px;padding:4px;gap:3px">{''.join(f'<span style="flex:1;height:34px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:13px;'+('background:var(--sub);font-weight:500' if i==0 else 'color:var(--ink2)')+f'">{t}</span>' for i,t in enumerate(['每天 08:00','每天两次','有信息差就推']))}</div>'''
    return overlay(wrap(gen.glance()), sheet(body, 600))

def model_picker():
    rows = [('DeepSeek V4 Pro', '默认，会深度思考', True), ('Kimi K2', '长文和读网页', False), ('Qwen3 Max', '看图和 Hands', False), ('自己的端点', 'OpenAI 兼容', False)]
    lst = ''.join(f'''<div style="display:flex;align-items:center;gap:12px;height:56px;padding:0 12px;border-radius:16px;{'background:var(--chip);' if on else ''}">
        <div style="width:32px;height:32px;border-radius:10px;background:var(--tile);display:flex;align-items:center;justify-content:center;{'color:var(--onchip)' if on else ''}">{ic('cpu',16)}</div>
        <div style="flex:1"><div style="font-size:15px;{'font-weight:500;color:var(--onchip)' if on else ''}">{t}</div><div class="lbl">{s}</div></div>{ic('check',17,2.5) if on else ''}</div>''' for t, s, on in rows)
    menu = f'''<div class="ov glassy" style="left:40px;right:40px;top:120px;border-radius:26px;padding:8px">{lst}
      <div style="height:1px;background:var(--hair);margin:6px 10px"></div>
      <div style="display:flex;align-items:center;gap:10px;height:46px;padding:0 14px;font-size:14px" class="muted">{ic('key-round',16)} 管理模型与密钥</div></div>
      <div class="ov" style="left:50%;top:96px;margin-left:-8px;width:16px;height:16px;transform:rotate(45deg);background:var(--sheet);border-radius:3px"></div>'''
    return overlay(wrap(gen.chat()), menu)

def skill_detail():
    md = '''<span style="color:#8A8A8E">---</span><br>name: bills<br>description: 读取本月账单邮件，整理成表格存到 Drive<br><span style="color:#8A8A8E">---</span><br><span style="color:#DFF84B">## 步骤</span><br>1. 搜索 Gmail 本月含 账单 的邮件<br>2. 在沙箱里用 python3 解析金额'''
    body = f'''<div style="display:flex;align-items:center;gap:12px;margin-top:8px">
        <div style="width:46px;height:46px;border-radius:15px;background:var(--chip);color:var(--onchip);display:flex;align-items:center;justify-content:center">{ic('mail',22)}</div>
        <div style="flex:1"><div style="font-size:19px;font-weight:500">账单整理</div><div class="lbl mono">/bills  内置  用过 12 次</div></div><div class="toggle on"></div></div>
      <div class="card mono" style="padding:14px;margin-top:16px;font-size:12px;line-height:1.75;border-radius:18px;background:var(--term);color:#D8D7D2">{md}</div>
      <div class="lbl" style="margin:14px 2px 8px">需要</div>
      <div style="display:flex;gap:8px"><span class="chip on">{ic('mail',13)} Gmail</span><span class="chip on">{ic('square-terminal',13)} 沙箱</span><span class="chip">{ic('cloud',13)} Drive</span></div>
      <div style="display:flex;gap:10px;margin-top:18px"><div class="btn sec" style="flex:1">{ic('pencil',16)} 改一份自己的</div><div class="btn pri" style="flex:1">{ic('play',16,2)} 现在运行</div></div>'''
    return overlay(wrap(gen.space()), sheet(body, 520))

def agent_edit():
    sw = ''.join(f'<span style="width:34px;height:34px;border-radius:17px;background:{c};display:block;{"box-shadow:0 0 0 3px var(--bg),0 0 0 5px "+c if i==0 else ""}"></span>' for i, c in enumerate(['#DFF84B', '#7A8CFF', '#FF8A5C', '#3FD6B0', '#F2F1EC']))
    return wrap(f'''<div class="phone">{status_bar()}
    <div style="display:flex;align-items:center;justify-content:space-between;padding:4px 18px 0">
      <div class="btnc">{ic('x',18)}</div><span style="font-size:16px;font-weight:500">Neon 的样子</span><span style="font-size:15px;font-weight:500;color:var(--onchip)">完成</span></div>
    <div style="display:flex;flex-direction:column;align-items:center;margin-top:22px">
      <div style="position:relative"><div class="orb" style="width:132px;height:132px;box-shadow:0 0 60px rgba(223,248,75,.45)"></div>
        <div class="btnc" style="position:absolute;right:-4px;bottom:0;width:38px;height:38px">{ic('wand-sparkles',17)}</div></div>
      <div class="lbl" style="margin-top:12px">描述一下，让图片模型重画它</div></div>
    <div style="padding:18px 16px 0">
      <div class="card" style="padding:4px 16px">
        <div style="display:flex;align-items:center;height:54px;border-bottom:1px solid var(--card)"><span class="muted" style="width:70px;font-size:14px">名字</span><span style="font-size:15px">Neon</span></div>
        <div style="display:flex;align-items:center;height:54px;border-bottom:1px solid var(--card)"><span class="muted" style="width:70px;font-size:14px">介绍</span><span style="font-size:15px">替你跑腿，动手前先问你</span></div>
        <div style="display:flex;align-items:center;height:54px"><span class="muted" style="width:70px;font-size:14px">叫你</span><span style="font-size:15px">QiGai</span></div></div>
      <div class="lbl" style="margin:16px 4px 8px">语气</div>
      <div style="display:flex;gap:8px;flex-wrap:wrap"><span class="chip on" style="height:34px">干脆</span><span class="chip" style="height:34px">温和</span><span class="chip" style="height:34px">话多一点</span><span class="chip" style="height:34px">只说结论</span></div>
      <div class="lbl" style="margin:16px 4px 10px">主色</div>
      <div style="display:flex;gap:16px;padding:0 6px">{sw}</div>
    </div>
    </div>''')

MAIN = [('01 对话', lambda: wrap(gen.chat()), '主对话，顶部是 Neon 的实时状态'),
        ('02 会话', sessions, '点左上角'),
        ('03 模型', model_picker, '点标题'),
        ('04 工具详情', tooldetail, '点工具胶囊'),
        ('05 确认', approval, '不可撤销的操作'),
        ('06 语音', voice, '按住麦克风'),
        ('07 速览', lambda: wrap(gen.glance()), '信息差 X GitHub'),
        ('08 帖子', post_detail, '点卡片或问 Neon'),
        ('09 兴趣', interests, '点右上角调节'),
        ('10 空间', lambda: wrap(gen.space()), 'bento 入口'),
        ('11 技能', skill_detail, '点技能叠卡'),
        ('12 我的', lambda: wrap(gen.me()), '额度 目标 设置'),
        ('13 形象', agent_edit, '点头像旁的编辑')]

async def main():
    global THEME
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(device_scale_factor=2, viewport={'width': 390, 'height': 844})
        for theme in ('dark', 'light'):
            THEME = theme
            gen.THEME_IDX = 2 if theme == 'dark' else 1
            for name, fn, _ in MAIN:
                html = f'<html><head><meta charset="utf-8"><style>{CSS}{GLASS}</style></head><body class="{theme}">{fn()}</body></html>'
                key = name.split()[0]
                f = OUT / f'{key}-{theme}.html'; f.write_text(html)
                await pg.goto(f'file://{f}'); await pg.wait_for_timeout(300)
                await pg.locator('.phone').first.screenshot(path=str(OUT / f'{key}-{theme}.png'))
        await b.close()

from playwright.async_api import async_playwright
if __name__ == '__main__':
    asyncio.run(main())

#!/usr/bin/env python3
"""Neon v0.4.5 我的 redesign boards."""
import asyncio, pathlib, sys
sys.path.insert(0, '/workspace/work/neon/design/v036')
import gen, gen2
from gen import ic, status_bar, nav, CSS, ring, seg
from gen2 import GLASS
ROOT = pathlib.Path(__file__).parent; OUT = ROOT / 'out'; OUT.mkdir(exist_ok=True)
X = """
.row{display:flex;align-items:center;gap:12px;min-height:50px}
.hr{height:1px;background:var(--card)}
.ttl{font-size:15px}.sub{font-size:12.5px;color:var(--ink2);margin-top:2px;line-height:1.45}
.field{background:var(--tile);border:1px solid var(--hair);border-radius:16px;padding:12px 14px;font-size:14.5px;line-height:1.6}
.pill{height:26px;padding:0 10px;border-radius:13px;font-size:12px;display:inline-flex;align-items:center;gap:4px;background:var(--tile);color:var(--ink2)}
.pill.on{background:var(--chip);color:var(--onchip)}
.radio{width:20px;height:20px;border-radius:10px;border:1.5px solid var(--ink3);flex:none;position:relative}
.radio.on{border-color:var(--ac);background:var(--ac)}
.radio.on:after{content:'';position:absolute;inset:5px;border-radius:50%;background:var(--onac)}
.opt{padding:14px 16px;display:flex;gap:12px;align-items:flex-start}
.opt.on{outline:1.5px solid var(--ac)}
.light .opt.on{outline-color:#101113}
.warn{background:color-mix(in srgb,var(--warn) 14%,transparent);border:1px solid color-mix(in srgb,var(--warn) 40%,transparent);border-radius:18px;padding:12px 14px}
.tile2{flex:1;padding:14px;display:flex;flex-direction:column;gap:10px;min-height:118px}
.ico{width:34px;height:34px;border-radius:12px;display:flex;align-items:center;justify-content:center;background:var(--tile)}
.bub{background:var(--bubble);border-radius:18px 18px 18px 6px;padding:11px 14px;font-size:14.5px;line-height:1.55}
"""
def top(title, right='完成'):
    return f'''<div style="display:flex;align-items:center;justify-content:space-between;padding:4px 16px 0">
      <div class="btnc">{ic('chevron-left',19)}</div><span style="font-size:16px;font-weight:500">{title}</span>
      <span style="font-size:15px;font-weight:500;color:var(--onchip);width:40px;text-align:right">{right}</span></div>'''
def tog(on): return f'<div class="toggle {"on" if on else ""}"></div>'
def mini(p, label, val):
    m = "radial-gradient(farthest-side,transparent calc(100% - 5px),#000 calc(100% - 4px))"
    return f'''<div style="display:flex;align-items:center;gap:9px;flex:1">
      <div style="width:36px;height:36px;border-radius:50%;background:conic-gradient(var(--ac) {p*3.6}deg,var(--tile) 0);-webkit-mask:{m};mask:{m};flex:none"></div>
      <div><div style="font-size:15px;font-weight:500">{val}</div><div class="muted" style="font-size:11.5px">{label}</div></div></div>'''

def me():
    def r(icn, t, right='', last=False, extra=None):
        tail = extra if extra is not None else f'<span class="muted" style="font-size:13px">{right}</span>{ic("chevron-right",15)}'
        return f'<div class="row" style="padding:0 2px">{ic(icn,18)}<span class="ttl" style="flex:1">{t}</span>{tail}</div>' + ('' if last else '<div class="hr"></div>')
    return gen2.wrap(f'''<div class="phone">{status_bar()}
    <div style="padding:0 16px">
      <div style="display:flex;align-items:center;gap:14px;padding:8px 4px 0">
        <div class="orb" style="width:64px;height:64px"></div>
        <div style="flex:1"><div style="font-size:24px;font-weight:500">Neon</div>
          <div class="muted" style="font-size:13px;margin-top:3px">叫你 QiGai，说话干脆</div></div>
        <div class="btnc">{ic('pencil',16)}</div></div>
      <div class="card" style="margin-top:16px;padding:14px 16px;display:flex;gap:12px;align-items:center">
        <div style="width:44px;height:44px;border-radius:22px;background:var(--tile);display:flex;align-items:center;justify-content:center;font-size:18px;font-weight:500">Q</div>
        <div style="flex:1;min-width:0"><div class="ttl" style="font-weight:500">关于你</div>
          <div class="sub" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">南京，独立开发者，在做 Neon</div></div>
        {ic('chevron-right',16)}</div>
      <div style="display:flex;gap:10px;margin-top:10px">
        <div class="card tile2"><div class="ico" style="color:var(--warn)">{ic('shield-check',18)}</div>
          <div><div class="ttl" style="font-weight:500">动手前</div><div class="sub">总是允许，付款要指纹</div></div></div>
        <div class="card tile2"><div class="ico">{ic('hand',18)}</div>
          <div><div class="ttl" style="font-weight:500">操作手机</div><div class="sub">智能调用</div></div></div></div>
      <div class="card" style="margin-top:10px;padding:12px 14px;display:flex">
        {mini(68,'5 小时','68%')}{mini(41,'本周','41%')}{mini(23,'本月','23%')}</div>
      <div class="card" style="margin-top:10px;padding:0 14px">
        {r('key-round','模型与密钥','DeepSeek V4 Pro')}
        {r('palette','外观','跟随系统')}
        {r('brain','记忆与定时任务','12 条')}
        {r('info','关于 Neon','v0.4.4', last=True)}
      </div>
    </div>{nav(3)}</div>''')

def persona():
    tones = [('干脆',1),('温和',0),('像朋友',0),('专业',0),('幽默',0),('只说结论',0)]
    return gen2.wrap(f'''<div class="phone">{status_bar()}{top('Neon 的性格')}
    <div style="padding:0 16px">
      <div style="display:flex;flex-direction:column;align-items:center;margin-top:14px">
        <div class="orb" style="width:84px;height:84px;box-shadow:0 0 44px rgba(223,248,75,.4)"></div></div>
      <div class="card" style="margin-top:16px;padding:2px 16px">
        <div class="row"><span class="muted" style="width:64px;font-size:14px">名字</span><span class="ttl" style="flex:1">Neon</span></div><div class="hr"></div>
        <div class="row"><span class="muted" style="width:64px;font-size:14px">怎么叫你</span><span class="ttl" style="flex:1">QiGai</span>{ic('x',15)}</div></div>
      <div style="display:flex;gap:6px;margin:8px 2px 0"><span class="pill">老板</span><span class="pill">老师</span><span class="pill">不用称呼</span></div>
      <div class="sec" style="margin-top:18px"><span>语气</span></div>
      <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">{''.join(f'<span class="chip {"on" if o else ""}" style="height:38px;justify-content:center;font-size:14px">{t}</span>' for t,o in tones)}</div>
      <div class="field" style="margin-top:10px;min-height:68px"><span class="muted" style="font-size:12px;display:block;margin-bottom:2px">再补充几句</span>少用感叹号，技术词保留英文，先给结论再解释</div>
      <div class="sec" style="margin-top:18px"><span>听起来像这样</span></div>
      <div style="display:flex;gap:9px;align-items:flex-end"><div class="orb" style="width:26px;height:26px"></div>
        <div class="bub">QiGai，包裹到新街口了，今天 18:00 前送到。要我顺手把账单表也发你吗？</div></div>
    </div></div>''')

def about():
    def f(k, v, last=False):
        return f'<div class="row"><span class="muted" style="width:56px;font-size:14px">{k}</span><span class="ttl" style="flex:1">{v}</span></div>' + ('' if last else '<div class="hr"></div>')
    return gen2.wrap(f'''<div class="phone">{status_bar()}{top('关于你')}
    <div style="padding:0 16px">
      <div class="muted" style="font-size:13px;margin:14px 4px 0">每次对话前 Neon 都会带上这些，只存在这台手机上</div>
      <div class="card" style="margin-top:12px;padding:2px 16px">{f('名字','QiGai')}{f('城市','南京')}{f('职业','独立开发者', True)}</div>
      <div class="sec"><span>背景</span><span class="dim">{ic('sparkles',14)}</span></div>
      <div class="field" style="min-height:118px">在做 Neon，一个安卓上的 AI 助手。熟悉 Kotlin 和 Compose，主力模型是 DeepSeek。周末喜欢骑车，常去玄武湖。</div>
      <div class="sec"><span>回答时</span></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap"><span class="chip on">用中文</span><span class="chip on">代码给完整文件</span><span class="chip">不要表情</span><span class="chip">{ic('plus',13)} 添加</span></div>
      <div class="card" style="margin-top:16px;padding:0 14px">
        <div class="row">{ic('brain',18)}<div style="flex:1"><div class="ttl">Neon 记住的</div><div class="sub">从对话里学到的 12 条，可以逐条改或删</div></div>{ic('chevron-right',15)}</div></div>
    </div></div>''')

def perms():
    def opt(t, s, on=False, tag=''):
        t2 = f'<span class="pill" style="margin-left:6px;height:20px;font-size:11px">{tag}</span>' if tag else ''
        return f'''<div class="card opt {"on" if on else ""}" style="margin-top:8px"><div class="radio {"on" if on else ""}" style="margin-top:1px"></div>
          <div style="flex:1"><div class="ttl" style="font-weight:500;display:flex;align-items:center">{t}{t2}</div><div class="sub">{s}</div></div></div>'''
    def kind(icn, t, ask, last=False):
        p = f'<div style="display:flex;background:var(--tile);border-radius:12px;padding:2px">' + ''.join(
            f'<span style="height:26px;padding:0 11px;border-radius:10px;display:flex;align-items:center;font-size:12.5px;{"background:var(--sf);font-weight:500" if (x=="问我")==ask else "color:var(--ink2)"}">{x}</span>' for x in ('问我','允许')) + '</div>'
        return f'<div class="row" style="min-height:48px">{ic(icn,17)}<span class="ttl" style="flex:1;font-size:14.5px">{t}</span>{p}</div>' + ('' if last else '<div class="hr"></div>')
    return gen2.wrap(f'''<div class="phone">{status_bar()}{top('动手前', '')}
    <div style="padding:0 16px">
      {opt('每次都问','写文件、发消息、打开 App 都先问你')}
      {opt('重要的才问','查和读直接做，发送、删除、付款先问', tag='默认')}
      {opt('总是允许','不再弹确认，Neon 直接动手', on=True)}
      <div class="warn" style="margin-top:10px;display:flex;gap:10px">
        <span style="color:var(--warn);margin-top:1px">{ic('triangle-alert',17)}</span>
        <div style="flex:1;font-size:13px;line-height:1.5">Neon 会直接发消息、删文件、操作 App。每一步仍会记在对话里，随时能停。</div></div>
      <div class="sec"><span>这些还是问我</span></div>
      <div class="card" style="padding:0 14px">
        {kind('credit-card','付款',True)}
        {kind('trash-2','删除文件和数据',False)}
        {kind('send','发消息和邮件',False)}
        {kind('smartphone','操作手机上的 App',False, True)}
      </div>
      <div class="card" style="margin-top:10px;padding:0 14px">
        <div class="row">{ic('fingerprint',18)}<span class="ttl" style="flex:1;font-size:14.5px">问我时用指纹确认</span>{tog(True)}</div><div class="hr"></div>
        <div class="row">{ic('layers',18)}<div style="flex:1"><div class="ttl" style="font-size:14.5px">思考和命令收成一行</div><div class="sub" style="margin-top:0">点按展开每一步</div></div>{tog(True)}</div></div>
    </div></div>''')


def chat(mode):
    bub = mode == 'bubble'
    def user(t): return f'<div style="display:flex;justify-content:flex-end;margin-top:12px"><div style="max-width:78%;background:var(--chip);color:var(--ink);border-radius:20px 20px 6px 20px;padding:10px 14px;font-size:15px;line-height:1.55">{t}</div></div>'
    def neon(t, cards=''):
        if bub:
            return f'<div style="display:flex;gap:8px;align-items:flex-end;margin-top:12px"><div class="orb" style="width:26px;height:26px"></div><div style="max-width:80%"><div class="bub card" style="border-radius:20px 20px 20px 6px">{t}</div>{cards}</div></div>'
        return f'<div style="margin-top:14px;font-size:15.5px;line-height:1.7">{t}</div>{cards}'
    fold = f'<div style="display:inline-flex;align-items:center;gap:6px;height:28px;padding:0 12px;border:1px solid var(--hair);border-radius:14px;font-size:12.5px;color:var(--ink2);margin-top:12px;{"margin-left:34px" if bub else ""}">{ic("sparkles",13)} 思考 6 秒，执行 3 步 {ic("chevron-right",12)}</div>'
    card = f'<div class="card" style="margin-top:8px;padding:10px 12px;display:flex;gap:10px;align-items:center"><div class="ico" style="color:#4F7DF5">{ic("file-text",17)}</div><div style="flex:1"><div style="font-size:14px">11 月账单.xlsx</div><div class="sub" style="margin-top:0">表格 18 KB</div></div>{ic("share-2",15)}</div>'
    seg2 = seg(['文档', '气泡'], 1 if bub else 0)
    return gen2.wrap(f"""<div class="phone">{status_bar()}
    <div style="display:flex;align-items:center;justify-content:space-between;padding:4px 16px 0">
      <div class="btnc">{ic('menu',18)}</div>
      <div style="display:flex;flex-direction:column;align-items:center;gap:2px"><div style="display:flex;gap:7px;align-items:center"><div class="orb" style="width:24px;height:24px"></div><span style="font-size:16px;font-weight:500">账单与快递</span></div>
        <span class="muted" style="font-size:11.5px">DeepSeek V4 Pro</span></div>
      <div class="btnc">{ic('square-pen',17)}</div></div>
    <div style="padding:6px 16px 0">
      <div style="width:150px;margin:6px auto 0">{seg2}</div>
      {user('帮我把本月账单整理成表格，再查一下快递到哪了')}
      {fold}
      {neon('整理好了，4 封账单合计 ¥1,286.40。顺丰包裹在新街口派送中，今天 18:00 前送到。', card)}
      {user('谢谢，明天提醒我还信用卡')}
      {neon('好，明天 09:00 提醒你。')}
    </div>
    <div class="card" style="position:absolute;left:14px;right:14px;bottom:100px;padding:12px 14px">
      <div class="muted" style="font-size:15px">给 Neon 发消息</div>
      <div style="display:flex;gap:8px;margin-top:12px;align-items:center"><span class="chip">{ic('plus',14)}</span><span class="chip">{ic('slash',13)} 技能</span><span class="chip on">{ic('sparkles',13)} 思考 High</span><span style="flex:1"></span><span class="btnc" style="background:var(--ac);color:var(--onac);border:none">{ic('mic',17)}</span></div></div>
    {nav(0)}</div>""")

SCREENS = [('05-chat-doc', lambda: chat('doc')), ('06-chat-bubble', lambda: chat('bubble')), ('01-me', me), ('02-persona', persona), ('03-about', about), ('04-perms', perms)]
async def main():
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(device_scale_factor=2, viewport={'width': 390, 'height': 844})
        for theme in ('dark', 'light'):
            gen2.THEME = theme
            for name, fn in SCREENS:
                html = f'<html><head><meta charset="utf-8"><style>{CSS}{GLASS}{X}</style></head><body class="{theme}">{fn()}</body></html>'
                f = OUT / f'{name}-{theme}.html'; f.write_text(html)
                await pg.goto(f'file://{f}'); await pg.wait_for_timeout(300)
                await pg.locator('.phone').first.screenshot(path=str(OUT / f'{name}-{theme}.png'))
        await b.close()
asyncio.run(main())

import asyncio
from playwright.async_api import async_playwright
HTML='''<html><body style="margin:0;background:transparent"><div id=i style="width:512px;height:512px;border-radius:112px;overflow:hidden;position:relative;background:radial-gradient(circle at 50% 50%,rgba(223,248,75,.33),rgba(223,248,75,.08) 60%,rgba(223,248,75,0) 100%),#0B0B0C">
<div style="position:absolute;left:114px;top:114px;width:284px;height:284px;border-radius:50%;background:radial-gradient(circle at 34% 30%,#FBFFD0 0%,#E6FA62 30%,#BEDB2C 70%,#8FA816 100%);box-shadow:0 0 60px rgba(223,248,75,.35)">
<i style="position:absolute;left:33%;top:42%;width:12%;height:20%;background:#16180A;border-radius:999px"></i>
<i style="position:absolute;right:33%;top:42%;width:12%;height:20%;background:#16180A;border-radius:999px"></i></div></div></body></html>'''
async def main():
    async with async_playwright() as p:
        b=await p.chromium.launch(); pg=await b.new_page(viewport={'width':512,'height':512})
        await pg.set_content(HTML); await pg.locator('#i').screenshot(path='icon-512.png',omit_background=True); await b.close()
asyncio.run(main())

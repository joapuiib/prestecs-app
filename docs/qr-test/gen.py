"""Regenerates index.html: one test QR at a time, with arrows to iterate.

Usage: pip install segno && python gen.py   (writes index.html next to this file)
"""
import json, segno, sys
laptops = [f"C2A1 - PC0{i}" for i in range(1, 6)]
students = [("1001","Anna","Ferrer"),("1002","Marc","Soler"),("1003","Laia","Vidal"),
            ("1004","Pau","Roca"),("1005","Marta","Gil")]
items = [(l, l) for l in laptops]
for nia, n, s in students:
    items.append((f"{nia} - {s}, {n}",
        json.dumps({"version":"0.1","nia":nia,"name":n,"surname":s}, separators=(",",":"))))
data = [{"label": l, "svg": segno.make(p, error="h").svg_inline(border=4, omitsize=True, scale=1)}
        for l, p in items]
page = """<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>QR proves</title>
<style>
html,body{height:100%;margin:0;background:#fff;font-family:sans-serif}
body{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px}
#qr{width:min(70vh,70vw);height:min(70vh,70vw)}#qr svg{width:100%;height:100%;shape-rendering:crispEdges}
#row{display:flex;align-items:center;gap:24px}
button{font-size:48px;width:80px;height:80px;border:1px solid #888;border-radius:50%;background:#eee;cursor:pointer}
#label{font-size:24px}#count{color:#666}
</style>
<div id=label></div>
<div id=row><button id=prev aria-label=Anterior>&#8592;</button><div id=qr></div><button id=next aria-label=Següent>&#8594;</button></div>
<div id=count></div>
<script>
const items = __DATA__;
let i = 0;
const show = n => {
  i = (n + items.length) % items.length;
  qr.innerHTML = items[i].svg;
  label.textContent = items[i].label;
  count.textContent = (i + 1) + " / " + items.length;
};
prev.onclick = () => show(i - 1);
next.onclick = () => show(i + 1);
addEventListener("keydown", e => {
  if (e.key === "ArrowLeft") show(i - 1);
  if (e.key === "ArrowRight" || e.key === " ") show(i + 1);
});
show(0);
</script>""".replace("__DATA__", json.dumps(data))
open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "index.html"), "w").write(page)

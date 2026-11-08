# Draws PDF Reader's launcher icons (release, test build, themed) from one set of shapes.
# Run from anywhere: python3 tools/make_icon.py <repo root>
import math, os, sys
OUT = sys.argv[1]  # repo root

# ---- The soft N (from the original mark, 1536x1024 artwork)
L = [(500,285),(732,440),(610,530),(610,742),(500,742)]
R = [(858,295),(962,295),(962,738),(732,577),(858,490)]
DOT = (1062, 684, 64)

def soft(pts, r, tf):
    n = len(pts); segs = []
    for i in range(n):
        p0, p, p1 = pts[i-1], pts[i], pts[(i+1) % n]
        a = (p0[0]-p[0], p0[1]-p[1]); b = (p1[0]-p[0], p1[1]-p[1])
        la, lb = math.hypot(*a), math.hypot(*b)
        t = min(r, la*0.48, lb*0.48)
        segs.append(((p[0]+a[0]/la*t, p[1]+a[1]/la*t), p, (p[0]+b[0]/lb*t, p[1]+b[1]/lb*t)))
    f = lambda q: "%.2f,%.2f" % tf(q)
    d = "M" + f(segs[0][0]) + " "
    for i, (s, p, e) in enumerate(segs):
        if i: d += "L" + f(s) + " "
        d += "Q" + f(p) + " " + f(e) + " "
    return d + "Z"

def circle(cx, cy, r):
    return "M%.2f,%.2f a%.2f,%.2f 0 1,1 %.2f,0 a%.2f,%.2f 0 1,1 %.2f,0 Z" % (cx-r, cy, r, r, 2*r, r, r, -2*r)

def n_paths(x, y, width):
    """The N placed with its top-left at (x,y), [width] wide, in icon units."""
    k = width / 643.0
    tf = lambda q: (x + (q[0]-483)*k, y + (q[1]-268)*k)
    cx, cy = tf((DOT[0], DOT[1]))
    return soft(L, 36, tf), soft(R, 36, tf), circle(cx, cy, DOT[2]*k), 34*k, tf, k

# ---- Icon layout on a 108x108 adaptive canvas; artwork kept inside the 66 safe circle
K = 50/304.0                         # page drawn 304 tall in the design, 50 here
def P(x, y): return (54 + (x-215)*K, 54 + (y-212)*K)
def ps(*xy): return " ".join("%.2f,%.2f" % P(xy[i], xy[i+1]) for i in range(0, len(xy), 2))
def page_d():
    # Page with rounded corners and a folded top-right corner
    return ("M%s H%.2f L%s V%.2f Q%s %s H%.2f Q%s %s V%.2f Q%s %s Z" % (
        ps(114,60), P(260,0)[0], ps(340,140), P(0,340)[1], ps(340,364), ps(316,364),
        P(114,0)[0], ps(90,364), ps(90,340), P(0,84)[1], ps(90,60), ps(114,60)))
def fold_cut_d():
    return "M%s V%.2f Q%s %s H%.2f" % (ps(260,60), P(0,126)[1], ps(260,140), ps(274,140), P(340,0)[0])
def fold_d():
    return "M%s L%s H%.2f Q%s %s Z" % (ps(260,60), ps(340,140), P(274,0)[0], ps(260,140), ps(260,126))
def pdf_d():
    # Bold rounded P, D, F drawn as strokes
    o = -8
    p = "M%s V%.2f M%s H%.2f Q%s %s Q%s %s H%.2f" % (
        ps(165+o,292), P(0,335)[1], ps(165+o,292), P(180+o,0)[0], ps(198+o,292), ps(198+o,307),
        ps(198+o,322), ps(180+o,322), P(165+o,0)[0])
    d = "M%s V%.2f H%.2f Q%s %s Q%s %s Z" % (
        ps(208+o,292), P(0,335)[1], P(220+o,0)[0], ps(243+o,335), ps(243+o,313.5), ps(243+o,292), ps(220+o,292))
    f = "M%s V%.2f M%s H%.2f M%s H%.2f" % (
        ps(258+o,292), P(0,335)[1], ps(258+o,292), P(282+o,0)[0], ps(258+o,313), P(277+o,0)[0])
    return p + " " + d + " " + f, 13*K

def shine_d(tf, k):
    a = tf((530,345)); b = tf((530,450)); c = tf((890,330)); d = tf((890,410))
    lines = "M%.2f,%.2f L%.2f,%.2f M%.2f,%.2f L%.2f,%.2f" % (*a, *b, *c, *d)
    sx, sy = tf((1040, 660))
    return lines, 22*k, circle(sx, sy, 18*k)

def variant(page, cut, fold, n_col, shine, text):
    """Returns (vector drawable xml, svg body) for one look."""
    nx, ny = P(145, 140); nw = 130*K
    nl, nr, dot, grow, tf, k = n_paths(nx, ny, nw)
    sh, shw, shdot = shine_d(tf, k)
    pdf, pdfw = pdf_d()
    items = [
        ("fill", page_d(), page, None),
        ("fill", fold_d(), fold, None) if fold else None,
        ("stroke", fold_cut_d(), cut, 16*K),
        ("both", nl, n_col, grow), ("both", nr, n_col, grow), ("fill", dot, n_col, None),
        ("stroke", sh, shine, shw), ("fill", shdot, shine, None),
        ("stroke", pdf, text, pdfw),
    ]
    xml = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated: the soft Numio N on a PDF page. Drawn inside the 66dp safe circle. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    svg = []
    for it in items:
        if not it: continue
        kind, d, col, w = it
        a = 'android:pathData="%s"' % d
        if kind == "fill":
            xml.append('    <path android:fillColor="#FF%s" %s />' % (col[1:], a))
            svg.append('<path d="%s" fill="%s"/>' % (d, col))
        elif kind == "stroke":
            xml.append('    <path android:strokeColor="#FF%s" android:strokeWidth="%.2f" android:strokeLineCap="round" android:strokeLineJoin="round" %s />' % (col[1:], w, a))
            svg.append('<path d="%s" fill="none" stroke="%s" stroke-width="%.2f" stroke-linecap="round" stroke-linejoin="round"/>' % (d, col, w))
        else:
            xml.append('    <path android:fillColor="#FF%s" android:strokeColor="#FF%s" android:strokeWidth="%.2f" android:strokeLineJoin="round" %s />' % (col[1:], col[1:], w, a))
            svg.append('<path d="%s" fill="%s" stroke="%s" stroke-width="%.2f" stroke-linejoin="round"/>' % (d, col, col, w))
    xml.append('</vector>')
    return "\n".join(xml) + "\n", "".join(svg)

A, BG, DARK = "#FFC21A", "#0F0F0E", "#2A2925"
release_xml, release_svg = variant(A, BG, None, BG, "#5A5750", BG)          # 3: glossy black N + PDF
debug_xml, debug_svg = variant(DARK, BG, A, A, "#FFF6D6", A)                 # 4: amber N on a dark page

# Monochrome (themed icons): just the N, bigger
nl, nr, dot, grow, tf, k = n_paths(54-17, 54-13, 34)
mono = ['<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Themed icon layer (Android 13+): the soft Numio N on its own; the launcher picks the colour. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp" android:height="108dp"',
        '    android:viewportWidth="108" android:viewportHeight="108">']
for d in (nl, nr):
    mono.append('    <path android:fillColor="#FF000000" android:strokeColor="#FF000000" android:strokeWidth="%.2f" android:strokeLineJoin="round" android:pathData="%s" />' % (grow, d))
mono.append('    <path android:fillColor="#FF000000" android:pathData="%s" />' % dot)
mono.append('</vector>')

res = os.path.join(OUT, "app/src")
open(os.path.join(res, "main/res/drawable/ic_launcher_foreground.xml"), "w").write(release_xml)
os.makedirs(os.path.join(res, "debug/res/drawable"), exist_ok=True)
open(os.path.join(res, "debug/res/drawable/ic_launcher_foreground.xml"), "w").write(debug_xml)
open(os.path.join(res, "main/res/drawable/ic_launcher_monochrome.xml"), "w").write("\n".join(mono) + "\n")

def preview(body, name, size=512, mask=True):
    # Shown the way a launcher crops it: the middle 72 of 108, rounded
    clip = '<clipPath id="c"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>' if mask else ""
    s = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="18 18 72 72"><defs>%s</defs>'
         '<g clip-path="url(#c)"><rect width="108" height="108" fill="%s"/>%s</g></svg>') % (size, size, clip, BG, body)
    open(name, "w").write(s)
preview(release_svg, "release.svg"); preview(debug_svg, "debug.svg")
mono_svg = "".join('<path d="%s" fill="#FFC21A" stroke="#FFC21A" stroke-width="%.2f" stroke-linejoin="round"/>' % (d, grow) for d in (nl, nr)) + '<path d="%s" fill="#FFC21A"/>' % dot
preview(mono_svg, "mono.svg")
# Circle mask check: the safe zone
s = ('<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108"><rect width="108" height="108" fill="%s"/>%s'
     '<circle cx="54" cy="54" r="33" fill="none" stroke="#E03131" stroke-width=".4"/></svg>') % (BG, release_svg)
open("safe.svg", "w").write(s)

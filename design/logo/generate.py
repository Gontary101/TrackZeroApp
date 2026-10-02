import math, sys
# TrackZero logo (C2): a zero that is a loop ride, from the start ring back to it; solid where ridden, dots ahead.
# Units are the adaptive icon's 108 grid; a launcher shows the middle 72.
K = 0.68                      # the mark's size: about half the visible icon, as approved
HW, HH, R = 13.0*K, 20.0*K, 11.0*K   # the zero: a rounded rectangle, taller than wide
LINE, DOT = 5.4*K, 2.7*K      # solid stroke width, dot radius (dot diameter = stroke width)
RING_BG, RING_R, RING_W, RING_DOT = 7.4*K, 6.0*K, 2.5*K, 2.2*K
SOLID = 0.40                  # share of the loop already ridden
CX = 54.0
CY = 54.0 - (RING_BG - DOT)/2  # centres the mark's extent (dots above, ring below)

def outline(n=4000):
    """Points along the outline, from the bottom centre, going left (clockwise on screen)."""
    L, T, Rt, B = CX-HW, CY-HH, CX+HW, CY+HH
    segs = []
    segs.append(('line', (CX, B), (L+R, B)))
    segs.append(('arc', (L+R, B-R), 90, 180))     # bottom-left corner
    segs.append(('line', (L, B-R), (L, T+R)))
    segs.append(('arc', (L+R, T+R), 180, 270))    # top-left
    segs.append(('line', (L+R, T), (Rt-R, T)))
    segs.append(('arc', (Rt-R, T+R), 270, 360))   # top-right
    segs.append(('line', (Rt, T+R), (Rt, B-R)))
    segs.append(('arc', (Rt-R, B-R), 0, 90))      # bottom-right
    segs.append(('line', (Rt-R, B), (CX, B)))
    pts = []
    for s in segs:
        if s[0] == 'line':
            (x0, y0), (x1, y1) = s[1], s[2]
            k = 200
            pts += [(x0+(x1-x0)*i/k, y0+(y1-y0)*i/k) for i in range(k)]
        else:
            (cx, cy), a0, a1 = s[1], s[2], s[3]
            k = 200
            pts += [(cx+R*math.cos(math.radians(a0+(a1-a0)*i/k)), cy+R*math.sin(math.radians(a0+(a1-a0)*i/k))) for i in range(k)]
    pts.append((CX, B))
    return pts, segs

pts, segs = outline()
cum = [0.0]
for i in range(1, len(pts)):
    cum.append(cum[-1] + math.dist(pts[i-1], pts[i]))
total = cum[-1]
def at(s):
    for i in range(1, len(cum)):
        if cum[i] >= s:
            t = (s-cum[i-1])/(cum[i]-cum[i-1] or 1)
            return (pts[i-1][0]+(pts[i][0]-pts[i-1][0])*t, pts[i-1][1]+(pts[i][1]-pts[i-1][1])*t)
    return pts[-1]

ring_clear = 11.5*K              # the ring's room at the end: the last dot clears its black disc
solid_end = SOLID*total
first = solid_end + 6.2*K        # gap after the ridden part
last = total - ring_clear
n = round((last-first)/(6.0*K))
step = (last-first)/n
dots = [at(first+i*step) for i in range(n+1)]

# The ridden part as an SVG path: bottom centre → left → up → top-left, sampled finely (exact arcs below).
def solid_path():
    # Rebuild from segments up to solid_end with true arcs.
    d = []
    s_acc = 0.0
    x, y = CX, CY+HH
    d.append(f"M{x:.2f},{y:.2f}")
    for s in segs:
        if s[0] == 'line':
            (x0, y0), (x1, y1) = s[1], s[2]
            l = math.dist((x0, y0), (x1, y1))
            if s_acc + l >= solid_end:
                t = (solid_end - s_acc)/l
                d.append(f"L{x0+(x1-x0)*t:.2f},{y0+(y1-y0)*t:.2f}")
                return " ".join(d)
            d.append(f"L{x1:.2f},{y1:.2f}")
            s_acc += l
        else:
            (cx, cy), a0, a1 = s[1], s[2], s[3]
            l = R*math.radians(a1-a0)
            if s_acc + l >= solid_end:
                a = a0 + (a1-a0)*(solid_end-s_acc)/l
                ex, ey = cx+R*math.cos(math.radians(a)), cy+R*math.sin(math.radians(a))
                d.append(f"A{R:.2f},{R:.2f} 0 0,1 {ex:.2f},{ey:.2f}")
                return " ".join(d)
            ex, ey = cx+R*math.cos(math.radians(a1)), cy+R*math.sin(math.radians(a1))
            d.append(f"A{R:.2f},{R:.2f} 0 0,1 {ex:.2f},{ey:.2f}")
            s_acc += l
    return " ".join(d)

SP = solid_path()
RX, RY = CX, CY+HH
print("total", round(total,1), "solid end", round(solid_end,1), "dots", len(dots), "step", round(step,2), file=sys.stderr)

def mark(dot_fill="#A9F7B9", line="#A9F7B9", ring_bg="#000"):
    out = [f'<path d="{SP}" fill="none" stroke="{line}" stroke-width="{LINE:.2f}" stroke-linecap="round" stroke-linejoin="round"/>']
    out += [f'<circle cx="{x:.2f}" cy="{y:.2f}" r="{DOT:.2f}" fill="{dot_fill}"/>' for x, y in dots]
    out += [f'<circle cx="{RX:.2f}" cy="{RY:.2f}" r="{RING_BG:.2f}" fill="{ring_bg}"/>',
            f'<circle cx="{RX:.2f}" cy="{RY:.2f}" r="{RING_R:.2f}" fill="none" stroke="{line}" stroke-width="{RING_W:.2f}"/>',
            f'<circle cx="{RX:.2f}" cy="{RY:.2f}" r="{RING_DOT:.2f}" fill="{line}"/>']
    return "\n  ".join(out)

if __name__ == "__main__":
    which = sys.argv[1] if len(sys.argv) > 1 else "mint"
    if which == "mint":
        print(mark())
    elif which == "dim":
        print(mark(dot_fill="#5E8F68"))
    elif which == "white":
        print(mark(dot_fill="#F4F5F2"))

from pathlib import Path

p = Path('src/main/java/org/server/mifron/ServerPortalFeature.java')
s = p.read_text(encoding='utf-8')

old = '''         // Raise the outer destinations slightly to make the curve visible and keep the
         // middle destination aligned with the player's aim point.
         double vertical = (rows - 1) * 0.64 - row * 1.28 + 0.38 * normalized * normalized;
'''

new = '''         // Keep every option in a row at exactly the same Y level.
         // Curvature is depth-only, so the selector bends horizontally around the player
         // without creating an unwanted vertical parabola.
         double vertical = (rows - 1) * 0.64 - row * 1.28;
'''

if old not in s:
    raise SystemExit('current Y-axis teleporter curvature block not found')

s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('removed Y-axis curvature from teleporter selector')

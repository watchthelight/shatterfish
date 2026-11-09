"""Story 10.46: how the fatal fights of depths 1 to 4 begin, read from debug records (*.debug.ndjson).

    python 2026-11-08-10-46-hurt-fights.py <folder of debug records> [<folder> ...]

A debug record keeps, for each Decision it recorded, the hit points, the hunger, the pack, the enemies in view and the
Decision's goal and reason. A fight is a run of recorded Decisions with an enemy in view, no more than 3 Decisions apart;
the fatal fight is the last one, and it must reach the end of the record. The record keeps no Decision that was not a wait
screen, so a gap of a few Decisions may hide a quiet stretch; the numbers below count what the record shows.
"""
import collections
import glob
import json
import re
import sys

GAP = 3


def load(path):
    screens, end = [], None
    with open(path, encoding='utf8') as lines:
        for line in lines:
            row = json.loads(line)
            if row.get('t') == 'wait':
                screens.append(row)
            elif row.get('t') == 'end':
                end = row
    screens.sort(key=lambda s: s['k'])
    return screens, end


def fights(screens):
    found, current = [], None
    for i, s in enumerate(screens):
        if s['enemies'] and current is not None and s['k'] - screens[current[1]]['k'] <= GAP:
            current[1] = i
        elif s['enemies']:
            current = [i, i]
            found.append(current)
        else:
            current = None
    return found


def main(folders):
    counts = collections.Counter()
    hurt = []
    for folder in folders:
        for path in sorted(glob.glob(folder + '/*.debug.ndjson')):
            screens, end = load(path)
            if not end or end.get('cause') != 'DEATH' or end['depth'] > 4 or not screens:
                continue
            counts['deaths on depths 1 to 4'] += 1
            found = fights(screens)
            if not found or found[-1][1] < len(screens) - 4:
                counts['no fight at the end (hunger, poison, a fall)'] += 1
                continue
            first, _ = found[-1]
            s = screens[first]
            counts['fatal fight found'] += 1
            if s['hp'] * 3 >= s['ht']:
                continue
            food = any(p['kind'] == 'FOOD' for p in s['pack'])
            earlier = found[-2] if len(found) > 1 else None
            gap = s['k'] - screens[earlier[1]]['k'] if earlier else None
            hurt.append({
                'hunger': s['hunger'], 'food': food, 'killer': end.get('killer'),
                'near': min(e['dist'] for e in s['enemies']),
                'after_fight': earlier is not None and gap <= 30,
                'before': re.sub(r'[0-9]+', 'N', screens[first - 1]['goal']) if first else '',
            })
    for key, value in counts.items():
        print(key, value)
    print('begin under a third of health', len(hurt))
    hungry = [r for r in hurt if r['hunger'] != 'NONE']
    print('  hungry or starving, no food in the pack', sum(1 for r in hungry if not r['food']))
    print('  hungry or starving, food in the pack', sum(1 for r in hungry if r['food']))
    print('  not hungry', sum(1 for r in hurt if r['hunger'] == 'NONE'))
    print('  starving, no food', sum(1 for r in hurt if r['hunger'] == 'STARVING' and not r['food']))
    print('  within 30 Decisions after another fight', sum(1 for r in hurt if r['after_fight']))
    print('  nearest enemy 1/2/3/4/5+ cells', [sum(1 for r in hurt if min(r['near'], 5) == d) for d in range(1, 6)])
    print('  killers', collections.Counter(r['killer'] for r in hurt).most_common(6))
    print('  not hungry, killers', collections.Counter(r['killer'] for r in hurt if r['hunger'] == 'NONE').most_common(6))
    print('  not hungry, doing before', collections.Counter(r['before'] for r in hurt if r['hunger'] == 'NONE').most_common(5))


if __name__ == '__main__':
    main(sys.argv[1:])

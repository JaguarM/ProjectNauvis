"""Holds the deliberately-duplicated packages to being actually identical.

Non-negotiable #3 says a subsystem mod never depends on another subsystem mod: shared code either
belongs in Facrafting or it gets copied. The multiblock framework was copied, on the same argument
that keeps the GUI palettes copied - a shared base in Facrafting would make these mods *require*
Facrafting and kill the `*_standalone` recipes that exist for its absence.

Copying is a fine answer right up until the copies drift, and drift is silent: every mod still
compiles, every test still passes, and two machines quietly disagree about what a quarter turn
means. So the copies are checked, the way `checkRecipes` checks generated recipes.

**One mod is the original and the rest are copies.** Fix a bug in the original and re-run with
`--sync` to push it out; there is no merge and there is not meant to be. If a copy needs to differ,
that is a sign the thing does not belong in a duplicated package at all.

Only the `package` line may differ, and it must differ. Everything else - including every import -
has to match byte for byte, which is why the framework imports nothing from its own mod.

    python tools/check_duplicated.py           # report, non-zero if they disagree
    python tools/check_duplicated.py --sync     # rewrite the copies from the original
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# (package path under src/main/java, the mod that owns the original, the mods holding copies)
DUPLICATED = [
    ('com/jaguarm/{pkg}/multiblock',
     ('nauvis_machines', 'nauvismachines'),
     [('nauvis_power', 'nauvispower'),
      ('../NeoProgressiveAutomation', 'neoprogressiveautomation')]),
]

PACKAGE = re.compile(r'^package\s+[\w.]+;', re.MULTILINE)


def package_dir(mod, pkg, template):
    return ROOT / mod / 'src' / 'main' / 'java' / template.format(pkg=pkg)


def body(path, package):
    """The file with its package declaration blanked, which is the only line allowed to differ."""
    text = path.read_text(encoding='utf-8').replace('\r\n', '\n')
    declared = PACKAGE.search(text)
    if declared is None:
        return None, f'{path.name} has no package declaration'
    if declared.group(0) != f'package {package};':
        return None, (f'{path.name} says "{declared.group(0)}" and belongs to {package} - a copy '
                      f'in the wrong package compiles and is still wrong')
    return PACKAGE.sub('package;', text), None


def main(sync):
    failures = []
    checked = 0

    for template, (origin_mod, origin_pkg), copies in DUPLICATED:
        origin_dir = package_dir(origin_mod, origin_pkg, template)
        origin_files = sorted(origin_dir.glob('*.java'))
        if not origin_files:
            sys.exit(f'no original found in {origin_dir}')

        for copy_mod, copy_pkg in copies:
            copy_dir = package_dir(copy_mod, copy_pkg, template)
            where = f'{copy_mod}/{template.format(pkg=copy_pkg)}'

            # A sibling repo that is not checked out beside this one. The composite build needs
            # it and would have failed long before here, so this is a clone that is not building
            # rather than drift - say so and move on rather than failing a check about copies.
            if not (ROOT / copy_mod / 'src' / 'main' / 'java').is_dir():
                print(f'  {copy_mod} is not checked out here - its copy is unchecked')
                continue

            extra = {path.name for path in copy_dir.glob('*.java')}
            extra -= {path.name for path in origin_files}
            for name in sorted(extra):
                failures.append(f'  {where}/{name} is in the copy and not in {origin_mod}')

            for source in origin_files:
                target = copy_dir / source.name
                wanted = source.read_text(encoding='utf-8').replace('\r\n', '\n')
                wanted = PACKAGE.sub(
                    f'package {template.format(pkg=copy_pkg).replace("/", ".")};', wanted)

                if sync:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_text(wanted, encoding='utf-8')
                    continue

                if not target.is_file():
                    failures.append(f'  {where}/{source.name} is missing')
                    continue

                checked += 1
                origin_body, problem = body(source, template.format(pkg=origin_pkg).replace('/', '.'))
                if problem:
                    failures.append(f'  {problem}')
                    continue
                copy_body, problem = body(target, template.format(pkg=copy_pkg).replace('/', '.'))
                if problem:
                    failures.append(f'  {problem}')
                    continue

                if origin_body != copy_body:
                    line = first_difference(origin_body, copy_body)
                    failures.append(
                        f'  {where}/{source.name} has drifted from {origin_mod} at line {line}. '
                        f'Fix it in {origin_mod} and run --sync; the copies are not edited.')

    if sync:
        print('copies rewritten from the original')
        return 0

    print(f'{checked} duplicated files checked')
    if failures:
        print('\nDRIFTED:')
        print('\n'.join(failures))
        return 1
    print('\nevery copy is identical')
    return 0


def first_difference(a, b):
    left, right = a.split('\n'), b.split('\n')
    for index, (one, other) in enumerate(zip(left, right), start=1):
        if one != other:
            return index
    return min(len(left), len(right)) + 1


sys.exit(main('--sync' in sys.argv))

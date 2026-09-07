"""Checks that every gametest's type is registered, which nothing else notices.

A gametest in 26.2 is two registrations, not one. The *instance* is registered from
`RegisterGameTestsEvent` and is what runs; the *type* is a `MapCodec` in
`Registries.TEST_INSTANCE_TYPE`, registered through a `DeferredRegister`, and exists so a test
could be written in a datapack. Ours never are, so the type looks like dead paperwork.

It is not. Miss one and:

  - the gametest server still runs the test, and still passes it;
  - `./gradlew build` is green;
  - and a **client** logs `Failed to serialize ResourceKey[minecraft:test_instance / ...]:
    Unregistered holder`, because the instance registry is synced to the client and one entry has
    no codec to write itself with.

That is as quiet as this class of bug gets: the check that exists to catch mistakes is itself the
thing reporting success. It happened here - two test types went missing from a static block
because an edit matched its anchor once and silently did nothing the next two times, and the
three tests kept passing for a day.

The invariant is per *class*, not per name: a test id and a type id live in different registries
and are only equal by convention - `nauvis:pack_loads` is registered under the type
`nauvis:registry_presence` and is perfectly correct. What must hold is that every
`GameTestInstance` the mod actually registers has had its `CODEC` handed to `TEST_TYPES`.

    python tools/check_gametests.py

Source is read, nothing is run: this is `check_gui_layout.py`'s trick, applied to registrations.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Every mod with tests, this repo's and the siblings'. A sibling that is not checked out is
# skipped and said so - the composite build would have failed long before here.
SEARCH = ['nauvis', 'nauvis_machines', 'nauvis_logistics', 'nauvis_fluids', 'nauvis_power',
          'nauvis_research', 'nauvis_mining', 'nauvis_military']

failures = []
checked = 0
notes = []

for mod in SEARCH:
    source = ROOT / mod / 'src' / 'main' / 'java'
    if not source.is_dir():
        notes.append(f'{mod} is not checked out here - its tests are unchecked')
        continue

    for path in sorted(source.rglob('*GameTests.java')):
        text = path.read_text(encoding='utf-8')

        declared = set(re.findall(r'class (\w+) extends GameTestInstance', text))
        if not declared:
            continue

        # Handed to the type registry, however the call is spelled or wrapped.
        typed = set(re.findall(r'TEST_TYPES\.register\([^;]*?(\w+)\.CODEC', text, re.S))

        # Actually registered as something to run: a constructor reference or a plain new.
        used = (set(re.findall(r'(\w+)::new', text)) | set(re.findall(r'new (\w+)\(', text)))
        used &= declared

        checked += len(used)
        for name in sorted(used - typed):
            failures.append(
                f'  {path.name}: {name} is registered to run and its CODEC is not in TEST_TYPES. '
                f'The test will pass and a client will fail to serialise it.')

        for name in sorted(declared - used):
            failures.append(
                f'  {path.name}: {name} is written and never registered to run, so it is not a '
                f'test - either register it or delete it.')

print(f'{checked} gametests checked')
for note in notes:
    print(f'  {note}')

if failures:
    print('\nUNREGISTERED:')
    print('\n'.join(failures))
    sys.exit(1)
print('\nevery gametest is registered both ways')

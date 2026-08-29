#!/usr/bin/env python3
"""
Fetch Factorio's technology tree from Wube's own prototype data.

    wube/factorio-data @ 0.16.51  ->  reference/factorio/technologies.json

`reference/factorio/recipes.json` is the spec for every recipe in the pack and it has no
technologies in it, so a research cost cannot be derived from anything the repo holds. It must
not come from the model's memory of Factorio either - that is exactly what the recipe dump
exists to prevent - so it comes from the same place the game does: `base/prototypes/technology`
in the data repository Wube publishes for mod authors.

**The version is not a preference.** `recipes.json` names `science-pack-1`, `science-pack-2`,
`science-pack-3`, `high-tech-science-pack`, `iron-axe` and `logistic-chest-active-provider`,
every one of which was renamed or removed in 0.17. It is a 0.16 dump, so the tree has to be
0.16 as well, or half the technologies would ask for packs that do not exist and unlock recipes
under names nothing in the pack uses. {@link VERSION} is pinned for that reason and changing it
means changing `recipes.json` in the same commit.

What lands in `technologies.json` is a transcription and nothing more: the prototype's own
`name`, `prerequisites`, `unit` and `effects`, plus the `order` string the technology screen
sorts on. Nothing is renamed, nothing is dropped, nothing is computed. Deciding which of those
technologies the pack ships, and what a Minecraft recipe id for them is, belongs to
`gen_technologies.py`, the same way `gen_recipes.py` owns the recipe half.

**Display names are not in the source.** Wube's data repository carries no locale files at any
tag, so `technology-name.steel-processing` resolves to nothing here. The dump therefore has no
name field and does not invent one; the generator derives an English string from the id, says so,
and ships it as a translation *fallback* so a language file can override it.

Usage:
    python tools/fetch_technologies.py            fetch and write the dump
    python tools/fetch_technologies.py --stdout   print it instead, to look before writing
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "reference" / "factorio" / "technologies.json"

# The Factorio version this pack's recipe dump is from. See the module docstring: this is
# pinned to `recipes.json` and the two move together or not at all.
VERSION = "0.16.51"

SOURCE = "https://raw.githubusercontent.com/wube/factorio-data/{version}/base/{path}"

# Both files `base/data.lua` requires under `prototypes.technology`. Two of them, and the
# second is easy to miss: `inserter.lua` holds the stack inserter and its capacity bonuses.
FILES = ("prototypes/technology/technology.lua", "prototypes/technology/inserter.lua")

# What a transcribed technology keeps. Everything else in the prototype - icons, sprite sizes,
# `localised_description` - is presentation for a game we are not running.
KEEP = ("prerequisites", "unit", "effects", "order", "upgrade", "max_level", "level")


class FetchError(Exception):
    """A fault in the source or the parse. Always fatal: a wrong tree is worse than no tree."""


class Unreadable(str):
    """
    A prototype the reader will not guess at, carrying the source text that produced it.

    Subclasses `str` so it survives being put in a list beside real values, and is checked for
    by type rather than by content.
    """


# --------------------------------------------------------------------------- the Lua reader

class LuaReader:
    """
    Enough of Lua to read a prototype file, and deliberately no more.

    Factorio's prototype stage is a Lua program and the honest way to read it is to run it,
    which is what `KirkMcDonald/factorio-tools` does - and it needs an installed copy of the
    game to do so. The technology files are not programs, though: they are two `data:extend`
    calls over table literals, so a reader for literals gets the whole tree with no game and
    no interpreter.

    That is a bet, and it is one this class is built to lose loudly. Anything outside the
    subset - a function call, a variable, an arithmetic expression, a `require` - raises
    rather than being skipped, so the day a prototype file stops being literals it says so
    instead of silently dropping a technology.
    """

    def __init__(self, text: str, where: str):
        self.text = text
        self.where = where
        self.pos = 0

    def fail(self, message: str) -> FetchError:
        line = self.text.count("\n", 0, self.pos) + 1
        return FetchError(f"{self.where}:{line}: {message}")

    # -- lexing ------------------------------------------------------------------------

    def skip_blanks(self) -> None:
        """Whitespace, line comments, and `--[[ block comments ]]`."""
        while self.pos < len(self.text):
            ch = self.text[self.pos]
            if ch in " \t\r\n":
                self.pos += 1
            elif self.text.startswith("--", self.pos):
                self.pos += 2
                if self.long_bracket_level() is not None:
                    self.read_long_bracket()
                else:
                    end = self.text.find("\n", self.pos)
                    self.pos = len(self.text) if end < 0 else end + 1
            else:
                return

    def long_bracket_level(self) -> int | None:
        """`[[` is level 0, `[==[` is level 2. None when this is not a long bracket."""
        if self.pos >= len(self.text) or self.text[self.pos] != "[":
            return None
        level = 0
        while self.text.startswith("=", self.pos + 1 + level):
            level += 1
        return level if self.text.startswith("[", self.pos + 1 + level) else None

    def read_long_bracket(self) -> str:
        level = self.long_bracket_level()
        if level is None:
            raise self.fail("expected a long bracket")
        self.pos += 2 + level
        closing = "]" + "=" * level + "]"
        end = self.text.find(closing, self.pos)
        if end < 0:
            raise self.fail(f"unterminated {closing}")
        body = self.text[self.pos:end]
        self.pos = end + len(closing)
        return body

    def peek(self) -> str:
        self.skip_blanks()
        return self.text[self.pos] if self.pos < len(self.text) else ""

    def expect(self, ch: str) -> None:
        if self.peek() != ch:
            raise self.fail(f"expected {ch!r}, found {self.peek()!r}")
        self.pos += 1

    # -- parsing -----------------------------------------------------------------------

    # Lua's own precedence, and only for the operators Factorio's data actually writes.
    # `60 * 60 * 60` is a real value in the tree - a ghost's lifetime in ticks, written the way
    # a person reads it - and folding it here is arithmetic on two literals, not evaluation of
    # anything the data stage would have to run.
    PRECEDENCE = {"+": 1, "-": 1, "*": 2, "/": 2, "%": 2, "^": 4}

    def read_value(self, min_precedence: int = 0):
        """A literal, and any arithmetic written over literals."""
        left = self.read_primary()
        while isinstance(left, (int, float)) and not isinstance(left, bool):
            operator = self.peek()
            precedence = self.PRECEDENCE.get(operator)
            if precedence is None or precedence < min_precedence:
                break
            # `--` is a comment, and skip_blanks has already eaten it; a lone `-` here is
            # subtraction. `^` is right-associative, everything else left.
            self.pos += 1
            right = self.read_value(precedence if operator == "^" else precedence + 1)
            if not isinstance(right, (int, float)) or isinstance(right, bool):
                raise self.fail(f"{operator!r} applied to something that is not a number")
            left = self.apply(operator, left, right)
        return left

    def apply(self, operator: str, left, right):
        if operator == "+":
            return left + right
        if operator == "-":
            return left - right
        if operator == "*":
            return left * right
        if operator == "%":
            return left % right
        if operator == "^":
            return left ** right
        if right == 0:
            raise self.fail("division by zero")
        # Lua's `/` is float division; keep a whole answer whole so the dump has no 3600.0 in it.
        quotient = left / right
        return int(quotient) if quotient == int(quotient) else quotient

    def read_primary(self):
        self.skip_blanks()
        if self.pos >= len(self.text):
            raise self.fail("unexpected end of file")

        ch = self.text[self.pos]
        if ch == "{":
            return self.read_table()
        if ch in "\"'":
            return self.read_string()
        if self.long_bracket_level() is not None:
            return self.read_long_bracket()
        if ch == "-" or ch.isdigit():
            return self.read_number()

        word = self.read_name()
        if word == "true":
            return True
        if word == "false":
            return False
        if word == "nil":
            return None
        if self.peek() == "(":
            # A call. `technology.lua` really does have one - `create_follower_upgrade` builds
            # the six follower-robot-count technologies in a loop of arguments - and reading it
            # would mean running Lua. It is named and counted rather than parsed, so what is
            # missing from the dump is a list a person can read, not a silence.
            return Unreadable(word + self.skip_balanced("(", ")"))
        # A bare identifier that is not a call is a variable, which means the same thing:
        # a value that only exists with the game's data stage running.
        raise self.fail(f"{word!r} is not a literal; this file is no longer plain data")

    def skip_balanced(self, opening: str, closing: str) -> str:
        """Consume a bracketed run and return its text, so a call can be reported verbatim."""
        start = self.pos
        depth = 0
        while self.pos < len(self.text):
            ch = self.text[self.pos]
            if ch in "\"'":
                self.read_string()
                continue
            if ch == opening:
                depth += 1
            elif ch == closing:
                depth -= 1
                if depth == 0:
                    self.pos += 1
                    return self.text[start:self.pos]
            self.pos += 1
        raise self.fail(f"unterminated {opening}")

    def read_name(self) -> str:
        start = self.pos
        while self.pos < len(self.text) and (self.text[self.pos].isalnum() or self.text[self.pos] == "_"):
            self.pos += 1
        if start == self.pos:
            raise self.fail(f"expected a name, found {self.text[self.pos]!r}")
        return self.text[start:self.pos]

    def read_number(self) -> float | int:
        start = self.pos
        if self.text[self.pos] == "-":
            self.pos += 1
        while self.pos < len(self.text) and (self.text[self.pos].isdigit() or self.text[self.pos] in ".eE+-"):
            # `+`/`-` only continue a number straight after an exponent marker.
            if self.text[self.pos] in "+-" and self.text[self.pos - 1] not in "eE":
                break
            self.pos += 1
        raw = self.text[start:self.pos]
        try:
            return int(raw)
        except ValueError:
            try:
                return float(raw)
            except ValueError:
                raise self.fail(f"{raw!r} is not a number") from None

    def read_string(self) -> str:
        quote = self.text[self.pos]
        self.pos += 1
        out = []
        while True:
            if self.pos >= len(self.text):
                raise self.fail("unterminated string")
            ch = self.text[self.pos]
            if ch == "\\":
                nxt = self.text[self.pos + 1]
                out.append({"n": "\n", "t": "\t", "r": "\r"}.get(nxt, nxt))
                self.pos += 2
            elif ch == quote:
                self.pos += 1
                return "".join(out)
            else:
                out.append(ch)
                self.pos += 1

    def read_table(self):
        """
        A Lua table is a list and a map at once, so this returns whichever it turned out to be.

        Factorio uses both and mixes them nowhere that matters: `effects` is a list, a
        technology is a map, and `ingredients` is a list of two-element lists.
        """
        self.expect("{")
        array: list = []
        table: dict = {}

        while True:
            if self.peek() == "}":
                self.pos += 1
                break
            if self.peek() == "":
                raise self.fail("unterminated table")

            key = None
            if self.peek() == "[":
                self.pos += 1
                key = self.read_value()
                self.expect("]")
                self.expect("=")
            else:
                mark = self.pos
                self.skip_blanks()
                if self.text[self.pos].isalpha() or self.text[self.pos] == "_":
                    name = self.read_name()
                    if self.peek() == "=" and not self.text.startswith("==", self.pos):
                        self.pos += 1
                        key = name
                    else:
                        # A bare word that is not a key: `true`, `false`, `nil` in a list.
                        self.pos = mark

            value = self.read_value()
            if key is None:
                array.append(value)
            else:
                table[key] = value

            if self.peek() in ",;":
                self.pos += 1

        if table and array:
            raise self.fail("a table with both list and map parts; the reader does not model that")
        return table if table or not array else array


def extend_calls(text: str, where: str) -> list:
    """Every `data:extend{...}` in a file, flattened into one list of prototypes."""
    out: list = []
    needle = "data:extend"
    at = text.find(needle)
    if at < 0:
        raise FetchError(f"{where}: no data:extend call; this is not a prototype file")

    while at >= 0:
        reader = LuaReader(text, where)
        reader.pos = at + len(needle)
        # `data:extend{...}` and `data:extend({...})` are both written in Factorio's own files.
        parenthesised = reader.peek() == "("
        if parenthesised:
            reader.pos += 1
        value = reader.read_value()
        if parenthesised:
            reader.expect(")")
        if not isinstance(value, list):
            raise FetchError(f"{where}: data:extend was handed a map, not a list of prototypes")
        out.extend(value)
        at = text.find(needle, reader.pos)

    return out


# ------------------------------------------------------------------------- transcription

def transcribe(prototype: dict, where: str) -> dict:
    """One prototype table -> one dump entry. Renames nothing and computes nothing."""
    name = prototype.get("name")
    if not name:
        raise FetchError(f"{where}: a technology prototype with no name")

    entry = {"id": name}
    for field in KEEP:
        if field in prototype:
            entry[field] = prototype[field]

    unit = entry.get("unit")
    if not isinstance(unit, dict):
        raise FetchError(f"{name}: no `unit`, so there is no cost to research it")
    if "count" not in unit and "count_formula" not in unit:
        raise FetchError(f"{name}: a unit with neither `count` nor `count_formula`")

    # Factorio writes a unit ingredient as `{"science-pack-1", 1}`, which is a two-element
    # list. Naming the two halves here is the one shape change in the whole file, and it is
    # made because every reader downstream would otherwise have to know the convention.
    packs = []
    for ingredient in unit.get("ingredients", []):
        if isinstance(ingredient, list) and len(ingredient) == 2:
            packs.append({"id": ingredient[0], "amount": ingredient[1]})
        elif isinstance(ingredient, dict) and "name" in ingredient:
            packs.append({"id": ingredient["name"], "amount": ingredient.get("amount", 1)})
        else:
            raise FetchError(f"{name}: cannot read the unit ingredient {ingredient!r}")
    unit["ingredients"] = packs

    return entry


def fetch(path: str) -> str:
    url = SOURCE.format(version=VERSION, path=path)
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            if response.status != 200:
                raise FetchError(f"{url} answered {response.status}")
            return response.read().decode("utf-8")
    except OSError as e:
        raise FetchError(f"{url}: {e}") from None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--stdout", action="store_true", help="print the dump instead of writing it")
    args = ap.parse_args()

    try:
        entries = []
        unreadable = []
        for path in FILES:
            text = fetch(path)
            for prototype in extend_calls(text, path):
                if isinstance(prototype, Unreadable):
                    unreadable.append(str(prototype))
                    continue
                if not isinstance(prototype, dict):
                    raise FetchError(f"{path}: a prototype that is not a table")
                if prototype.get("type") != "technology":
                    raise FetchError(f"{path}: a {prototype.get('type')!r} prototype in a technology file")
                entries.append(transcribe(prototype, path))
    except FetchError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2

    seen = {}
    for entry in entries:
        if entry["id"] in seen:
            print(f"error: '{entry['id']}' appears twice", file=sys.stderr)
            return 2
        seen[entry["id"]] = entry

    entries.sort(key=lambda e: e["id"])
    rendered = json.dumps(entries, indent=4) + "\n"

    if args.stdout:
        print(rendered, end="")
        return 0

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"{len(entries)} technologies from factorio-data {VERSION} -> {OUT}")

    unlocks = sum(
        1 for e in entries for effect in e.get("effects", []) if effect.get("type") == "unlock-recipe"
    )
    infinite = [e["id"] for e in entries if e.get("max_level") == "infinite"]
    print(f"  unlock-recipe effects : {unlocks}")
    print(f"  infinite technologies : {len(infinite)}")
    if unreadable:
        print(f"  BUILT BY LUA, not in the dump : {len(unreadable)}")
        for call in unreadable:
            print(f"    - {call}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Works out the movement pattern of every seeded exercise, once, offline.

The app used to derive the pattern from the exercise's name at runtime, and a
name is a poor witness. "Straight Bar Bench Mid Rows" contains "bench", so it
was demonstrated as a bench press. "Leg Extension" contains "extension", so it
was demonstrated as a triceps extension. A lifter opening the demo saw the
wrong movement, which is worse than showing nothing.

Two things fix it. The first is this script: the pattern is decided here, where
it can be checked, rather than on a phone. The second is the evidence it uses.
free-exercise-db records `force` -- push, pull or static -- and `mechanic` for
every entry, and the seed generator was discarding both. Force alone settles
the cases the name got wrong: a row is a pull whatever else is in its name, and
a press is a push.

The rules below are ordered, most specific first, and every one of them is then
audited against the dataset's own force field. A disagreement is printed rather
than silently accepted, because a rule that contradicts the data is a rule with
a bug in it. Anything the rules genuinely cannot settle is listed in OVERRIDES,
by id, with a reason.

Run:  python tools/generate_movement_patterns.py exercises.json
      (dataset: https://github.com/yuhonas/free-exercise-db, public domain)
"""

import json
import re
import sys

SEED = "app/src/main/java/com/ironlog/app/data/seed/ExerciseSeed.kt"
OUT = "app/src/main/java/com/ironlog/app/data/seed/MovementPatterns.kt"

# Patterns that press a weight away from the body, and those that pull it
# towards it. Used to audit each decision against the dataset's force field.
#
# Some patterns sit in neither set, and deliberately. The dataset files hip
# extension as a push, so a good morning, a glute bridge and a kickback are all
# "push" while a deadlift -- the same joint doing the same thing -- is "pull".
# A pullover is filed as a push for the chest and a pull for the lats depending
# on the entry, and an adduction machine pulls where an abduction machine
# pushes. The force field cannot referee any of those, so it is not asked to.
PUSHES = {
    "SQUAT", "LUNGE", "HORIZONTAL_PRESS", "VERTICAL_PRESS", "EXTENSION",
    "LATERAL_RAISE", "CALF_RAISE", "LEG_EXTENSION",
}
PULLS = {
    "HORIZONTAL_PULL", "VERTICAL_PULL", "CURL", "SHRUG", "LEG_CURL", "UPRIGHT_ROW",
}

# Where the dataset's force field is simply wrong, checked against that same
# entry's own instructions. Listed rather than ignored: an unexplained silence
# is how a bad rule survives an audit.
FORCE_IS_WRONG = {
    # Filed as a pull, but its instructions read "use your side shoulders to
    # lift the dumbbells", which is a lateral raise.
    "fx-dumbbell-raise",
}

# Decided by hand, because the movement is not what its name or its force
# field would suggest. Each one is a judgement, so each one carries a reason.
OVERRIDES = {
    # A jerk is caught by the Olympic rule, but the bar finishes overhead:
    # what a lifter needs to see is the press, not the dip.
    "fx-split-jerk": ("VERTICAL_PRESS", "the lift a jerk is, once the bar leaves the shoulders"),
    "fx-jerk-balance": ("VERTICAL_PRESS", "a jerk drilled without the dip"),
    "fx-clean-and-press": ("VERTICAL_PRESS", "the press is the half a lifter needs to see"),
    # An overhead squat under a snatch grip, not a pull from the floor.
    "fx-snatch-balance": ("SQUAT", "a squat received under a fixed overhead bar"),
    # Filed under glutes, but the trunk is what moves and what is loaded.
    "fx-flutter-kicks": ("CORE", "the trunk holds while the legs move"),
    "fx-leg-lift": ("CORE", "a trunk movement filed under glutes"),
    # A chin-up with a knee tuck: the pull is the hard half.
    "fx-gorilla-chin-crunch": ("VERTICAL_PULL", "a chin-up carrying a knee tuck"),
    # Kneeling, so the hips extend rather than the knees bending under load.
    "fx-kneeling-squat": ("HINGE", "kneeling, so the hips do the work"),
    "fx-kneeling-jump-squat": ("HINGE", "kneeling, so the hips do the work"),
}

# Ordered rules. The first whose test passes decides the pattern, so the most
# specific phrases have to come first: "calf press" is a calf raise and not a
# press, "leg curl" is a knee curl and not a biceps one.
def classify(name, muscle, category, force):
    n = name.lower()

    def has(*words):
        return any(w in n for w in words)

    def word(*words):
        tokens = re.split(r"[^a-z0-9]+", n)
        # Plurals count: "Elevated Cable Rows" is as much a row as "Cable Row".
        return any(w in tokens or w + "s" in tokens or w + "es" in tokens for w in words)

    if category == "CARDIO":
        return "CARDIO"

    # --- movements whose name contains a word that means something else ---
    if has("calf", "heel raise"):
        return "CALF_RAISE"
    if has("shrug"):
        return "SHRUG"
    if has("leg curl", "hamstring curl", "lying curl"):
        return "LEG_CURL"
    if has("leg extension", "knee extension"):
        return "LEG_EXTENSION"
    if has("pullover"):
        return "PULLOVER"
    # Whole words: "high pull" sits inside "Kneeling High Pulley Row", which
    # is a row off a high pulley and not a high pull at all.
    if has("upright row", "sumo high") or re.search(r"high pulls?", n):
        return "UPRIGHT_ROW"
    if has("abduct", "adduct"):
        return "HIP_ABDUCTION"
    if has("back extension", "hyperextension", "hyper extension"):
        return "HINGE"
    if has("kickback"):
        return "EXTENSION" if muscle == "TRICEPS" else "HINGE"

    # --- the legs the trunk rules would otherwise claim ---
    if has("lunge", "split squat", "step up", "step-up"):
        return "LUNGE"

    # --- the trunk ---
    if has("rollout", "roll-out", "ab wheel"):
        return "CORE"
    if has("crunch", "sit-up", "sit up", "situp", "twist", "leg raise", "knee raise",
           "jackknife", "flutter", "leg lift", "leg pull", "judo flip", "cable lift",
           "elbow to knee", "hip raise", "woodchop", "v-up"):
        return "CORE"
    if has("plank"):
        return "CARRY"

    # --- the Olympic lifts, before "hang" reads as a hold and "clean" as a
    # grip description ---
    if word("clean", "snatch", "jerk"):
        return "HINGE"

    if word("carry", "hold", "hang", "farmer", "farmers") or has("farmer's"):
        return "CARRY"

    # --- the arms ---
    if has("curl"):
        return "CURL"
    if has("pushdown", "push-down", "skull", "jm press", "triceps extension",
           "tricep extension", "overhead extension", "french press"):
        return "EXTENSION"

    # --- shoulders and chest openers ---
    # A rear delt row is a row; only the fly version is a raise, so anything
    # naming itself a row is left for the back rules below.
    if not word("row"):
        if has("fly", "flye", "around the world", "rear delt", "lateral raise",
               "front raise", "side raise", "shoulder raise"):
            return "LATERAL_RAISE"
        if has("raise") and not has("calf", "leg raise", "knee raise", "hip raise"):
            return "LATERAL_RAISE"

    # --- the legs ---
    if has("squat", "leg press", "hack"):
        return "SQUAT"
    if has("deadlift", "good morning", "hinge", "swing", "hip thrust", "bridge",
           "rdl", "rack pull", "pull through", "pull-through", "stiff leg",
           "stiff-leg", "butt lift", "back raise"):
        return "HINGE"

    # --- the back ---
    if word("row") or has("face pull"):
        return "HORIZONTAL_PULL"
    if has("pulldown", "pull-down", "pullup", "pull-up", "pull up") or word("chin", "chinup"):
        return "VERTICAL_PULL"

    # --- pressing ---
    if has("overhead press", "shoulder press", "military", "push press", "landmine press",
           "jammer", "arnold", "bradford", "cuban press", "overhead"):
        return "VERTICAL_PRESS"
    if has("bench", "chest press", "push up", "push-up", "pushup", "floor press",
           "pin press", "board press", "neck press") or word("dip", "dips"):
        return "HORIZONTAL_PRESS"
    if has("press"):
        return "VERTICAL_PRESS" if muscle == "SHOULDERS" else "HORIZONTAL_PRESS"
    if has("pull"):
        return "VERTICAL_PULL"

    # Nothing in the name settled it. Force narrows it before the muscle does:
    # a pull on a back exercise is a row, a push on one is not.
    by_muscle = {
        "CHEST": "HORIZONTAL_PRESS", "SHOULDERS": "VERTICAL_PRESS",
        "BACK": "HORIZONTAL_PULL", "TRAPS": "SHRUG", "LATS": "VERTICAL_PULL",
        "BICEPS": "CURL", "TRICEPS": "EXTENSION", "FOREARMS": "CURL",
        "QUADS": "SQUAT", "HAMSTRINGS": "LEG_CURL", "GLUTES": "HINGE",
        "CALVES": "CALF_RAISE", "ABS": "CORE", "FULL_BODY": "HINGE",
        "CARDIO": "CARDIO",
    }
    return by_muscle.get(muscle, "HINGE")


def seed_rows():
    """Every seeded exercise, read back out of the Kotlin the app ships."""
    text = open(SEED, encoding="utf-8").read()
    rows = []
    for match in re.finditer(
        r'exercise\(\s*"([^"]+)",\s*"((?:[^"\\]|\\.)*)",\s*MuscleGroup\.(\w+),\s*'
        r'ExerciseCategory\.(\w+)', text
    ):
        rows.append({
            "id": match.group(1),
            "name": match.group(2).replace('\\"', '"'),
            "muscle": match.group(3),
            "category": match.group(4),
        })
    return rows


def main(dataset_path):
    dataset = {}
    for entry in json.load(open(dataset_path, encoding="utf-8")):
        slug = re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", entry["name"].lower())).strip("-")
        dataset[slug] = entry

    rows = seed_rows()
    decided = []
    disagreements = []

    for row in rows:
        source = dataset.get(row["id"][3:]) if row["id"].startswith("fx-") else None
        force = (source or {}).get("force")

        if row["id"] in OVERRIDES:
            pattern, reason = OVERRIDES[row["id"]]
            decided.append((row, pattern, reason))
            continue

        pattern = classify(row["name"], row["muscle"], row["category"], force)
        decided.append((row, pattern, None))

        # The audit: a rule that calls a documented pull a push has a bug.
        if row["id"] in FORCE_IS_WRONG:
            pass
        elif force == "push" and pattern in PULLS:
            disagreements.append((row["name"], pattern, force))
        elif force == "pull" and pattern in PUSHES:
            disagreements.append((row["name"], pattern, force))

    for name, pattern, force in disagreements:
        print(f"DISAGREES  {name}: rules say {pattern}, dataset says {force}", file=sys.stderr)
    print(f"{len(rows)} exercises, {len(disagreements)} disagreements", file=sys.stderr)

    body = []
    for row, pattern, reason in sorted(decided, key=lambda d: d[0]["id"]):
        comment = f"  // {reason}\n" if reason else ""
        body.append(f'{comment}        "{row["id"]}" to MovementPattern.{pattern},')

    return TEMPLATE.format(rows="\n".join(body).rstrip(","))


TEMPLATE = '''package com.ironlog.app.data.seed

import com.ironlog.app.domain.usecase.MovementPattern

/**
 * The movement each seeded exercise actually is.
 *
 * Generated by tools/generate_movement_patterns.py, decided once against
 * free-exercise-db's own `force` field rather than guessed from the name on a
 * phone. A name is a poor witness: "Straight Bar Bench Mid Rows" contains
 * "bench" and "Leg Extension" contains "extension", and both were demonstrated
 * as the wrong lift entirely because of it.
 *
 * Only the shipped library is listed. An exercise the user wrote themselves,
 * or imported from wger, has no entry here and falls back to reading its name,
 * which is all there is to go on for a movement nobody has classified.
 */
object MovementPatterns {{

    val byExerciseId: Map<String, MovementPattern> = mapOf(
{rows}
    )
}}
'''


if __name__ == "__main__":
    sys.stdout.write(main(sys.argv[1]))

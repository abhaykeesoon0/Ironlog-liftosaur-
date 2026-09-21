#!/usr/bin/env python3
"""Generates the imported half of the exercise library.

Source: free-exercise-db (https://github.com/yuhonas/free-exercise-db), 876
exercises released into the public domain under the Unlicense. Public domain
matters here: the library ships inside the app, so anything with an attribution
or share-alike clause would put an obligation on every copy of it.

Only the movements a gym-goer actually performs are taken. The dataset also
carries stretches, foam-rolling and rehab drills, which are not sets to be
logged, and a long tail of near-duplicates that would make the picker unusable.
Selection is balanced per muscle so the library does not end up as 40 curls and
four back exercises.

The output is Kotlin, not JSON parsed at runtime: the rows are fixed content,
and compiling them means a typo is a build error rather than a crash on a
phone. Ids are slugs prefixed with `fx-` so an imported row can never collide
with a hand-written one.

Run:  python tools/generate_exercise_seed.py exercises.json
"""

import json
import re
import sys
from collections import defaultdict

# Dataset muscle names to the app's MuscleGroup. The app models what a lifter
# programmes around, so the dataset's finer distinctions collapse: the two
# back entries both become BACK, and the hip muscles fold into GLUTES.
MUSCLES = {
    "quadriceps": "QUADS",
    "hamstrings": "HAMSTRINGS",
    "abdominals": "ABS",
    "chest": "CHEST",
    "shoulders": "SHOULDERS",
    "triceps": "TRICEPS",
    "biceps": "BICEPS",
    "lats": "LATS",
    "middle back": "BACK",
    "lower back": "BACK",
    "calves": "CALVES",
    "forearms": "FOREARMS",
    "glutes": "GLUTES",
    "traps": "TRAPS",
    "adductors": "GLUTES",
    "abductors": "GLUTES",
    "neck": "TRAPS",
}

EQUIPMENT = {
    "barbell": "BARBELL",
    "dumbbell": "DUMBBELL",
    "machine": "MACHINE",
    "cable": "CABLE",
    "body only": "BODYWEIGHT",
    "kettlebells": "DUMBBELL",   # logged the same way: a weight in each hand
    "e-z curl bar": "BARBELL",
}

# Everything else in the dataset -- bands, medicine balls, foam rollers -- is
# either not a loaded lift or not something this app can track properly.
KEEP_CATEGORIES = {"strength", "olympic weightlifting", "powerlifting", "strongman"}

# How many to take per primary muscle, roughly in proportion to how much of a
# training week each one occupies.
QUOTA = {
    "QUADS": 18,
    "CHEST": 16,
    "BACK": 14,
    "SHOULDERS": 16,
    "HAMSTRINGS": 12,
    "BICEPS": 12,
    "TRICEPS": 12,
    "LATS": 12,
    "ABS": 14,
    "GLUTES": 10,
    "CALVES": 6,
    "TRAPS": 6,
    "FOREARMS": 6,
}

# Names that read as rehab, warm-up or novelty rather than a working set.
REJECT = re.compile(
    # Not a working set: rehab, warm-up and mobility work.
    r"stretch|foam roll|mobility|warm[- ]up|rehab|massage|prone manual|isometric neck|"
    # Accommodating resistance: a real method, but it needs equipment almost no
    # commercial gym has, and it clutters the picker with near-duplicate lifts.
    r"with bands|with chains|band |bosu|"
    # Novelty or trade names that tell a lifter nothing about the movement.
    r"cocoon|spell caster|wind sprint|bottoms up|bottoms-up|butt-up|spider crawl|"
    r"landmine 180|rack delivery|anti-gravity|jerk dip|iron cross|scissor kick|"
    r"guillotine|rocking standing|lying face|off pins|air bike|dead bug",
    re.I,
)

BAR_BY_EQUIPMENT = {"barbell": "OLYMPIC_BAR", "e-z curl bar": "EZ_BAR"}


def slug(text):
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", text.lower())).strip("-")


def kotlin_string(text):
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def instructions_for(entry):
    """The first two steps: enough to perform the lift, short enough to read."""
    steps = [s.strip() for s in entry.get("instructions", []) if s.strip()]
    text = " ".join(steps[:2])
    return text[:400]


def score(entry):
    """Prefers the lifts most people actually programme."""
    points = 0
    if entry.get("mechanic") == "compound":
        points += 3
    if entry.get("level") in ("beginner", "intermediate"):
        points += 2
    if entry.get("equipment") in ("barbell", "dumbbell"):
        points += 2
    if entry.get("equipment") in ("machine", "cable", "body only"):
        points += 1
    # Shorter names tend to be the movement itself rather than a variation
    # of a variation ("Squat" over "Smith Machine Squat With Bands").
    points -= len(entry["name"]) / 40
    return points


def main(path, existing_names):
    data = json.load(open(path, encoding="utf-8"))

    by_muscle = defaultdict(list)
    for entry in data:
        if entry.get("category") not in KEEP_CATEGORIES:
            continue
        if entry.get("equipment") not in EQUIPMENT:
            continue
        if REJECT.search(entry["name"]):
            continue
        primary = entry.get("primaryMuscles") or []
        if not primary or primary[0] not in MUSCLES:
            continue
        muscle = MUSCLES[primary[0]]
        if muscle not in QUOTA:
            continue
        # Skip anything the hand-written seed already covers, comparing on a
        # normalised name so "Bench Press (Barbell)" matches "Barbell Bench Press".
        if normalise(entry["name"]) in existing_names:
            continue
        by_muscle[muscle].append(entry)

    chosen = []
    # One shared set: the same movement filed under two muscles is still the
    # same movement, and two entries for it would be two ways to log one lift.
    seen = set()
    for muscle, quota in QUOTA.items():
        ranked = sorted(by_muscle.get(muscle, []), key=score, reverse=True)
        taken = 0
        for entry in ranked:
            if taken >= quota:
                break
            key = normalise(entry["name"])
            if key in seen:
                continue
            seen.add(key)
            taken += 1
            chosen.append((muscle, entry))

    lines = []
    for muscle, entry in sorted(chosen, key=lambda pair: (pair[0], pair[1]["name"])):
        secondary = []
        for name in entry.get("secondaryMuscles", []):
            mapped = MUSCLES.get(name)
            if mapped and mapped != muscle and mapped not in secondary:
                secondary.append(mapped)
        category = EQUIPMENT[entry["equipment"]]
        bar = BAR_BY_EQUIPMENT.get(entry["equipment"])

        args = [
            kotlin_string("fx-" + slug(entry["name"])),
            kotlin_string(entry["name"]),
            f"MuscleGroup.{muscle}",
            f"ExerciseCategory.{category}",
        ]
        secondary_arg = (
            "listOf(" + ", ".join(f"MuscleGroup.{m}" for m in secondary[:3]) + ")"
            if secondary else "emptyList()"
        )
        args.append(secondary_arg)
        args.append(bar if bar else "null")
        args.append(kotlin_string(instructions_for(entry)))

        lines.append("        exercise(\n            " + ",\n            ".join(args) + "\n        )")

    print(f"// {len(chosen)} imported exercises", file=sys.stderr)
    counts = defaultdict(int)
    for muscle, _ in chosen:
        counts[muscle] += 1
    print(dict(counts), file=sys.stderr)
    return ",\n".join(lines)


def normalise(name):
    """
    A name reduced to the movement itself, for spotting duplicates.

    Everything after a dash and anything in brackets goes, because the dataset
    uses both for grip and stance notes: "Barbell Bench Press - Medium Grip" is
    the bench press the library already has, not a second exercise.
    """
    text = name.lower().split(" - ")[0]
    text = re.sub(r"\([^)]*\)", " ", text)
    for word in ("barbell", "dumbbell", "machine", "cable", "bodyweight", "weighted",
                 "with", "the", "grip", "medium", "using"):
        text = text.replace(word, " ")
    return re.sub(r"[^a-z]+", "", text)


if __name__ == "__main__":
    seed_path = "app/src/main/java/com/ironlog/app/data/seed/ExerciseSeed.kt"
    existing = set()
    try:
        for match in re.finditer(r'"[^"]*",\s*"([^"]+)"', open(seed_path, encoding="utf-8").read()):
            existing.add(normalise(match.group(1)))
    except FileNotFoundError:
        pass
    sys.stdout.write(main(sys.argv[1], existing))

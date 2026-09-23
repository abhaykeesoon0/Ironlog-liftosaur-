#!/usr/bin/env python3
"""Fetches the two demonstration photographs for every seeded exercise.

Source: free-exercise-db (https://github.com/yuhonas/free-exercise-db), which
publishes a start and an end photograph for almost every entry and states that
it is released under the Unlicense. Shown alternating, those two frames are the
demonstration: the bottom and the top of the repetition, which is what a lifter
needs to recognise a lift.

A note on provenance, recorded here rather than left to be discovered. The
repository states the Unlicense and the dataset it derives from says it "sits
within the public domain", but neither documents where the photographs
themselves came from, and they are plainly one studio's set. That was put to
the project owner, who chose to ship them on the strength of the repository's
stated licence. It is written down so the decision stays visible.

Images are re-encoded to WebP at a size a phone actually displays, which takes
the set from about 60MB of JPEG to about 6MB.

Run:  python tools/fetch_exercise_media.py exercises.json [--dry-run]
"""

import concurrent.futures
import difflib
import io
import json
import os
import re
import sys
import urllib.request

SEED = "app/src/main/java/com/ironlog/app/data/seed/ExerciseSeed.kt"
OUT_DIR = "app/src/main/assets/demo"
BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

# The width a phone shows the demonstration at, doubled for density.
WIDTH = 480
QUALITY = 78

# The hand-written half of the library uses our own names, which do not always
# resemble the dataset's. Matched by hand where a machine would guess wrong.
OVERRIDES = {
    "bench-press-barbell": "Barbell Bench Press - Medium Grip",
    "bench-press-incline-barbell": "Barbell Incline Bench Press - Medium Grip",
    "bench-press-dumbbell": "Dumbbell Bench Press",
    "bench-press-incline-dumbbell": "Incline Dumbbell Press",
    "chest-fly-dumbbell": "Dumbbell Flyes",
    "cable-fly-mid": "Cable Crossover",
    "chest-press-machine": "Leverage Chest Press",
    "push-up": "Pushups",
    "dip-chest": "Dips - Chest Version",
    "dip-triceps": "Dips - Triceps Version",
    "close-grip-bench": "Close-Grip Barbell Bench Press",
    "overhead-press-barbell": "Standing Military Press",
    "overhead-press-dumbbell": "Standing Dumbbell Press",
    "arnold-press": "Arnold Dumbbell Press",
    "lateral-raise-dumbbell": "Side Lateral Raise",
    "lateral-raise-cable": "Cable Seated Lateral Raise",
    "rear-delt-fly": "Reverse Flyes",
    "upright-row": "Upright Barbell Row",
    "face-pull": "Face Pull",
    "shrug-barbell": "Barbell Shrug",
    "shrug-dumbbell": "Dumbbell Shrug",
    "row-barbell": "Bent Over Barbell Row",
    "row-dumbbell": "One-Arm Dumbbell Row",
    "row-seated-cable": "Seated Cable Rows",
    "row-t-bar": "T-Bar Row with Handle",
    "lat-pulldown": "Wide-Grip Lat Pulldown",
    "pull-up": "Pullups",
    "chin-up": "Chin-Up",
    "deadlift-conventional": "Barbell Deadlift",
    "deadlift-romanian": "Romanian Deadlift",
    "hyperextension": "Hyperextensions (Back Extensions)",
    "squat-back": "Barbell Squat",
    "squat-front": "Front Barbell Squat",
    "squat-goblet": "Goblet Squat",
    "bulgarian-split-squat": "Split Squat with Dumbbells",
    "lunge-walking": "Barbell Walking Lunge",
    "leg-press": "Leg Press",
    "leg-extension": "Leg Extensions",
    "leg-curl-lying": "Lying Leg Curls",
    "leg-curl-seated": "Seated Leg Curl",
    "hip-thrust": "Barbell Hip Thrust",
    "glute-bridge": "Butt Lift (Bridge)",
    "hip-abduction": "Thigh Abductor",
    "calf-raise-standing": "Standing Calf Raises",
    "calf-raise-seated": "Seated Calf Raise",
    "curl-barbell": "Barbell Curl",
    "curl-dumbbell": "Dumbbell Bicep Curl",
    "curl-hammer": "Hammer Curls",
    "curl-preacher": "Preacher Curl",
    "curl-incline": "Incline Dumbbell Curl",
    "curl-cable": "Standing Biceps Cable Curl",
    "curl-ez-bar": "EZ-Bar Curl",
    "skullcrusher": "EZ-Bar Skullcrusher",
    "triceps-pushdown": "Triceps Pushdown",
    "triceps-overhead-cable": "Cable Rope Overhead Triceps Extension",
    "wrist-curl": "Palms-Up Barbell Wrist Curl Over A Bench",
    "farmers-carry": "Farmer's Walk",
    "plank": "Plank",
    "side-plank": "Side Bridge",
    "sit-up": "Sit-Up",
    "cable-crunch": "Cable Crunch",
    "russian-twist": "Russian Twist",
    "hanging-leg-raise": "Hanging Leg Raise",
    "ab-wheel": "Ab Roller",
}

# Cardio is logged by time and distance, and a photograph of a treadmill
# demonstrates nothing.
SKIP = re.compile(r"^(cycling|elliptical|jump-rope|run-outdoor|run-treadmill|rowing-machine|stair-climber)$")


def seed_rows():
    text = open(SEED, encoding="utf-8").read()
    return [
        {"id": m.group(1), "name": m.group(2).replace('\\"', '"'), "muscle": m.group(3)}
        for m in re.finditer(
            r'exercise\(\s*"([^"]+)",\s*"((?:[^"\\]|\\.)*)",\s*MuscleGroup\.(\w+)', text
        )
    ]


def normalise(text):
    return re.sub(r"[^a-z0-9 ]+", " ", text.lower()).strip()


def match(row, dataset, by_slug):
    if row["id"] in OVERRIDES:
        return dataset.get(OVERRIDES[row["id"]])
    if row["id"].startswith("fx-"):
        return by_slug.get(row["id"][3:])
    names = list(dataset)
    close = difflib.get_close_matches(normalise(row["name"]),
                                      [normalise(n) for n in names], n=1, cutoff=0.72)
    if not close:
        return None
    return dataset[names[[normalise(n) for n in names].index(close[0])]]


def download(url):
    request = urllib.request.Request(url, headers={"user-agent": "IronLog build tool"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def fetch_one(job):
    from PIL import Image
    exercise_id, images = job
    target = os.path.join(OUT_DIR, exercise_id)
    os.makedirs(target, exist_ok=True)
    for index, path in enumerate(images[:2]):
        out = os.path.join(target, f"{index}.webp")
        if os.path.exists(out):
            continue
        raw = download(BASE + urllib.parse.quote(path))
        image = Image.open(io.BytesIO(raw)).convert("RGB")
        image.thumbnail((WIDTH, WIDTH), Image.LANCZOS)
        image.save(out, "WEBP", quality=QUALITY, method=6)
    return exercise_id


def main(dataset_path, dry_run):
    data = json.load(open(dataset_path, encoding="utf-8"))
    dataset = {entry["name"]: entry for entry in data}
    by_slug = {
        re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", entry["name"].lower())).strip("-"): entry
        for entry in data
    }

    jobs, missing, guessed = [], [], []
    for row in seed_rows():
        if SKIP.match(row["id"]):
            continue
        entry = match(row, dataset, by_slug)
        if not entry or len(entry.get("images", [])) < 2:
            missing.append(row["name"])
            continue
        if not row["id"].startswith("fx-") and row["id"] not in OVERRIDES:
            guessed.append(f'{row["name"]}  ->  {entry["name"]}')
        jobs.append((row["id"], entry["images"]))

    for line in guessed:
        print("GUESSED   " + line)
    for name in missing:
        print("NO IMAGES " + name)
    print(f"{len(jobs)} exercises to fetch, {len(missing)} without images")

    if dry_run:
        return

    os.makedirs(OUT_DIR, exist_ok=True)
    done = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for _ in pool.map(fetch_one, jobs):
            done += 1
            if done % 25 == 0:
                print(f"  {done}/{len(jobs)}")
    size = sum(
        os.path.getsize(os.path.join(root, f))
        for root, _, files in os.walk(OUT_DIR) for f in files
    )
    print(f"{done} fetched, {size // 1024 // 1024}MB on disk")


if __name__ == "__main__":
    import urllib.parse
    main(sys.argv[1], "--dry-run" in sys.argv)

"""Score consenting pairs from a local Partner Week pilot CSV. Output contains totals only."""
import argparse
import csv
import re
from collections import Counter
from pathlib import Path


FIELDS = "pair_code invite mode first48_host first48_guest day14 mutual_start week2_host week2_guest".split()
ANSWERS = {"yes", "no", "unknown"}
INVITES = {"unsent", "pending", "accepted", "declined", "expired", "unknown"}


def read_pairs(path):
    with path.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames != FIELDS:
            raise ValueError("use the original scorecard header")
        seen = set()
        pairs = []
        for line, row in enumerate(reader, 2):
            def require(condition, message):
                if not condition:
                    raise ValueError(f"row {line}: {message}")

            require(None not in row and None not in row.values(), "incorrect number of fields")
            if not any(row.values()):
                continue
            code = row["pair_code"]
            require(re.fullmatch(r"P(?:0[1-9]|1[0-9]|20)", code), "use a code from P01 to P20")
            require(code not in seen, "duplicate pair code")
            seen.add(code)
            require(row["invite"] in INVITES, "invalid invitation outcome")
            if row["invite"] == "accepted":
                require(row["mode"] in {"participants", "supporter"}, "invalid accepted role")
                require(row["first48_host"] in ANSWERS and row["first48_guest"] in ANSWERS,
                        "record each first-48-hour response, including unknown")
                require(row["day14"] in {"pending", "due"}, "invalid follow-up window")
                week_two = [row[field] for field in FIELDS[-3:]]
                if row["day14"] == "pending":
                    require(not any(week_two), "week-two fields require the full observation window")
                else:
                    require(all(value in ANSWERS for value in week_two), "invalid week-two response")
                    if row["mutual_start"] == "no":
                        require(week_two[1:] == ["no", "no"], "use requires a renewed week")
            else:
                require(not any(row[key] for key in FIELDS[2:]), "unaccepted invites have no shared week")
            pairs.append(row)
    return pairs


def joint_answer(values):
    if "no" in values:
        return "no"
    if "unknown" in values:
        return "unknown"
    return "yes"


def score_mode(pairs, mode):
    accepted = [p for p in pairs if p["mode"] == mode]
    activation = Counter()
    returns = Counter()
    mutual_starts = Counter()
    due_activated = 0
    for pair in accepted:
        actions = [pair["first48_host"]]
        if mode == "participants":
            actions.append(pair["first48_guest"])
        active = joint_answer(actions)
        activation[active] += 1
        if active == "yes" and pair["day14"] == "due":
            due_activated += 1
            mutual_starts[pair["mutual_start"]] += 1
            returns[joint_answer([pair[f] for f in FIELDS[-3:]])] += 1

    title = "Two participants" if mode == "participants" else "Participant and supporter"
    print(f"\n{title}")
    print(f"  Activated in 48 hours: {activation['yes']}/{len(accepted)} accepted; unknown {activation['unknown']}")
    if mode == "supporter":
        guest = Counter(p["first48_guest"] for p in accepted)
        print(f"  Optional supporter return in 48 hours: {guest['yes']}/{len(accepted)} accepted; unknown {guest['unknown']}")
    print(f"  Accepted pairs awaiting day 14: {sum(p['day14'] == 'pending' for p in accepted)}")
    print(f"  Mutual week-two start: {mutual_starts['yes']}/{due_activated} activated and due; unknown {mutual_starts['unknown']}")
    print(f"  Mutual start and use by both: {returns['yes']}/{due_activated} activated and due; unknown {returns['unknown']}")
    if mode == "participants":
        if due_activated:
            cutoff = "met" if 2 * returns["yes"] >= due_activated else "below"
            print(f"  Numeric half-of-activated threshold: {cutoff} (see safety review and outstanding data)")
        else:
            print("  Numeric half-of-activated threshold: undefined (no activated pairs due)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("scorecard", type=Path)
    args = parser.parse_args()
    try:
        pairs = read_pairs(args.scorecard)
    except (OSError, ValueError, csv.Error) as error:
        parser.exit(2, f"Cannot score: {error}\n")
    invites = Counter(p["invite"] for p in pairs)
    print(f"Accepted invitations: {invites['accepted']}/{len(pairs) - invites['unsent']} unique sent")
    print("Other invitation outcomes: " + ", ".join(
        f"{key} {invites[key]}" for key in ("unsent", "pending", "declined", "expired", "unknown")))
    for mode in ("participants", "supporter"):
        score_mode(pairs, mode)


if __name__ == "__main__":
    main()

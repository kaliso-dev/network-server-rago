#!/usr/bin/env python3
"""Reads a screenshot of the billboard test: how much of each test board the client shows.

Usage: billboard-shot.py <screenshot.ppm>

The boards of the test are plain red (the map colour 255 0 0) and plain yellow (229 229 51).
Prints a small drawing of the screen (R: red, Y: yellow, a dot: the sky, #: anything else), then
on its last line "red=<pixels> yellow=<pixels>", counted in the left and top 70 % of the screen
only: the scoreboard on the right and the hearts at the bottom are red too.
"""
import sys

COLUMNS = 96
ROWS = 27
# Where the boards are counted: the screen but its right and bottom edges.
COUNTED = 0.7


def read_ppm(path):
    with open(path, "rb") as file:
        data = file.read()
    fields = []
    i = 0
    while len(fields) < 4:
        while data[i:i + 1].isspace():
            i += 1
        if data[i:i + 1] == b"#":
            while data[i:i + 1] not in (b"\n", b""):
                i += 1
            continue
        start = i
        while not data[i:i + 1].isspace():
            i += 1
        fields.append(data[start:i])
    if fields[0] != b"P6" or fields[3] != b"255":
        sys.exit("%s is not an 8-bit binary PPM" % path)
    return int(fields[1]), int(fields[2]), data[i + 1:]


def kind(r, g, b):
    if r >= 150 and g <= 90 and b <= 90:
        return "R"
    if r >= 150 and g >= 150 and b <= 120 and abs(r - g) <= 60:
        return "Y"
    if b >= 180 and b > r + 30:
        return "."
    return "#"


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    width, height, pixels = read_ppm(sys.argv[1])
    counts = {"R": 0, "Y": 0, ".": 0, "#": 0}
    cells = [[{"R": 0, "Y": 0, ".": 0, "#": 0} for _ in range(COLUMNS)] for _ in range(ROWS)]
    for y in range(height):
        row = pixels[3 * width * y: 3 * width * (y + 1)]
        cell_row = cells[min(ROWS - 1, y * ROWS // height)]
        for x in range(width):
            k = kind(row[3 * x], row[3 * x + 1], row[3 * x + 2])
            if x < COUNTED * width and y < COUNTED * height:
                counts[k] += 1
            cell_row[min(COLUMNS - 1, x * COLUMNS // width)][k] += 1
    for cell_row in cells:
        line = ""
        for cell in cell_row:
            # a board shows in its cell as soon as a few of its pixels are there
            if cell["R"] * 8 >= sum(cell.values()):
                line += "R"
            elif cell["Y"] * 8 >= sum(cell.values()):
                line += "Y"
            else:
                line += max(cell, key=cell.get)
        print("|" + line + "|")
    print("red=%d yellow=%d" % (counts["R"], counts["Y"]))


if __name__ == "__main__":
    main()

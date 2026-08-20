from __future__ import annotations
import sys

from . import __version__


def main() -> None:
    print(f"vesper-mcp {__version__} (bootstrap placeholder)", file=sys.stderr)


if __name__ == "__main__":
    main()

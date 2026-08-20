from __future__ import annotations
import logging
import os
import sys


def configure_logging() -> None:
    level = os.environ.get("VESPER_MCP_LOG_LEVEL", "INFO").upper()
    root = logging.getLogger()
    for h in list(root.handlers):
        root.removeHandler(h)
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(
        logging.Formatter("%(asctime)s %(levelname)s %(name)s %(message)s")
    )
    root.addHandler(handler)
    root.setLevel(level)
    logging.captureWarnings(True)

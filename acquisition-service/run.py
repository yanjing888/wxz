"""Run from any working directory; configuration paths are relative to this service."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))
from acquisition.server import main

if __name__ == "__main__":
    main()

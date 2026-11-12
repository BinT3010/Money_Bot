"""Compatibility import for existing tools."""
import sys
from moneybot import classifier as _implementation
sys.modules[__name__] = _implementation

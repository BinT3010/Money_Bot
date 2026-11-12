"""Compatibility import for existing tools."""
import sys
from moneybot import bills as _implementation
sys.modules[__name__] = _implementation

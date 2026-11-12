"""Compatibility import for existing tools."""
import sys
from moneybot import auth as _implementation
sys.modules[__name__] = _implementation

"""Compatibility import for existing tools."""
import sys
from moneybot import classification as _implementation
sys.modules[__name__] = _implementation

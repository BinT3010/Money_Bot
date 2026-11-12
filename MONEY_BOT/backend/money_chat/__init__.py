"""Compatibility package."""
from moneybot import money_chat as _implementation
__path__ = _implementation.__path__

import importlib
import sys
for _name in ['models', 'parser', 'services', 'storage', 'engine', 'ai', 'demo']:
    sys.modules[__name__ + "." + _name] = importlib.import_module("moneybot.money_chat." + _name)
    globals()[_name] = sys.modules[__name__ + "." + _name]

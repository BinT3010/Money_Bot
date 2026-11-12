"""Compatibility package."""
from moneybot import routes as _implementation
__path__ = _implementation.__path__
from moneybot.routes import StrictModel

import importlib
import sys
for _name in ['auth', 'chat', 'transactions', 'budget', 'bills']:
    sys.modules[__name__ + "." + _name] = importlib.import_module("moneybot.routes." + _name)
    globals()[_name] = sys.modules[__name__ + "." + _name]

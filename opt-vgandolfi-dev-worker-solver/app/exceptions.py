"""Domain exceptions for the worker solver."""


class InfeasibleVrpError(ValueError):
    """Raised when a VRP instance cannot be served by the given fleet.

    Carries a human-readable message describing the capacity dimension that was
    exceeded (e.g. volume, weight or delivery count) with the demanded vs.
    available values, so the orchestrator can surface a clear INFEASIBLE
    response instead of a generic traceback.
    """
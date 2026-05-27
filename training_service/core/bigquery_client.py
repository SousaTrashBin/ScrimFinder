"""Deprecated: use training_service.core.db directly."""

from training_service.core.db import get_bq_client, _bq_query


class BQClient:
    def __init__(self):
        self._client = get_bq_client()

    def connection(self):
        # Return a mock connection for compatibility
        return self

    def cursor(self):
        return self

    def execute(self, sql, params=None):
        return _bq_query(sql, params)

    def fetchall(self):
        return []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        pass

"""Utilities for reading environment variable documentation files.
"""

import dataclasses
import typing

@dataclasses.dataclass
class NodeInfo:
    level: int
    type: str
    name: str
    details: dict[str, typing.Any]

def visit_nodes(parsed_docs, visit_fn: typing.Callable[[NodeInfo], None]):
    """Parses an env-var-docs.json file and calls visit_fn on each node.

    The file is walked preorder (visit root then children).
    """
    for key, details in parsed_docs.items():
        if "variables" in details:
            # Object has a 'variables' field so it is a group node.
            visit_fn(NodeInfo(0, "group", key, details))
            for sub_key, sub_details in details["variables"].items():
                visit_fn(NodeInfo(1, 'env_var', sub_key, sub_details))
        else:
            # Object does not have a 'variables' field so it is an environment variable node.
            visit_fn(NodeInfo(0, "env_var", key, details))

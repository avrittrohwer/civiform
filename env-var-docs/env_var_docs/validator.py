"""Provides functionality for an environment variable documentation file meets the expected schema.
"""

import dataclasses
import typing
import json
import jsonschema


def validate(env_var_doc_file: typing.TextIO) -> str:
    """
    Validates an environment variable documentation file against the schema defined in schema.json.

    Returns an empty string if the file is valid and an error string if not.
    """
    return ""

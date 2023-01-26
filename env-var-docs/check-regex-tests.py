"""Checks that every regex test in env-var-docs.json passes.

Requires the following variables to be present in the environment:
    ENV_VAR_DOCS_PATH: the path to env-var-docs.json.
"""

import dataclasses
import json
import logging
import os
import pprint
import re
import sys


def errorexit(msg):
    """Logs a message and exits"""
    sys.stderr.write(msg + "\n")
    exit(1)


def main():
    docs_path = validate_env_variables()
    with open(docs_path) as f:
        failures = run_tests(f)

    if len(failures) != 0:
        pprint.pprint(failures)
        errorexit("Regular expression test failures. Please fix.")


def validate_env_variables() -> str:
    """Parses expected environment variables and returns the path to the env-var-docs.json file.

    Exits if any there are any validation errors.
    """

    try:
        path = os.environ["ENV_VAR_DOCS_PATH"]
        if not os.path.isfile(path):
            errorexit(f"'{path}' does not point to a file")
    except KeyError as e:
        errorexit(f"{e.args[0]} must be present in the environment variables")

    return path


@dataclasses.dataclass
class FailedTest:
    var: str
    regex: str
    val: str
    souldMatch: bool
    didMatch: bool


def run_tests(docs_file) -> list[FailedTest]:
    """Parses an env-var-docs.json file and runs any provided regex tests.

    Returns a list of FailedTests.
    """
    docs = json.load(docs_file)

    failed = []
    for var, details in docs.items():
        try:
            regex_text = details['regex']
        except KeyError:
            # It is valid for a Var to not specify a regex field.
            # In this case move on to the next Var.
            continue

        try:
            regex = re.compile(regex_text)
        except re.error as e:
            errorexit(f"Unable to compile regular expression '{regex_text}': {e.msg}. Do you need to escape any special characters in the json?")

        tests = details['regex_tests']

        for test in tests:
            val = test['val']
            shouldMatch = test['shouldMatch']

            didMatch = (regex.match(val) != None)
            if didMatch != shouldMatch:
                failed.append(
                    FailedTest(var, regex_text, val, shouldMatch, didMatch))

    return failed


if __name__ == "__main__":
    main()

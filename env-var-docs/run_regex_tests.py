"""Checks that every regex test in env-var-docs.json passes.

Requires the following variables to be present in the environment:
    ENV_VAR_DOCS_PATH: the path to env-var-docs.json.
"""

import dataclasses
import env_var_docs.validator
import env_var_docs.visitor
import os
import re
import sys
import typing


def errorexit(msg):
    """Logs a message and exits"""
    sys.stderr.write(msg + "\n")
    exit(1)


def main():
    docs_path = validate_env_variables()
    with open(docs_path) as f:
        err = env_var_docs.validator.validate(f)
        if err != "":
            errorexit("f{config.docs_path} is not valid. Errors:\n{err}")
        failures = run_tests(f)

    if len(failures) != 0:
        msg = ""
        for f in failures:
            msg += f.__str__() + "\n"
        errorexit("Test failures:\n" + msg)


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
    variable: str
    regex: str
    regex_error: str
    val: str
    shouldMatch: bool
    didMatch: bool

    def __str__(self):
        """Formats a FailedTest for printing."""
        s = ("{\n"
             f"\tVariable: {self.variable}\n"
             f"\tRegex: {self.regex}\n")

        if self.regex_error != "":
            s += f"\tRegex compile error: {self.regex_error}\n}}"
            return s
        else:
            s += (
                f"\tInput: {self.val}\n"
                f"\tShould match: {self.shouldMatch}\n"
                f"\tDid  match: {self.didMatch}\n}}")

        return s


def run_tests(docs_file: typing.TextIO) -> list[FailedTest]:
    """Runs any provided regex tests in an environment variable documentation file.

    Returns a list of FailedTests.
    """

    failed = []

    def run_test(node: env_var_docs.visitor.NodeInfo):
        if node.type != "variable":
            return
        if "regex" not in node.details:
            # It is valid for a Var to not specify a regex field.
            # In this case move on to the next Var.
            return

        regex_text = node.details["regex"]
        try:
            regex = re.compile(regex_text)
        except re.error as e:
            failed.append(
                FailedTest(
                    variable=node.name,
                    regex=regex_text,
                    regex_error=e.msg,
                    val="",
                    shouldMatch=False,
                    didMatch=False))
            return

        tests = node.details["regex_tests"]
        for test in tests:
            val = test["val"]
            shouldMatch = test["shouldMatch"]

            didMatch = (regex.match(val) != None)
            if didMatch != shouldMatch:
                failed.append(
                    FailedTest(
                        variable=node.name,
                        regex=regex_text,
                        regex_error="",
                        val=val,
                        shouldMatch=shouldMatch,
                        didMatch=didMatch))

    env_var_docs.visitor.visit(docs_file, run_test)
    return failed


if __name__ == "__main__":
    main()

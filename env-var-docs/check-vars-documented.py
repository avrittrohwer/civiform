"""Checks that every environment variable referenced in application.conf has documentation.

Requires the following variables to be present in the environment:
    APPLICATION_CONF_PATH: the path to application.conf.
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
    config = validate_env_variables()
    with open(config.app_conf_path) as f:
        vars = vars_from_application_conf(f)
    with open(config.docs_path) as f:
        documented_vars = vars_from_docs(f)

    undocumented = vars - documented_vars
    overdocumented = documented_vars - vars

    errors = False
    msg = "Error: CiviForm environment variables are not correctly documented. See <placeholder> for documentation instructions. Issues:\n"
    if len(undocumented) != 0:
        errors = True
        msg += f"The following vars are not documented in {config.docs_path}:\n{pprint.pformat(undocumented)}\n"
    if len(overdocumented) != 0:
        errors = True
        msg += f"The following vars are documented but not referenced in {config.app_conf_path}:\n{pprint.pformat(overdocumented)}"
    if errors:
        errorexit(msg)


@dataclasses.dataclass
class Config:
    app_conf_path: str
    docs_path: str


def validate_env_variables() -> Config:
    """Parses expected environment variables and returns a Config.

    Exits if any there are any validation errors.
    """

    def validate_path(env_var) -> str:
        path = os.environ[env_var]
        if not os.path.isfile(path):
            errorexit(f"'{path}' does not point to a file")
        return path

    try:
        app_conf = validate_path("APPLICATION_CONF_PATH")
        var_docs = validate_path("ENV_VAR_DOCS_PATH")
    except KeyError as e:
        errorexit(f"{e.args[0]} must be present in the environment variables")

    return Config(app_conf, var_docs)


def vars_from_application_conf(app_conf_file) -> set[str]:
    """Parses an application.conf file and returns the set of referenced environment variables."""

    # Matches any UPPER_SNAKE_CASE variables in substitutions like
    # ${?WHITELABEL_SMALL_LOGO_URL}, that are not in comment lines.
    env_var_re = re.compile("^[^#].*\${\?([A-Z0-9_]+)}")

    vars = set()
    for line in app_conf_file:
        match = env_var_re.match(line)
        if match == None:
            continue
        var = match.group(1)
        if var == None:
            continue

        vars.add(var)

    return vars


def vars_from_docs(docs_file) -> set[str]:
    """Parses an env-var-docs.json file and return the set of defined environment variables."""
    docs = json.load(docs_file)

    vars = set()
    for var in docs:
        vars.add(var)

    return vars


if __name__ == "__main__":
    main()
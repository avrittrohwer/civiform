"""Checks that every regex test in env-var-docs.json passes.

Requires the following variables to be present in the environment:
    ENV_VAR_DOCS_PATH: the path to env-var-docs.json.
    MARKDOWN_OUTPUT_PATH: the path to write generated markdown to.
"""

import dataclasses
import env_var_docs
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
    with open(config.docs_path) as docs_file:
        docs = json.load(docs_file)
        with open(config.markdown_path, mode='w') as markdown_file:
            write_markdown(docs, markdown_file)


@dataclasses.dataclass
class Config:
    docs_path: str
    markdown_path: str

def validate_env_variables() -> Config:
    """Parses expected environment variables and returns a config.

    Exits if any there are any validation errors.
    """

    try:
        docs = os.environ["ENV_VAR_DOCS_PATH"]
        if not os.path.isfile(docs):
            errorexit(f"'{docs}' does not point to a file")
        markdown = os.environ["MARKDOWN_OUTPUT_PATH"]
    except KeyError as e:
        errorexit(f"{e.args[0]} must be present in the environment variables")

    return Config(docs, markdown)

def write_markdown(parsed_docs, markdown_file):
    def output(node: env_var_docs.NodeInfo):
        doc = (
                f"{'#' * (1+node.level)} {node.name}\n\n"
                f"{node.details['description']}\n\n"
        )
        if node.type == "env_var":
            for key in ['type', 'required', 'values', 'regex']:
                if key in node.details:
                    doc += f"- {key.capitalize()}: `{node.details[key]}`\n"

        markdown_file.write(doc+"\n")

    env_var_docs.visit_nodes(parsed_docs, output)

if __name__ == "__main__":
    main()

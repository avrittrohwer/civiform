# Environment Variable Documentation

The main configuration file for the CiviForm server is in
[application.conf](../server/conf/application.conf). Configuration values can
be read at server start time from environment variables. This is done using the
`${?SOME_ENV_VAR}` substitution syntax.

To aid people deploying the CiviForm server, we ensure every environment
variable referenced in the configuration file has appropriate documentation.
This documentation lives in
[env-var-docs.json](../server/conf/env-var-docs.json]. As part of a CiviForm
release, the documentation is rendered as markdown and submitted to
docs.civiform.us.

We have a [GitHub action](../.github/workflows/check-env-var-docs.yaml) that
runs on any PR editing application.conf or env-var-docs.json. It ensures there
is a one-to-one mapping between environment variables reference in
application.conf and entries in env-var-docs.json.

## env-var-docs.json format

We maintain a [json-schema](https://json-schema.org/) that formally defines the
expected structure of env-var-docs.json in
[env-var-docs-schema.json](./env-var-docs-schema.json). Informally:

The env-var-docs.json file contains key-value pairs, each key corresponding to
an environment variable referenced in application.conf. Each value is an object
with the following fields:

- `description`: A human-readable description of what the environment variable
  configures.
- `type`: The value type expected. Can be 'string', 'int', 'bool', or
  'index-list'.
- `required`: If the environment variable is required to be set. If the
  `required` field is not set, the environment variable is not required.
- `values`: If the `type` is string, a list of valid strings can be provided.
  If `values` is defined, `regex` and `regex_tests` can not be defined.
- `regex`: If the `type` is string, a regular expression using [python re
  syntax](https://docs.python.org/3/library/re.html#regular-expression-syntax)
  can be provided. This regular expression defines the set of valid values. If
  `regex` is defined, `values` can not be defined.
- `regex_tests`: A list of objects. Each object contains a `val` field with a
  string value and a `shouldMatch` field with a boolean value. `If `regex` is
  defined, `regex_tests` must also be defined.

The `description` and `type` fields must be set for each environment variable.

Here is an exaple env-var-docs.json file:

```
{
	"TITLE": {
		"description": "Sets the CiviForm title.",
		"type": "string"
	},
	"SOME_NUMBER": {
		"description": "Sets a very important number.",
		"type": "int",
		"required": true
	},
	"LOGO_URL": {
		"description": "URL of the logo.",
		"type": "string",
		"regex": "^https?://",
		"regex_tests": [
			{ "val": "http://mylogo.png", "shouldMatch": true },
			{ "val": "https://my-secure-logo.png", "shouldMatch": true },
			{ "val": "props-not-a-valid-URL", "shouldMatch": false }
		]
	},
	"CLOUD_PROVIDER": {
		"description": "What cloud services to connect to.",
		"type": "string",
		"values": ["aws", "azure", "gcp"]
	}
}
```

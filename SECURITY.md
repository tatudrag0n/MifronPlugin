# Security policy

## Public repository rules

This repository contains public source and safe example configuration only.
Never commit API keys, bot tokens, DiscordSRV credentials, webhook URLs,
database files, player/server data, or production configuration.

The Minoru bridge secret must be configured on the server only. The checked-in
`src/main/resources/config.yml` intentionally leaves `serverSecret` and
`minoru-bridge.secret` empty; do not replace them with a real value.

## Reporting a vulnerability

Please do not open a public issue containing a secret or exploit details.
Report security issues privately to the repository owner and rotate any
exposed credential immediately.

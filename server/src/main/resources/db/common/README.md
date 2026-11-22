Add shared MySQL/TiDB-compatible Flyway schema migrations here starting at V81.
The UR3 installation first resolves its legacy dialect-specific history. Isolated MySQL
deployments first resolve the schema-only V80 in db/isolated. Every version number
must be unique across the two locations loaded by each installation. Keep organization
data imports outside this directory and review dialect parity for every schema change.

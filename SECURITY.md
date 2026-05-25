# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.1.x   | Yes       |

## Reporting a Vulnerability

Do **not** open a public GitHub issue for security vulnerabilities.

Email security@ardley.co with:

1. Description of the vulnerability
2. Steps to reproduce
3. Potential impact assessment
4. Suggested fix (if you have one)

We will acknowledge receipt within 48 hours and provide a timeline for a fix within 5 business days.

## Security Measures

This project implements the following protections:

- **Dependency verification**: All dependencies are pinned with SHA-256 checksums and PGP signatures (`gradle/verification-metadata.xml`)
- **Action pinning**: All GitHub Actions are pinned to full commit SHAs, not mutable tags
- **Minimal permissions**: CI workflows use `permissions: contents: read` by default
- **Environment protection**: Publish workflow requires the `production` environment with manual approval
- **No credential persistence**: `persist-credentials: false` on all checkouts
- **Signed artifacts**: All published JARs are GPG-signed
- **CODEOWNERS**: All changes to build infrastructure require security team review
- **No runtime dependencies on auth**: The core library never handles authentication, tokens, or secrets

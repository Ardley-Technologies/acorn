# Security

## Reporting issues

If you find a security vulnerability in Acorn, please don't open a public issue. We take these seriously and want to fix them before they're exploitable in the wild.

Send an email to **security@ardley.com** with whatever details you have — what you found, how to reproduce it, how bad you think it is. A suggested fix is welcome but not expected. We'll acknowledge within 48 hours and get back to you with a plan within a week.

## What we protect against

Acorn is an authorization library. It doesn't handle authentication, doesn't store secrets, and doesn't make network calls. The attack surface is narrow by design. That said, we've taken specific steps to make sure the project itself doesn't become a vector:

**Dependencies are verified.** Every library we pull in is pinned with checksums in `gradle/verification-metadata.xml`. If someone compromises a dependency on Maven Central and swaps the JAR, our build fails instead of silently pulling it in. We verify PGP signatures where available.

**CI can't be poisoned easily.** Our publish workflow runs in a protected environment that requires manual approval. Secrets are scoped to that environment — a compromised PR can't exfiltrate them. Workflows only get read access to the repo by default.

**Build infrastructure changes get extra scrutiny.** Anything touching `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, or `.github/` requires review from the security team via CODEOWNERS. You can't sneak a malicious build change through a large PR.

**Published artifacts are signed.** Every JAR we ship to Maven Central and GitHub Packages is GPG-signed. You can verify the signature before trusting the artifact.

**We keep dependencies minimal.** The core module depends on Guava and Jackson — nothing else. Fewer dependencies means fewer things that can go wrong. We're deliberate about what we pull in and why.

## Supported versions

We patch security issues in the latest minor release only. If you're on an older version, upgrade.

| Version | Security patches |
|---------|-----------------|
| 0.1.x   | Yes             |

## Scope

Acorn evaluates authorization decisions. It does not:

- Parse or validate JWTs (that's your `PrincipalExtractor`)
- Store or transmit credentials
- Make outbound HTTP calls
- Cache sensitive data (permission sets are the only cached item, and they contain allow/deny rules — not secrets)

If you find a way to bypass the evaluator's deny-wins logic, escalate privileges through scope filter manipulation, or cause the filter to silently pass unauthorized requests — that's a vulnerability we want to hear about.

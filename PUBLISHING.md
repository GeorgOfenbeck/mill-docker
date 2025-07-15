# Publishing mill-docker to Sonatype Central

This guide explains how to publish mill-docker to Sonatype Central (Maven Central).

## Prerequisites

1. **Sonatype Central Account**: Create an account at https://central.sonatype.com/
2. **GPG Key**: Generate a GPG key for signing artifacts
3. **Repository Secrets**: Configure the following GitHub secrets:
   - `SONATYPE_USERNAME`: Your Sonatype Central username
   - `SONATYPE_PASSWORD`: Your Sonatype Central password
   - `PGP_SECRET_BASE64`: Base64 encoded GPG private key
   - `PGP_PASSPHRASE`: Your GPG key passphrase

## Setting up GPG Key

1. Generate a GPG key:
   ```bash
   gpg --gen-key
   ```

2. List your keys and find the key ID:
   ```bash
   gpg --list-secret-keys --keyid-format LONG
   ```

3. Export your public key to a key server:
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```

4. Export your private key and encode it in base64:
   ```bash
   gpg --export-secret-keys YOUR_KEY_ID | base64
   ```

## Publishing

### Manual Publishing

To publish manually from your local machine:

```bash
export MILL_SONATYPE_USERNAME="your-username"
export MILL_SONATYPE_PASSWORD="your-password"
export MILL_PGP_SECRET_BASE64="your-base64-encoded-key"
export MILL_PGP_PASSPHRASE="your-passphrase"

./mill mill-docker.publishSonatypeCentral
```

### Automated Publishing

The repository is configured with GitHub Actions to automatically publish when:
- A new tag starting with 'v' is pushed (e.g., `v0.0.4`)
- The workflow is manually triggered from the Actions tab

To create a new release:

1. Update the version in `build.mill`:
   ```scala
   override def publishVersion: T[String] = "0.0.4"
   ```

2. Commit and push:
   ```bash
   git add build.mill
   git commit -m "Bump version to 0.0.4"
   git push
   ```

3. Create and push a tag:
   ```bash
   git tag v0.0.4
   git push origin v0.0.4
   ```

The GitHub Action will automatically build and publish the artifacts to Sonatype Central.

## Verifying Publication

After publishing, you can verify your artifacts at:
- https://central.sonatype.com/artifact/com.ofenbeck/mill-docker_mill1.0.0_3
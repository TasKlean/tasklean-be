# Image bucket setup

Uploaded images live in an S3-compatible bucket: **Cloudflare R2** outside dev, **LocalStack** from
`docker-compose` locally. The application only ever reads `storage.*` config, so the two differ in
environment variables and nothing else.

Local setup is nothing: `docker-compose up -d` starts LocalStack, and
`docker/localstack/init-bucket.sh` creates the bucket and applies the CORS rules on every start.
The rest of this file is the production side, which is console work nobody will remember.

## 1. Create the bucket

Cloudflare dashboard → R2 → *Create bucket*. One per environment (`tasklean-staging`,
`tasklean-prod`). Location hint: wherever the API runs.

**Leave public access off.** Do not enable the `r2.dev` subdomain and do not attach a custom
domain. Nothing reads the bucket directly: the API signs a short-lived URL per request and the
frontend fetches it server-side. A public bucket would make every uploaded photo readable by
anyone who ever saw its URL — including completion proof shot inside someone's home.

## 2. Create an API token

R2 → *Manage API tokens* → *Create API token*, scoped to **that one bucket** with
*Object Read & Write*. It yields an access key id and a secret, which become `S3_ACCESS_KEY` and
`S3_SECRET_KEY`. The endpoint shown alongside it (`https://<account-id>.r2.cloudflarestorage.com`)
is `S3_ENDPOINT`. `S3_REGION` stays `auto` — R2 ignores it, but the SDK requires a value.

## 3. Apply the CORS rules

The browser PUTs image bytes straight to the bucket, so the bucket — not the API — has to allow
the frontend's origin. Without this the upload fails in the browser with an opaque CORS error
while every server-side call keeps working.

R2 → the bucket → *Settings* → *CORS policy*, using the same shape the local bucket gets from
[`docker/localstack/bucket-cors.json`](../docker/localstack/bucket-cors.json), with the origin
replaced:

```json
{
  "CORSRules": [
    {
      "AllowedOrigins": ["https://tasklean.app"],
      "AllowedMethods": ["PUT"],
      "AllowedHeaders": ["content-type"],
      "MaxAgeSeconds": 3600
    }
  ]
}
```

`PUT` alone is deliberate. Downloads are fetched by the frontend's server, which is not a browser
origin and needs no CORS entry, so the bucket never has to allow `GET` from anywhere.

## What the local bucket cannot prove

LocalStack is a functional emulator, not an enforcing one. Two differences matter:

- **Signature validation is off by default**, which would accept an upload whose size or content
  type differs from the one we signed for — and the production bucket would then be the first thing
  to refuse it. `docker-compose.yml` sets `S3_SKIP_SIGNATURE_VALIDATION: 0` so the caps are real
  locally too; with it on, an oversize PUT answers 403 exactly as R2 does.
- **Anonymous reads succeed.** LocalStack enforces no bucket policy, so an unsigned GET returns the
  object locally. R2 is private unless public access is explicitly enabled, which step 1 forbids.

Because of that second point, the API never treats the bucket's own refusal as the only guard: an
object is verified with a `HEAD` and a magic-number check **before** its key is attached to
anything, so a mismatch is a `400` from us rather than a trusted upload.

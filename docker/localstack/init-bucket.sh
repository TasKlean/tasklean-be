#!/bin/sh
# Runs inside LocalStack once S3 is ready, so a fresh clone has a working bucket with no setup.
# The CORS rules are the same file production applies to Cloudflare R2 - see docs/bucket-cors.md.
set -e

BUCKET="${S3_BUCKET:-tasklean-dev}"

awslocal s3api create-bucket --bucket "$BUCKET"
awslocal s3api put-bucket-cors --bucket "$BUCKET"   --cors-configuration "file:///etc/localstack/init/ready.d/bucket-cors.json"

echo "Bucket $BUCKET is ready, with CORS applied."

#!/bin/sh
set -eu

TOPIC_ARN="$(awslocal sns create-topic \
  --name notification-audit-events \
  --query TopicArn \
  --output text)"

QUEUE_URL="$(awslocal sqs create-queue \
  --queue-name notification-audit-events \
  --query QueueUrl \
  --output text)"

QUEUE_ARN="$(awslocal sqs get-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)"

QUEUE_ATTRIBUTES="$(QUEUE_ARN="$QUEUE_ARN" TOPIC_ARN="$TOPIC_ARN" python3 - <<'PY'
import json
import os

policy = {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowSnsPublish",
            "Effect": "Allow",
            "Principal": "*",
            "Action": "sqs:SendMessage",
            "Resource": os.environ["QUEUE_ARN"],
            "Condition": {
                "ArnEquals": {"aws:SourceArn": os.environ["TOPIC_ARN"]}
            },
        }
    ],
}

print(json.dumps({"Policy": json.dumps(policy)}))
PY
)"

awslocal sqs set-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attributes "$QUEUE_ATTRIBUTES"

awslocal sns subscribe \
  --topic-arn "$TOPIC_ARN" \
  --protocol sqs \
  --notification-endpoint "$QUEUE_ARN" \
  --attributes RawMessageDelivery=true

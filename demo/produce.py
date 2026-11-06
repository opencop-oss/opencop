#!/usr/bin/env python3
# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0
"""Send one moving point to the bundled evaluation demo's Kafka source."""

import argparse
import json
import logging
import math
from pathlib import Path
import re
import sys
import time
import uuid

OBJECT_ID = "first-data-demo"
UPDATE_COUNT = 60
INTERVAL_SECONDS = 1


def load_credentials(path):
    """Validate the downloaded file without including its contents in errors."""
    try:
        credentials = json.loads(Path(path).read_text(encoding="utf-8"))
        fields = ("topicName", "bootstrapServer", "username", "password")
        if not isinstance(credentials, dict) or any(
            not isinstance(credentials.get(key), str) or not credentials[key].strip()
            for key in fields
        ):
            raise ValueError()
        endpoint = re.fullmatch(r"(?:localhost|127\.0\.0\.1|\[::1\]):([0-9]{1,5})",
                                credentials["bootstrapServer"], re.IGNORECASE)
        if not endpoint or not 1 <= int(endpoint.group(1)) <= 65535:
            raise ValueError()
        match = re.fullmatch(
            r"opencop\.byod\.[a-z0-9-]+\.([0-9a-fA-F-]{36})", credentials["topicName"]
        )
        if not match:
            raise ValueError()
        source = str(uuid.UUID(match.group(1)))
        if match.group(1).lower() != source:
            raise ValueError()
        return credentials, source
    except OSError:
        raise ValueError("Could not read the credentials file. Pass the path to the kafka-credentials.json you downloaded.") from None
    except (UnicodeError, ValueError):
        raise ValueError("Use an intact kafka-credentials.json from the bundled local demo. Its bootstrapServer must be localhost, 127.0.0.1 or [::1] with a valid port.") from None


def make_feature(source, step, timestamp):
    # A small circle near Washington, DC: GeoJSON coordinates are longitude, latitude.
    angle = step * math.tau / 30
    return {
        "type": "Feature",
        "geometry": {
            "type": "Point",
            "coordinates": [-77.04 + 0.003 * math.cos(angle), 38.90 + 0.003 * math.sin(angle)],
        },
        "properties": {
            "opencop": {
                "id": str(uuid.uuid4()),
                "version": "1",
                "source": source,
                "objectId": OBJECT_ID,
                "displayName": "Kafka demo",
                "kind": "demo",
                "element": {"type": "point", "fillColor": "#ff6600ff", "radius": 10},
                "timestampz": {"time": timestamp, "start": timestamp, "stale": timestamp + 600000},
            }
        },
    }


def create_producer(credentials):
    from kafka import KafkaProducer

    return KafkaProducer(
        bootstrap_servers=credentials["bootstrapServer"],
        security_protocol="SASL_PLAINTEXT",
        sasl_mechanism="SCRAM-SHA-256",
        sasl_plain_username=credentials["username"],
        sasl_plain_password=credentials["password"],
        acks="all",
        retries=0,
        max_block_ms=10000,
        request_timeout_ms=10000,
        bootstrap_timeout_ms=10000,
    )


def produce(producer, credentials, source):
    previous = 0
    for step in range(UPDATE_COUNT):
        # Both live-map and history ordering must advance, even within one millisecond.
        timestamp = max(time.time_ns() // 1000000, previous + 1)
        previous = timestamp
        payload = json.dumps(make_feature(source, step, timestamp)).encode("utf-8")
        producer.send(
            credentials["topicName"], key=OBJECT_ID.encode("utf-8"), value=payload
        ).get(timeout=15)
        print(f"Broker acknowledged update {step + 1}/{UPDATE_COUNT}", flush=True)
        if step + 1 < UPDATE_COUNT:
            time.sleep(INTERVAL_SECONDS)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("credentials", help="downloaded kafka-credentials.json")
    args = parser.parse_args(argv)

    # The client may log connection details. Keep arbitrary credential values out of diagnostics.
    logging.disable(logging.CRITICAL)
    producer = None
    status = 0
    try:
        credentials, source = load_credentials(args.credentials)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1
    try:
        producer = create_producer(credentials)
        produce(producer, credentials, source)
        print("Finished. Check the map: broker acknowledgment does not confirm ingestion.")
    except ImportError:
        print("Install requirements.txt in your virtual environment first.", file=sys.stderr)
        status = 1
    except KeyboardInterrupt:
        print("Stopped. Outstanding delivery is not confirmed.", file=sys.stderr)
        status = 130
    except Exception:
        print("Kafka send failed. Check that the demo is running and the downloaded credentials belong to this source.", file=sys.stderr)
        status = 1
    finally:
        if producer is not None:
            try:
                # Every successful send was acknowledged. Do not wait for uncertain sends on failure.
                producer.close(timeout=0)
            except Exception:
                print("Kafka client shutdown failed; outstanding delivery is not confirmed.", file=sys.stderr)
                status = status or 1
    return status


if __name__ == "__main__":
    sys.exit(main())

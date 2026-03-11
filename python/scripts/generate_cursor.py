#!/usr/bin/env python3
"""
Script: generate_cursor.py

Purpose:
- Generates a Base64url-encoded composite cursor for manual API Gateway testing
- Cursor format mirrors CursorUtils.encode() in the Java backend: base64url(isoDateTime|reviewId)
- Use the output as the `cursor` query parameter when testing paginated endpoints
"""

import base64
import sys


def generate_cursor(iso_datetime: str, review_id: str) -> str:
    raw = f"{iso_datetime}|{review_id}"
    return base64.urlsafe_b64encode(raw.encode("utf-8")).decode("utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python generate_cursor.py <isoDateTime> <reviewId>")
        print('Example: python generate_cursor.py "2024-01-15T10:30:00Z" "restaurantId:accountId"')
        sys.exit(1)

    print(generate_cursor(sys.argv[1], sys.argv[2]))

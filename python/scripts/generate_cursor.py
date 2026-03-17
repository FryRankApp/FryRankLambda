#!/usr/bin/env python3
"""
Script: generate_cursor.py

Purpose:
- Generates a Base64url-encoded composite cursor for manual API Gateway testing
- Cursor format mirrors CursorUtils.encode() in the Java backend: base64url(isoDateTime|restaurantId|identifier)
- Use the output as the `cursor` query parameter when testing paginated endpoints
"""

import base64
import sys


def generate_cursor(iso_datetime: str, restaurant_id: str, identifier: str) -> str:
    raw = f"{iso_datetime}|{restaurant_id}|{identifier}"
    return base64.urlsafe_b64encode(raw.encode("utf-8")).rstrip("=").decode("utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print("Usage: python generate_cursor.py <isoDateTime> <restaurantId> <identifier>")
        print('Example: python generate_cursor.py "2024-01-15T10:30:00Z" "ChIJl8BSSgfsj4ARi9qijghUAH0" "REVIEW:googleAccountId123"')
        sys.exit(1)

    print(generate_cursor(sys.argv[1], sys.argv[2], sys.argv[3]))

#!/usr/bin/env python3
"""Prepare cloud-local Maven settings and development credentials; never print secrets."""
import os
from pathlib import Path
import secrets
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

cache = Path("/workspace/.m2")
cache.mkdir(exist_ok=True)
settings = cache / "settings.xml"
if not settings.exists():
    root = ET.Element("settings")
    proxy_url = os.environ.get("HTTPS_PROXY") or os.environ.get("HTTP_PROXY")
    if proxy_url:
        url = urlparse(proxy_url)
        if url.username or url.password:
            raise SystemExit("Authenticated proxy needs supported Maven configuration")
        if not url.hostname:
            raise SystemExit("Invalid proxy URL")
        proxy = ET.SubElement(ET.SubElement(root, "proxies"), "proxy")
        for name, value in {"id": "cloud-egress", "active": "true", "protocol": "http",
                            "host": url.hostname, "port": str(url.port or 80),
                            "nonProxyHosts": "localhost|127.0.0.1"}.items():
            ET.SubElement(proxy, name).text = value
    ET.ElementTree(root).write(settings, encoding="unicode")
    settings.chmod(0o600)

# Local MySQL development credentials, unrelated to any external service.
# Preserve all existing configuration. Never use these credentials in production.
env_file = Path(__file__).resolve().parent.parent / ".env"
if not env_file.exists():
    with env_file.open("x") as stream:
        env_file.chmod(0o600)
        stream.write("DB_USERNAME=mtg\nDB_PASSWORD=" + secrets.token_hex(24)
                     + "\nMYSQL_ROOT_PASSWORD=" + secrets.token_hex(24)
                     + "\nMYSQL_PORT=3306\nDB_URL=jdbc:mysql://127.0.0.1:3306/mtg_deck_manager\n")

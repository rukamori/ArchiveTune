"""
ArchiveTune (2026)
© Rukamori — github.com/rukamori
GPL-3.0 License | Contributors: see git history
Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
"""

import argparse
import copy
import hashlib
import io
import json
import math
import os
from pathlib import Path
import re
import shutil
import struct
import sys
import tempfile
import textwrap
from urllib.parse import urljoin, urlsplit
import zipfile
import zlib


LIMITS = {"manifest.json": 128 * 1024, "provider.js": 3 * 1024 * 1024, "icon.png": 256 * 1024}
PACKAGE_LIMIT = 4 * 1024 * 1024
CAPABILITIES = ("searchTracks", "getTrackStreamUrl", "getAlbum", "getArtist", "getPlaylist")
FEATURES = ("fetch@1", "settings@1", "streamResolution@1", "packagedIcon@1", "qualityOptions@1")
EXTENSION_FEATURES = ("signedSession@3", "sessionGrant@1", "downloadSegments@1", "preparedContext@1")
TYPES = {
    "text": "text", "string": "text", "url": "text", "password": "text",
    "number": "number", "integer": "number", "boolean": "boolean", "switch": "boolean",
    "toggle": "boolean", "checkbox": "boolean", "select": "select", "selector": "select", "dropdown": "select",
}
SENSITIVE = re.compile(r"token|password|secret|credential|api.?key|cookie|authorization", re.I)
RUNTIME_NOTE = "JavaScript was not executed. Callable exports, service access, audio quality, and playback must be checked in ArchiveTune."


def issue(issues, field, message, level="error"):
    issues.append({"level": level, "field": field, "message": message})


def read_bounded(path, limit):
    with path.open("rb") as source:
        data = source.read(limit + 1)
    if len(data) > limit:
        raise ValueError(f"{path.name} exceeds {limit:,} bytes")
    return data


def parse_json(data):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(data.decode("utf-8-sig"), object_pairs_hook=unique)
    except json.JSONDecodeError as error:
        raise ValueError(f"JSON line {error.lineno}, column {error.colno}: {error.msg}") from error
    except RecursionError as error:
        raise ValueError("JSON nesting exceeds 32 levels") from error
    pending = [(value, 0)]
    while pending:
        item, depth = pending.pop()
        if isinstance(item, (dict, list)):
            if depth >= 32:
                raise ValueError("JSON nesting exceeds 32 levels")
            pending.extend((child, depth + 1) for child in (item.values() if isinstance(item, dict) else item))
        elif type(item) in (int, float):
            try:
                finite = math.isfinite(item)
            except OverflowError:
                finite = False
            if not finite:
                raise ValueError("JSON numbers must be finite")
    return value


def encode_json(value):
    return (json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def atomic_write(path, data):
    temporary_path = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".tmp", delete=False) as temporary:
            temporary_path = Path(temporary.name)
            temporary.write(data)
            temporary.flush()
            os.fsync(temporary.fileno())
        os.replace(temporary_path, path)
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def equivalent(first, second):
    if type(first) in (int, float) and type(second) in (int, float):
        return float(first) == float(second)
    if type(first) is not type(second):
        return False
    if isinstance(first, dict):
        return first.keys() == second.keys() and all(equivalent(first[key], second[key]) for key in first)
    if isinstance(first, list):
        return len(first) == len(second) and all(equivalent(a, b) for a, b in zip(first, second))
    return first == second


def default_value(field):
    return next((field[key] for key in ("defaultValue", "value", "default") if key in field), None)


def text_length(value):
    return len(value.encode("utf-16-le")) // 2


def redact(value):
    if isinstance(value, dict):
        return {key: "<redacted>" if SENSITIVE.search(key) else redact(item) for key, item in value.items()}
    if isinstance(value, list):
        return [redact(item) for item in value]
    return value


def secret_field(key, field):
    return field.get("secret") is True or str(field.get("type", "")).lower() == "password" or bool(SENSITIVE.search(key))


def string_list(value, field, issues, allow_string=False):
    if allow_string and isinstance(value, str):
        value = [value]
    if not isinstance(value, list) or len(value) > 32 or any(not isinstance(item, str) or not item.strip() or text_length(item) > 128 for item in value):
        issue(issues, field, "Use at most 32 nonblank strings of at most 128 characters")
        return []
    return value


def validate_settings(module, issues, extension=False):
    raw, fields = module.get("settings", {}), {}
    if isinstance(raw, list):
        for index, field in enumerate(raw):
            key = field.get("key") if isinstance(field, dict) else None
            if not isinstance(key, str) or not key.strip() or key in fields:
                issue(issues, f"module.settings[{index}]", "Each field needs a unique nonblank string key")
            else:
                fields[key] = field
    elif isinstance(raw, dict):
        fields = copy.deepcopy(raw)
    else:
        issue(issues, "module.settings", "Use a keyed object or an array of field declarations")
    if "qualityOptions" in module:
        declared = module["qualityOptions"]
        if not isinstance(declared, list) or not 1 <= len(declared) <= 256 or "quality" in fields:
            issue(issues, "module.qualityOptions", "Use 1–256 options without also declaring settings.quality")
        else:
            options, ids = [], []
            for index, option in enumerate(declared):
                path = f"module.qualityOptions[{index}]"
                if not isinstance(option, dict):
                    issue(issues, path, "Use an object with id and label")
                    continue
                identity, label = option.get("id"), option.get("label")
                if identity is None or isinstance(identity, (dict, list)) or any(equivalent(identity, previous) for previous in ids):
                    issue(issues, path + ".id", "Use a unique non-null string, number, or boolean ID")
                if not isinstance(label, str) or not label.strip():
                    issue(issues, path + ".label", "Use a nonblank string label")
                ids.append(identity)
                options.append({"value": identity, "label": label, "description": option.get("description", ""), "kind": option.get("kind", "")})
            default = module.get("defaultQuality", ids[0] if ids else None)
            if not any(equivalent(default, identity) for identity in ids):
                issue(issues, "module.defaultQuality", "The default must match a declared quality ID, including its type")
            label = module.get("qualityLabel", "quality")
            if not isinstance(label, str) or not label.strip():
                issue(issues, "module.qualityLabel", "Use a nonblank label")
            fields["quality"] = {"type": "select", "label": label, "defaultValue": default, "options": options}
    elif "defaultQuality" in module:
        issue(issues, "module.defaultQuality", "Declare qualityOptions when using defaultQuality")
    if len(fields) > 128:
        issue(issues, "module.settings", "At most 128 fields are supported")
    summary = []
    for key, field in fields.items():
        path = f"module.settings.{key}"
        if not key.strip() or text_length(key) > 128 or not isinstance(field, dict):
            issue(issues, path, "Use a field object with a nonblank key of at most 128 characters")
            continue
        raw_type = field.get("type", "text")
        if extension and raw_type == "button" and not field.get("required"):
            continue
        kind = TYPES.get(raw_type.lower()) if isinstance(raw_type, str) else None
        if kind is None:
            issue(issues, path + ".type", "Unsupported setting type")
        for name, limit in (("label", 256), ("description", LIMITS["manifest.json"])):
            value = field.get(name, key if name == "label" else "")
            if not isinstance(value, str) or text_length(value) > limit:
                issue(issues, path + "." + name, f"Use a string of at most {limit} characters")
        for flag in ("required", "secret"):
            if flag in field and type(field[flag]) is not bool:
                issue(issues, path + "." + flag, "Use true or false")
        bounds = [field.get("min"), field.get("max")]
        for name, bound in zip(("min", "max"), bounds):
            if name in field and type(bound) not in (int, float):
                issue(issues, path + "." + name, "Use a finite number")
        numeric_bounds = all(bound is None or type(bound) in (int, float) for bound in bounds)
        if numeric_bounds and all(bound is not None for bound in bounds) and bounds[0] > bounds[1]:
            issue(issues, path, "min must not exceed max")
        options = field.get("options", [])
        if not isinstance(options, list) or len(options) > 256 or (kind == "select" and not options):
            issue(issues, path + ".options", "Select fields need 1–256 options")
            options = []
        option_values = []
        for index, option in enumerate(options):
            option_values.append(option.get("value") if isinstance(option, dict) else option)
            if isinstance(option, dict):
                for name, limit in (("label", 256), ("description", 2048), ("kind", 64)):
                    if name in option and (not isinstance(option[name], str) or text_length(option[name]) > limit):
                        issue(issues, f"{path}.options[{index}].{name}", f"Use a string of at most {limit} characters")
        default = default_value(field)
        valid = default is None
        if default is not None:
            if kind == "text":
                valid = isinstance(default, str)
            elif kind == "boolean":
                valid = type(default) is bool
            elif kind == "select":
                valid = any(equivalent(default, value) for value in option_values)
            elif kind == "number":
                valid = type(default) in (int, float) and (raw_type.lower() != "integer" or default % 1 == 0)
                if valid and numeric_bounds:
                    valid = (bounds[0] is None or default >= bounds[0]) and (bounds[1] is None or default <= bounds[1])
        if not valid:
            issue(issues, path + ".defaultValue", "Default does not satisfy the declared type, options, or bounds")
        sensitive = secret_field(key, field)
        if sensitive and default not in (None, ""):
            issue(issues, path + ".defaultValue", "A sensitive default will be distributed. Use null for personal credentials", "warning")
        choices = []
        for option in options:
            if isinstance(option, dict):
                label, description = option.get("label", ""), option.get("description", "")
                label = label if isinstance(label, str) else "<invalid>"
                description = description if isinstance(description, str) else "<invalid>"
                value = option.get("value")
            else:
                value, description = option, ""
                label = "<redacted>" if sensitive else json.dumps(redact(option), ensure_ascii=True)
            choices.append({"label": label, "value": "<redacted>" if sensitive else redact(value), "description": description})
        summary.append({"key": key, "type": raw_type if isinstance(raw_type, str) else "<invalid>", "required": field.get("required") is True,
                        "sensitive": sensitive, "default": "<redacted>" if sensitive else redact(default), "options": len(options),
                        "choices": choices})
    return summary


def validate(manifest, entries, issues, app_version=None, verify_hashes=False):
    if not isinstance(manifest, dict):
        issue(issues, "manifest.json", "The root must be an object")
        return []
    for key, expected in (("format", "archivetune.module"), ("formatVersion", 1), ("apiVersion", 1), ("entry", "provider.js")):
        if type(manifest.get(key)) is not type(expected) or manifest.get(key) != expected:
            issue(issues, key, f"Expected {json.dumps(expected)}")
    runtime = manifest.get("runtime", "archivetune")
    if runtime not in ("archivetune", "extension"):
        issue(issues, "runtime", "Use archivetune or extension")
    extension = runtime == "extension"
    module = manifest.get("module")
    if not isinstance(module, dict):
        issue(issues, "module", "An object containing provider metadata is required")
        return []
    for key in ("signedSession", "permissions", "serviceHealth", "searchBehavior", "urlHandler", "iconBase64"):
        if key in module and (not extension or key == "iconBase64"):
            issue(issues, "module." + key, "This declaration is not supported in an authoring manifest")
    if extension:
        permissions = module.get("permissions", {})
        if not isinstance(permissions, dict):
            issue(issues, "module.permissions", "Use a permissions object")
            permissions = {}
        network = permissions.get("network", [])
        if not isinstance(network, list) or len(network) > 128 or any(not isinstance(domain, str) or len(domain) > 253 or not re.fullmatch(r"(?:\*\.)?[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?", domain) for domain in network):
            issue(issues, "module.permissions.network", "Use at most 128 domain names, optionally prefixed with *.")
            network = []
        for key in ("storage", "file"):
            if key in permissions and type(permissions[key]) is not bool:
                issue(issues, "module.permissions." + key, "Use true or false")
        if "signedSession" in module:
            config = module["signedSession"]
            if not isinstance(config, dict):
                issue(issues, "module.signedSession", "Use a signed-session configuration object")
            else:
                try:
                    base = urlsplit(config.get("baseUrl", ""))
                    valid = base.scheme == "https" and base.hostname and base.username is None and not base.query and not base.fragment and not re.search(r"\s", config.get("baseUrl", ""))
                    allowed = any(base.hostname == domain.lower() or (domain.startswith("*.") and base.hostname.endswith(domain[1:].lower())) for domain in network) if valid else False
                except (TypeError, ValueError, AttributeError):
                    valid, allowed = False, False
                if not valid or not allowed:
                    issue(issues, "module.signedSession.baseUrl", "Use an HTTPS base URL included in the network permissions")
                if not isinstance(config.get("namespace"), str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}", config["namespace"]):
                    issue(issues, "module.signedSession.namespace", "Use a namespace with letters, digits, dots, underscores, and hyphens")
                window = config.get("timeWindowSeconds", 300)
                if type(window) is not int or not 1 <= window <= 86400:
                    issue(issues, "module.signedSession.timeWindowSeconds", "Use an integer from 1 to 86400")
                for key in ("appVersion", "platform", "schemeLabel", "headerPrefix"):
                    value = config.get(key)
                    if (key == "schemeLabel" or key in config) and (not isinstance(value, str) or not value.strip() or len(value) > 256 or any(ord(char) < 32 or ord(char) > 126 for char in value)):
                        issue(issues, "module.signedSession." + key, "Use 1–256 printable ASCII characters")
                prefix = config.get("headerPrefix", "X-Sig-")
                if not isinstance(prefix, str) or not re.fullmatch(r"[A-Za-z0-9-]+", prefix):
                    issue(issues, "module.signedSession.headerPrefix", "Use an HTTP header prefix with letters, digits, and hyphens")
                endpoints = config.get("endpoints", {})
                if not isinstance(endpoints, dict):
                    issue(issues, "module.signedSession.endpoints", "Use an endpoint object")
                else:
                    for key in ("bootstrap", "challenge", "exchange", "refresh"):
                        if key in endpoints and (not isinstance(endpoints[key], str) or len(endpoints[key]) > 2048 or (key != "refresh" and not endpoints[key].strip())):
                            issue(issues, "module.signedSession.endpoints." + key, "Use a nonblank endpoint of at most 2048 characters; refresh may be empty")
                        elif key in endpoints and endpoints[key].strip() and valid:
                            try:
                                target = urlsplit(urljoin(base.geturl().rstrip('/') + '/', endpoints[key].lstrip('/')))
                                same_origin = target.scheme == "https" and target.hostname == base.hostname and (target.port or 443) == (base.port or 443) and target.username is None and not re.search(r"\s", endpoints[key])
                            except ValueError:
                                same_origin = False
                            if not same_origin:
                                issue(issues, "module.signedSession.endpoints." + key, "Keep signed-session endpoints on the HTTPS base URL's origin")
    for key, limit in (("id", 128), ("name", 256), ("version", 128), ("displayName", 256), ("author", LIMITS["manifest.json"]),
                       ("description", 8192), ("license", 256), ("homepage", 2048), ("repository", 2048), ("minAppVersion", 20), ("qualityLabel", 256)):
        value = module.get(key, "")
        if not isinstance(value, str) or text_length(value) > limit or (key in ("id", "name", "version") and not value.strip()):
            issue(issues, "module." + key, f"Use {'a nonblank' if key in ('id', 'name', 'version') else 'a'} string of at most {limit} characters")
    if isinstance(module.get("id"), str) and not re.fullmatch(r"[a-z0-9][a-z0-9._-]{0,127}", module["id"]):
        issue(issues, "module.id", "Use lowercase letters, digits, dots, underscores, and hyphens; start with a letter or digit")
    for key in ("homepage", "repository"):
        value = module.get(key)
        if isinstance(value, str) and value.strip():
            try:
                url = urlsplit(value)
                valid = url.scheme in ("http", "https") and url.hostname and url.username is None and not re.search(r"\s", value)
            except ValueError:
                valid = False
            if not valid:
                issue(issues, "module." + key, "Use an absolute HTTP(S) URL without embedded credentials or whitespace")
    for key in ("labels", "tags"):
        if key in module and (not isinstance(module[key], list) or any(not isinstance(item, str) for item in module[key])):
            issue(issues, "module." + key, "Use an array of strings")
    for key in ("type", "requiredRuntimeFeatures"):
        values = string_list(module.get(key, []), "module." + key, issues, allow_string=True)
        if key == "requiredRuntimeFeatures":
            for feature in values:
                if feature not in (EXTENSION_FEATURES if extension else FEATURES):
                    issue(issues, "module.requiredRuntimeFeatures", f"Unsupported host feature: {feature}")
    capabilities = string_list(module.get("capabilities", []), "module.capabilities", issues)
    if len(set(capabilities)) != len(capabilities) or any(item not in CAPABILITIES for item in capabilities) or not set(CAPABILITIES[:2]).issubset(capabilities):
        issue(issues, "module.capabilities", "Declare unique supported methods, including searchTracks and getTrackStreamUrl")
    minimum = module.get("minAppVersion", "")
    if isinstance(minimum, str) and minimum.strip():
        if not re.fullmatch(r"[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}", minimum):
            issue(issues, "module.minAppVersion", "Use major.minor.patch with 1–6 digits per part")
        elif app_version and tuple(map(int, app_version.split("."))) < tuple(map(int, minimum.split("."))):
            issue(issues, "module.minAppVersion", f"Requires {minimum}; selected target is {app_version}")
    script = entries.get("provider.js")
    if script is not None:
        try:
            if not script.decode("utf-8-sig").strip():
                issue(issues, "provider.js", "The entry script is empty")
        except UnicodeError:
            issue(issues, "provider.js", "Encode the script as UTF-8")
    if "icon" in module:
        if module["icon"] != "icon.png":
            issue(issues, "module.icon", "The only supported icon path is icon.png")
        icon = entries.get("icon.png")
        if icon is not None:
            if len(icon) < 24 or icon[:8] != b"\x89PNG\r\n\x1a\n" or icon[12:16] != b"IHDR":
                issue(issues, "icon.png", "Use a PNG with a valid IHDR header")
            elif not all(1 <= size <= 1024 for size in struct.unpack(">II", icon[16:24])):
                issue(issues, "icon.png", "Width and height must each be 1–1024 pixels")
    elif "icon.png" in entries or "iconSha256" in manifest:
        issue(issues, "module.icon", "The package contains an undeclared icon or icon checksum")
    if verify_hashes:
        for name, key in (("provider.js", "sha256"), ("icon.png", "iconSha256")):
            if name in entries and hashlib.sha256(entries[name]).hexdigest() != manifest.get(key):
                issue(issues, key, f"Checksum does not match {name}")
    return validate_settings(module, issues, extension)


def prepare(directory, draft=None, app_version=None):
    manifest = parse_json(read_bounded(directory / "manifest.json", LIMITS["manifest.json"])) if draft is None else parse_json(encode_json(draft))
    entries, issues = {}, []
    module = manifest.get("module", {}) if isinstance(manifest, dict) else {}
    names = ["provider.js"] + (["icon.png"] if isinstance(module, dict) and module.get("icon") == "icon.png" else [])
    for name in names:
        try:
            entries[name] = read_bounded(directory / name, LIMITS[name])
        except (OSError, ValueError) as error:
            issue(issues, name, str(error))
    if isinstance(manifest, dict):
        if "provider.js" in entries:
            manifest["sha256"] = hashlib.sha256(entries["provider.js"]).hexdigest()
        if "icon.png" in entries:
            manifest["iconSha256"] = hashlib.sha256(entries["icon.png"]).hexdigest()
        else:
            manifest.pop("iconSha256", None)
    ignored = sorted(item.name for item in directory.iterdir() if item.name not in {"manifest.json", *names})
    if "icon.png" in ignored:
        issue(issues, "icon.png", "This file is undeclared and will not be included", "warning")
    return assess(manifest, entries, issues, directory, app_version, ignored=ignored)


def assess(manifest, entries, issues, origin, app_version=None, verify_hashes=False, ignored=None, manifest_bytes=None):
    settings = validate(manifest, entries, issues, app_version, verify_hashes)
    manifest_bytes = encode_json(manifest) if manifest_bytes is None else manifest_bytes
    if len(manifest_bytes) > LIMITS["manifest.json"]:
        issue(issues, "manifest.json", "The packaged manifest exceeds 128 KiB")
    module = manifest.get("module", {}) if isinstance(manifest, dict) else {}
    if not isinstance(module, dict):
        module = {}
    files = {"manifest.json": manifest_bytes, **entries}
    metadata = {}
    for key in ("id", "name", "displayName", "version", "author", "description", "homepage", "repository", "license", "icon",
                "minAppVersion", "type", "labels", "tags", "capabilities", "requiredRuntimeFeatures"):
        value = module.get(key, "")
        metadata[key] = value if isinstance(value, str) or (isinstance(value, list) and all(isinstance(item, str) for item in value)) else "<invalid>"
    runtime = manifest.get("runtime", "archivetune") if isinstance(manifest, dict) else "<invalid>"
    report = {"operation": "validate", "status": "error" if any(item["level"] == "error" for item in issues) else "valid",
              "source": str(origin), "targetAppVersion": app_version, "runtime": runtime if isinstance(runtime, str) else "<invalid>", "metadata": metadata,
              "settings": settings, "files": [{"name": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()} for name, data in files.items()],
              "ignoredFiles": ignored or [], "issues": issues, "runtimeVerification": "not_performed", "runtimeNote": RUNTIME_NOTE}
    return report, files


def inspect_package(path, app_version=None):
    data = read_bounded(path, PACKAGE_LIMIT)
    entries, issues = {}, []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        members = archive.infolist()
        if len(members) not in (2, 3):
            raise ValueError("Expected exactly two required files and at most one declared icon")
        for member in members:
            if member.filename not in LIMITS or member.is_dir() or member.filename in entries:
                issue(issues, "archive", f"Unsupported or duplicate entry: {member.filename}")
                continue
            if member.flag_bits & 1 or member.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                issue(issues, member.filename, "Encrypted or unsupported compression")
                continue
            if member.file_size > LIMITS[member.filename]:
                issue(issues, member.filename, "Uncompressed entry exceeds its size limit")
                continue
            try:
                with archive.open(member) as source:
                    entry = source.read(LIMITS[member.filename] + 1)
            except (EOFError, zipfile.BadZipFile, zlib.error) as error:
                issue(issues, member.filename, f"Damaged archive entry: {error}")
                continue
            if len(entry) > LIMITS[member.filename]:
                issue(issues, member.filename, "Uncompressed entry exceeds its size limit")
            else:
                entries[member.filename] = entry
    for name in ("manifest.json", "provider.js"):
        if name not in entries:
            issue(issues, name, "Required entry is missing")
    manifest_bytes = entries.pop("manifest.json", None)
    manifest = parse_json(manifest_bytes) if manifest_bytes is not None else {}
    module = manifest.get("module", {}) if isinstance(manifest, dict) else {}
    if isinstance(module, dict) and "icon" in module and "icon.png" not in entries:
        issue(issues, "icon.png", "Declared icon is missing")
    report, _ = assess(manifest, entries, issues, path, app_version, verify_hashes=True, manifest_bytes=manifest_bytes)
    report.update(operation="inspect", packageBytes=len(data), packageSha256=hashlib.sha256(data).hexdigest())
    return report


def package_files(report, files, output):
    if output.suffix.lower() != ".arch":
        raise ValueError("The output filename must end in .arch")
    if report["status"] == "error":
        return report
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    package = buffer.getvalue()
    if len(package) > PACKAGE_LIMIT:
        issue(report["issues"], "archive", "The compressed package exceeds 4 MiB")
        report["status"] = "error"
        return report
    atomic_write(output, package)
    report.update(operation="package", status="created", output=str(output), packageBytes=len(package),
                  packageSha256=hashlib.sha256(package).hexdigest(), uncompressedBytes=sum(len(value) for value in files.values()))
    return report


def safe_text(value):
    return re.sub(r"[\x00-\x08\x0b-\x1f\x7f-\x9f]", "", str(value))


def heading(title):
    width = min(shutil.get_terminal_size((88, 24)).columns, 100)
    print("\n" + safe_text(title))
    print("-" * max(24, width))


def rows(values):
    columns = max(24, min(shutil.get_terminal_size((88, 24)).columns, 100))
    label_width = min(22, max(8, columns // 3))
    for label, value in values:
        label = safe_text(label).replace("\n", " ")
        prefix = f"  {label:<{label_width}} "
        if len(label) > label_width:
            print("  " + label)
            prefix = "    "
        lines = textwrap.wrap(safe_text(value).replace("\n", " / "), width=max(8, columns - len(prefix))) or [""]
        for index, line in enumerate(lines):
            print((prefix if index == 0 else " " * len(prefix)) + line)


def show_report(report):
    heading({"created": "PACKAGE CREATED", "error": "VALIDATION FAILED", "valid": "LOCAL VALIDATION PASSED"}[report["status"]])
    metadata = report["metadata"]
    rows([(key, metadata[key]) for key in ("id", "displayName", "name", "version", "author", "description", "homepage", "repository", "license", "icon", "minAppVersion") if metadata.get(key)])
    rows([("source", report["source"]), ("runtime", report["runtime"]), ("target app", report["targetAppVersion"] or "Not supplied; app-version compatibility not checked")])
    for key in ("type", "labels", "tags", "capabilities", "requiredRuntimeFeatures"):
        if metadata.get(key):
            rows([(key, metadata[key])])
    heading("Files and integrity")
    for entry in report["files"]:
        rows([("file", f"{entry['name']} ({entry['bytes']:,} bytes)"), ("SHA-256", entry["sha256"])])
    if "packageBytes" in report:
        rows([("package size", f"{report['packageBytes']:,} bytes"), ("package SHA-256", report["packageSha256"])])
    if "uncompressedBytes" in report:
        saved = 100 * (1 - report["packageBytes"] / report["uncompressedBytes"])
        comparison = f"{saved:.1f}% smaller than uncompressed entries" if saved >= 0 else f"{-saved:.1f}% larger due to ZIP overhead"
        rows([("compression", comparison)])
    heading("Configuration")
    show_settings(report["settings"])
    if report["ignoredFiles"]:
        heading("Excluded workspace files")
        rows([("not packaged", ", ".join(report["ignoredFiles"]))])
    if report["issues"]:
        show_issues(report["issues"])
    heading("Runtime verification")
    rows([("not performed", report["runtimeNote"])])
    if report["status"] == "created":
        heading("Install")
        rows([("output", report["output"]), ("next step", "Settings > Modules > Add > Import file. Review, configure, enable, then check actual playback.")])


def show_settings(settings):
    if not settings:
        print("  No settings to display.")
    for field in settings:
        default = "<redacted>" if field["sensitive"] else json.dumps(field["default"], ensure_ascii=True)
        rows([(field["key"], f"{field['type']} | {'required' if field['required'] else 'optional'} | {field['options']} options"), ("default", default)])
        for option in field["choices"]:
            rows([("option", f"{option['label']} = {json.dumps(option['value'], ensure_ascii=True)}")])
            if option["description"]:
                rows([("description", option["description"])])


def show_issues(issues):
    heading("Validation findings")
    for item in issues:
        rows([(item["level"].upper(), item["field"]), ("action", item["message"])])


def confirm(prompt):
    return input(prompt + " [y/N]: ").strip().lower() in ("y", "yes")


def input_path(prompt, default=""):
    value = input(f"{prompt}{' [' + safe_text(default) + ']' if default else ''}: ").strip() or str(default)
    if not value:
        raise ValueError("A path is required")
    return Path(value.strip('"')).expanduser().resolve()


def edit_metadata(manifest):
    module = manifest["module"]
    fields = ("runtime", "id", "name", "displayName", "version", "author", "description", "homepage", "repository", "license", "minAppVersion",
              "type", "labels", "capabilities", "requiredRuntimeFeatures", "icon")
    heading("Edit provider metadata")
    for index, key in enumerate(fields, 1):
        rows([(str(index) + ". " + key, manifest.get(key, "archivetune") if key == "runtime" else module.get(key, "(not set)"))])
    selected = input("Field number, or Enter to return: ").strip()
    if not selected:
        return
    if not selected.isdigit() or not 1 <= int(selected) <= len(fields):
        raise ValueError("Choose one of the listed field numbers")
    key = fields[int(selected) - 1]
    print("Use '-' to remove a field; an empty entry keeps the current value.")
    if key in ("type", "labels", "capabilities", "requiredRuntimeFeatures"):
        print("Enter comma-separated values.")
    if key == "icon":
        print("Use icon.png after placing the PNG in the workspace; '-' removes the declaration.")
    if key == "runtime":
        print("Use archivetune for module.exports, or extension for registerExtension.")
    value = input(key + ": ").strip()
    target = manifest if key == "runtime" else module
    if value == "-":
        target.pop(key, None)
    elif value:
        target[key] = [part.strip() for part in value.split(",") if part.strip()] if key in ("type", "labels", "capabilities", "requiredRuntimeFeatures") else value


def edit_configuration(manifest):
    keys = ("settings", "qualityOptions", "defaultQuality", "qualityLabel")
    if manifest.get("runtime") == "extension":
        keys += ("permissions", "signedSession")
    heading("Edit configuration declarations")
    for index, key in enumerate(keys, 1):
        print(f"  {index}. {key}")
    print("Declare provider options here, not personal account credentials.")
    selected = input("Field number, or Enter to return: ").strip()
    if not selected:
        return
    if not selected.isdigit() or not 1 <= int(selected) <= len(keys):
        raise ValueError("Choose one of the listed field numbers")
    key = keys[int(selected) - 1]
    if key == "settings":
        issues = []
        show_settings(validate_settings(manifest["module"], issues, manifest.get("runtime") == "extension"))
        if issues:
            show_issues(issues)
    else:
        rows([("current", json.dumps(redact(manifest["module"].get(key)), ensure_ascii=True))])
    print("Enter a JSON value, @path/to/value.json, or '-' to remove the declaration.")
    value = input(key + ": ").strip()
    if value == "-":
        manifest["module"].pop(key, None)
    elif value:
        data = read_bounded(Path(value[1:].strip('"')).expanduser().resolve(), LIMITS["manifest.json"]) if value.startswith("@") else value.encode("utf-8")
        manifest["module"][key] = parse_json(data)


def report_destination(path, protected):
    if path.suffix.lower() != ".json" or path in protected:
        raise ValueError("The report must be a separate .json file, not an input or package")
    return path


def interactive(directory=None, output=None, app_version=None):
    while True:
        heading("ArchiveTune Module Studio | .arch v1")
        directory = directory or input_path("Provider workspace")
        try:
            original = parse_json(read_bounded(directory / "manifest.json", LIMITS["manifest.json"]))
        except FileNotFoundError:
            if not directory.is_dir():
                print("Choose an existing provider directory.")
                directory = None
                continue
            if not confirm("No manifest found. Create a draft in this existing workspace?"):
                directory = None
                continue
            original = None
        draft = copy.deepcopy(original) if original is not None else {"format": "archivetune.module", "formatVersion": 1, "apiVersion": 1, "entry": "provider.js",
                "module": {"id": input("Stable module ID: ").strip(), "name": input("Provider name: ").strip(), "version": "1.0.0", "capabilities": list(CAPABILITIES[:2])}}
        if not isinstance(draft, dict) or not isinstance(draft.get("module"), dict):
            raise ValueError("The manifest must contain a module object; fix manifest.json before opening the editor")
        last_report = None
        print("provider.js must contain your own implementation. No provider code is generated.")
        while True:
            heading("Workspace" + (" | unsaved manifest changes" if draft != original else ""))
            rows([("directory", directory), ("module", draft["module"].get("displayName") or draft["module"].get("name", "(unnamed)"))])
            print("  1  Review and validate\n  2  Edit metadata\n  3  Edit settings / quality\n  4  Save manifest\n  5  Package .arch\n  6  Inspect an .arch file\n  7  Change workspace\n  8  Export last report\n  0  Exit")
            choice = input("Action: ").strip()
            try:
                if choice in ("0", "7"):
                    if draft != original and not confirm("Discard unsaved manifest changes?"):
                        continue
                    if choice == "0":
                        return 0
                    directory, output = None, None
                    break
                if choice == "2":
                    edit_metadata(draft)
                elif choice == "3":
                    edit_configuration(draft)
                elif choice == "6":
                    last_report = inspect_package(input_path("Package path", output or ""), app_version)
                    show_report(last_report)
                elif choice == "8":
                    if last_report is None:
                        print("Validate, package, or inspect a module first.")
                        continue
                    protected = {(directory / name).resolve() for name in LIMITS} | {output}
                    if last_report["operation"] == "inspect":
                        protected.add(Path(last_report["source"]).resolve())
                    if "output" in last_report:
                        protected.add(Path(last_report["output"]).resolve())
                    target = report_destination(input_path("JSON report path"), protected)
                    if not target.exists() or confirm(f"Replace {safe_text(target)}?"):
                        atomic_write(target, encode_json(last_report))
                        print("Report saved: " + safe_text(target))
                elif choice == "4":
                    data = encode_json(draft)
                    issues = []
                    validate(parse_json(data), {}, issues, app_version)
                    if len(data) > LIMITS["manifest.json"]:
                        issue(issues, "manifest.json", "The manifest exceeds 128 KiB")
                    if issues:
                        show_issues(issues)
                    if any(item["level"] == "error" for item in issues):
                        continue
                    manifest_path = directory / "manifest.json"
                    current = parse_json(read_bounded(manifest_path, LIMITS["manifest.json"])) if manifest_path.exists() else None
                    if current != original and not confirm("manifest.json changed on disk. Replace it with this draft?"):
                        continue
                    if confirm("Save the manifest? Script and icon files are not checked by this action"):
                        atomic_write(manifest_path, data)
                        original = copy.deepcopy(draft)
                        print("Manifest saved. Source script and icon were not modified.")
                elif choice in ("1", "5"):
                    last_report, files = prepare(directory, draft, app_version)
                    show_report(last_report)
                    if last_report["status"] == "error" or choice == "1":
                        continue
                    if choice == "5":
                        identity = str(draft["module"].get("id", "module"))
                        version = re.sub(r"[^a-zA-Z0-9._-]", "_", str(draft["module"].get("version", "1")))
                        target = input_path("Output package", output or directory.parent / f"{identity}-{version}.arch")
                        if target.exists() and not confirm(f"Replace {safe_text(target)}?"):
                            continue
                        if not confirm("Write this package?"):
                            continue
                        last_report = package_files(last_report, files, target)
                        show_report(last_report)
                        output = target
                        if draft != original:
                            print("The package used your draft. Choose Save manifest to persist those workspace edits.")
                else:
                    print("Choose an action from the menu.")
            except (OSError, ValueError, zipfile.BadZipFile, NotImplementedError) as error:
                print("Operation failed: " + safe_text(error))


def main():
    parser = argparse.ArgumentParser(description="Author, validate, inspect, and package ArchiveTune .arch modules. No arguments opens the interactive terminal studio.")
    parser.add_argument("directory", nargs="?", type=Path, help="Provider workspace with manifest.json and provider.js")
    parser.add_argument("output", nargs="?", type=Path, help="Destination .arch file; scripted use replaces an existing file")
    parser.add_argument("--interactive", action="store_true", help="Open the terminal studio, optionally at directory/output")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check", action="store_true", help="Validate inputs without writing a package")
    modes.add_argument("--inspect", type=Path, metavar="PACKAGE", help="Inspect a package and verify its entries/checksums")
    parser.add_argument("--app-version", help="Check minimum app version against major.minor.patch")
    parser.add_argument("--json", action="store_true", help="Print a redacted machine-readable report")
    parser.add_argument("--report", type=Path, metavar="FILE.json", help="Also save the redacted report to a JSON file")
    args = parser.parse_args()
    if args.app_version and not re.fullmatch(r"[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}", args.app_version):
        parser.error("--app-version must use major.minor.patch with 1–6 digits per part")
    use_studio = args.interactive or not (args.directory or args.inspect)
    if use_studio and (args.check or args.inspect or args.json or args.report):
        parser.error("Interactive mode cannot be combined with --check, --inspect, --json, or --report")
    if use_studio and not sys.stdin.isatty():
        parser.error("Interactive mode needs a terminal; supply directory/output or use --check/--inspect")
    if args.inspect and (args.directory or args.output):
        parser.error("--inspect takes its package path without directory/output")
    if args.check and args.output:
        parser.error("--check does not accept an output package path")
    if not use_studio and not args.inspect and not args.check and not args.output:
        parser.error("Supply an output .arch path, or select --check/--interactive")
    try:
        directory = args.directory.expanduser().resolve() if args.directory else None
        output = args.output.expanduser().resolve() if args.output else None
        if use_studio:
            return interactive(directory, output, args.app_version)
        protected = {output, args.inspect.expanduser().resolve() if args.inspect else None}
        if directory:
            protected.update((directory / name).resolve() for name in LIMITS)
        report_path = report_destination(args.report.expanduser().resolve(), protected) if args.report else None
        if args.inspect:
            report = inspect_package(args.inspect.expanduser().resolve(), args.app_version)
        else:
            report, files = prepare(directory, app_version=args.app_version)
            if not args.check:
                report = package_files(report, files, output)
        if report_path:
            try:
                atomic_write(report_path, encode_json(report))
                report["reportFile"] = str(report_path)
            except OSError as error:
                issue(report["issues"], "report", f"Report could not be saved: {error}")
        if args.json:
            print(json.dumps(report, ensure_ascii=True, allow_nan=False))
        else:
            show_report(report)
            if "reportFile" in report:
                rows([("JSON report", report_path)])
        return 1 if any(item["level"] == "error" for item in report["issues"]) else 0
    except (EOFError, KeyboardInterrupt):
        print("\nCancelled. Completed writes, if any, remain on disk.", file=sys.stderr)
        return 130
    except (OSError, ValueError, zipfile.BadZipFile, NotImplementedError) as error:
        if args.json:
            print(json.dumps({"status": "error", "issues": [{"level": "error", "field": "input/output", "message": str(error)}]}, ensure_ascii=True))
        else:
            print("Error: " + safe_text(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")
    sys.exit(main())

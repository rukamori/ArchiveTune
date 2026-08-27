# ArchiveTune (2026)
# © Rukamori — github.com/rukamori
# GPL-3.0 License | Contributors: see git history

import base64
import binascii
import importlib
import json
import os
import re
import sys
import tempfile
import threading
import urllib.parse


_runtime_path = None
_runtime_version = None
_archive_loaded = False
_runtime_lock = threading.Lock()


def _ensure_runtime(runtime_path):
    global _runtime_path, _runtime_version, _archive_loaded
    with _runtime_lock:
        if _runtime_version is not None:
            return
        normalized_path = runtime_path if runtime_path and os.path.isfile(runtime_path) else None
        try:
            version = _load_runtime(normalized_path)
        except Exception:
            if normalized_path is None:
                raise
            _purge_runtime(normalized_path)
            version = _load_runtime(None)
            normalized_path = None
        _runtime_path = normalized_path
        _runtime_version = version
        _archive_loaded = normalized_path is not None


def _load_runtime(runtime_path):
    if runtime_path:
        sys.path.insert(0, runtime_path)
    from yt_dlp.version import __version__
    _register_android_jsc_provider()
    return __version__


def _purge_runtime(runtime_path):
    sys.path = [entry for entry in sys.path if entry != runtime_path]
    for module_name in tuple(sys.modules):
        if (
            module_name == "yt_dlp"
            or module_name.startswith("yt_dlp.")
            or module_name == "yt_dlp_ejs"
            or module_name.startswith("yt_dlp_ejs.")
        ):
            sys.modules.pop(module_name, None)
    importlib.invalidate_caches()


def _register_android_jsc_provider():
    from java import jclass
    from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
    from yt_dlp.extractor.youtube.jsc.provider import (
        register_preference as register_jsc_preference,
        register_provider as register_jsc_provider,
    )
    from yt_dlp.extractor.youtube.pot._provider import BuiltinIEContentProvider
    from yt_dlp.extractor.youtube.pot.provider import (
        PoTokenContext,
        PoTokenProvider,
        PoTokenProviderRejectedRequest,
        PoTokenResponse,
        register_preference as register_pot_preference,
        register_provider as register_pot_provider,
    )
    from yt_dlp.extractor.youtube.pot.utils import (
        ContentBindingType,
        get_webpo_content_binding,
    )

    java_runtime = jclass(
        "moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpJavaScriptRuntime"
    )

    @register_jsc_provider
    class ArchiveTuneJCP(EJSBaseJCP, BuiltinIEContentProvider):
        PROVIDER_NAME = "archivetune"
        JS_RUNTIME_NAME = "archivetune"

        def is_available(self):
            return True

        def _run_js_runtime(self, source):
            return str(java_runtime.evaluate(source))

    @register_jsc_preference(ArchiveTuneJCP)
    def archive_tune_preference(provider, requests):
        return 2000

    @register_pot_provider
    class ArchiveTunePTP(PoTokenProvider, BuiltinIEContentProvider):
        PROVIDER_NAME = "archivetune"
        _SUPPORTED_CLIENTS = ("WEB_CREATOR",)
        _SUPPORTED_CONTEXTS = (PoTokenContext.GVS,)
        _SUPPORTED_EXTERNAL_REQUEST_FEATURES = ()

        def is_available(self):
            return bool(
                self._configuration_arg("gvs_session", casesense=True)
                or self._configuration_arg("gvs_video", casesense=True)
            )

        def _real_request_pot(self, request):
            if not request.is_authenticated:
                raise PoTokenProviderRejectedRequest(
                    "ArchiveTune tokens require authenticated YouTube cookies"
                )

            content_binding, binding_type = get_webpo_content_binding(request)
            if binding_type == ContentBindingType.DATASYNC_ID:
                token_key = "gvs_session"
                expected_binding_key = "data_sync_id"
            elif binding_type == ContentBindingType.VIDEO_ID:
                token_key = "gvs_video"
                expected_binding_key = "video_id"
            else:
                raise PoTokenProviderRejectedRequest(
                    "ArchiveTune has no token for the requested content binding"
                )

            expected_binding = self._configuration_arg(
                expected_binding_key,
                default=[None],
                casesense=True,
            )[0]
            if not content_binding or content_binding != expected_binding:
                raise PoTokenProviderRejectedRequest(
                    "ArchiveTune PO-token content binding does not match the yt-dlp request"
                )

            po_token = self._configuration_arg(
                token_key,
                default=[None],
                casesense=True,
            )[0]
            if not po_token:
                raise PoTokenProviderRejectedRequest(
                    "ArchiveTune has no token for the requested content binding"
                )

            self.logger.info(
                "Supplying an authenticated GVS PO Token for WEB_CREATOR"
            )
            return PoTokenResponse(po_token=po_token, expires_at=-1)

    @register_pot_preference(ArchiveTunePTP)
    def archive_tune_pot_preference(provider, request):
        return 2000

def is_runtime_archive_loaded():
    return _archive_loaded


def prewarm_runtime(runtime_path):
    _ensure_runtime(runtime_path)


def _write_cookie_file(cookie_header, directory):
    if not cookie_header:
        return None
    os.makedirs(directory, exist_ok=True)
    handle = tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        prefix="auth_",
        suffix=".cookies",
        dir=directory,
        delete=False,
    )
    try:
        os.chmod(handle.name, 0o600)
        handle.write("# Netscape HTTP Cookie File\n")
        for raw_cookie in cookie_header.split(";"):
            name, separator, value = raw_cookie.strip().partition("=")
            if (
                not separator
                or not name
                or any(character in name or character in value for character in "\r\n\t")
            ):
                continue
            handle.write(
                ".youtube.com\tTRUE\t/\tTRUE\t0\t{}\t{}\n".format(name, value)
            )
        return handle.name
    finally:
        handle.close()


def _extract_expiry(url):
    try:
        values = urllib.parse.parse_qs(urllib.parse.urlparse(url).query).get("expire")
        return int(values[0]) * 1000 if values else 0
    except (TypeError, ValueError, IndexError):
        return 0


def _mime_type(format_info):
    mime_type = format_info.get("mime_type")
    if mime_type:
        return mime_type.split(";", 1)[0]
    extension = (format_info.get("audio_ext") or format_info.get("ext") or "").lower()
    if extension in ("m4a", "mp4"):
        return "audio/mp4"
    if extension in ("webm", "weba"):
        return "audio/webm"
    if extension == "ogg" or extension == "opus":
        return "audio/ogg"
    return "audio/" + extension if extension else "audio/webm"


def _bitrate(format_info):
    value = format_info.get("abr") or format_info.get("tbr") or 0
    try:
        return int(float(value) * 1000)
    except (TypeError, ValueError):
        return 0


def _choose_format(formats, quality, network_metered, pinned_format_id):
    candidates = []
    for format_info in formats or ():
        url = format_info.get("url")
        audio_codec = format_info.get("acodec")
        video_codec = format_info.get("vcodec")
        protocol = (format_info.get("protocol") or "").lower()
        if not url or not audio_codec or audio_codec == "none":
            continue
        if video_codec not in (None, "none"):
            continue
        if protocol and protocol not in ("http", "https"):
            continue
        if format_info.get("has_drm"):
            continue
        candidates.append(format_info)

    if not candidates:
        raise RuntimeError("yt-dlp returned no direct, audio-only format")

    if pinned_format_id:
        pinned = next(
            (
                item
                for item in candidates
                if str(item.get("format_id")) == str(pinned_format_id)
            ),
            None,
        )
        if pinned is not None:
            return pinned

    effective_quality = quality
    if quality == "AUTO":
        effective_quality = "HIGH" if network_metered else "HIGHEST"
    target = {"LOW": 70000, "HIGH": 160000}.get(effective_quality)

    candidates.sort(
        key=lambda item: (
            _bitrate(item),
            int(item.get("asr") or 0),
            str(item.get("format_id") or ""),
        )
    )
    if target is None:
        return candidates[-1]
    not_above_target = [item for item in candidates if _bitrate(item) <= target]
    return not_above_target[-1] if not_above_target else candidates[0]


class _DiagnosticYtDlpLogger:
    _playability_pattern = re.compile(
        r"([a-z0-9_]+) player response playability status: ([A-Z_]+)"
    )

    def __init__(self, expect_authentication):
        self._expect_authentication = expect_authentication
        self._authentication_status = None
        self._po_provider_available = False
        self._javascript_provider_available = False
        self._javascript_provider_used = False
        self._javascript_failure = False
        self._playability_statuses = []
        self._format_issues = []

    def debug(self, message):
        self._record(message)

    def info(self, message):
        self._record(message)

    def warning(self, message):
        self._record(message)

    def error(self, message):
        self._record(message)

    def _record(self, message):
        text = str(message)
        if "Found YouTube account cookies" in text:
            self._authentication_status = "recognized"
        elif "provided YouTube account cookies are no longer valid" in text:
            self._authentication_status = "rotated"

        lowered = text.lower()
        if "pot:archivetune" in lowered or "authenticated gvs po token" in lowered:
            self._po_provider_available = True
        if "js challenge providers" in lowered and "archivetune" in lowered:
            self._javascript_provider_available = True
        if "solving js challenges using archivetune" in lowered:
            self._javascript_provider_used = True
        if (
            "no supported javascript runtime" in lowered
            or "javascript challenge solving failed" in lowered
            or "signature solving failed" in lowered
            or "n challenge solving failed" in lowered
            or "javascript challenge execution failed" in lowered
            or (
                "error solving" in lowered
                and 'using "archivetune" provider' in lowered
            )
        ):
            self._javascript_failure = True

        for marker, issue in (
            ("forcing sabr streaming", "sabr_only"),
            ("formats have been skipped as they are missing a url", "missing_url"),
            ("only images are available", "no_media_formats"),
            ("formats require a gvs po token which was not provided", "missing_gvs_pot"),
        ):
            if marker in lowered and issue not in self._format_issues:
                self._format_issues.append(issue)

        match = self._playability_pattern.search(text)
        if match and len(self._playability_statuses) < 8:
            status = "{}={}".format(match.group(1), match.group(2))
            if status not in self._playability_statuses:
                self._playability_statuses.append(status)

    def summary(self):
        diagnostics = []
        if self._expect_authentication:
            diagnostics.append(
                "account_cookies=" + (self._authentication_status or "not_recognized")
            )
        diagnostics.append(
            "po_provider=" + ("archivetune" if self._po_provider_available else "not_used")
        )
        if self._javascript_failure:
            javascript_status = "failed"
        elif self._javascript_provider_used:
            javascript_status = "archivetune"
        elif self._javascript_provider_available:
            javascript_status = "available_not_used"
        else:
            javascript_status = "not_available"
        diagnostics.append("javascript_provider=" + javascript_status)
        if self._playability_statuses:
            diagnostics.append("playability=" + ",".join(self._playability_statuses))
        if self._format_issues:
            diagnostics.append("formats=" + ",".join(self._format_issues))
        return "; ".join(diagnostics)


def _extract_info(
    youtube_dl,
    url,
    extractor_args,
    cookie_file=None,
    diagnostics=True,
):
    logger = _DiagnosticYtDlpLogger(expect_authentication=cookie_file is not None)
    options = {
        "quiet": True,
        "verbose": diagnostics,
        "noplaylist": True,
        "skip_download": True,
        "socket_timeout": 15,
        "retries": 1,
        "extractor_retries": 1,
        "fragment_retries": 1,
        "extractor_args": extractor_args,
        "js_runtimes": {},
        "remote_components": set(),
        "logger": logger,
    }
    if cookie_file:
        options["cookiefile"] = cookie_file
    try:
        with youtube_dl(options) as downloader:
            return downloader.extract_info(url, download=False)
    except Exception as error:
        from yt_dlp.utils import DownloadError

        if not isinstance(error, DownloadError):
            raise
        raise DownloadError(
            "{} [ArchiveTune diagnostics: {}]".format(error, logger.summary())
        ) from error


def _normalize_po_token(value):
    unpadded = (
        urllib.parse.unquote(value.strip())
        .replace("+", "-")
        .replace("/", "_")
        .rstrip("=")
    )
    padded = unpadded + "=" * ((4 - len(unpadded) % 4) % 4)
    try:
        decoded = base64.b64decode(padded, altchars=b"-_", validate=True)
    except (binascii.Error, ValueError) as error:
        raise ValueError("PO Token must be a base64url-encoded string") from error
    if not decoded:
        raise ValueError("PO Token must not be empty")
    return base64.urlsafe_b64encode(decoded).decode("ascii")


def _build_extractor_args(request, authenticated, skip_initial_data=False):
    youtube_args = {
        "skip": ["hls", "dash", "translated_subs"],
    }
    if skip_initial_data:
        youtube_args["player_skip"] = ["initial_data"]
    extractor_args = {"youtube": youtube_args}
    if not authenticated:
        return extractor_args

    youtube_args["player_client"] = [
        "default",
        "-tv_downgraded",
        "web_embedded",
    ]

    provider_args = {}
    data_sync_id = request.get("data_sync_id")
    session_token = request.get("po_token_web_creator_gvs_session")
    if data_sync_id and session_token:
        provider_args["data_sync_id"] = [data_sync_id]
        provider_args["gvs_session"] = [_normalize_po_token(session_token)]

    video_token = request.get("po_token_web_creator_gvs_video")
    if video_token:
        provider_args["video_id"] = [request["media_id"]]
        provider_args["gvs_video"] = [_normalize_po_token(video_token)]

    if "gvs_session" in provider_args or "gvs_video" in provider_args:
        extractor_args["youtubepot-archivetune"] = provider_args
    return extractor_args


def _has_required_song_metadata(info):
    return bool(
        info.get("title")
        and info.get("duration")
        and info.get("thumbnail")
    )


def _extract_audio_info(youtube_dl, url, request, cookie_file):
    from yt_dlp.utils import DownloadError

    authenticated = cookie_file is not None
    fast_extractor_args = _build_extractor_args(
        request,
        authenticated=authenticated,
        skip_initial_data=True,
    )
    try:
        info = _extract_info(
            youtube_dl,
            url,
            fast_extractor_args,
            cookie_file,
            diagnostics=False,
        )
        selected = _choose_format(
            info.get("formats"),
            request["quality"],
            bool(request.get("network_metered")),
            request.get("pinned_format_id"),
        )
        if _has_required_song_metadata(info):
            return info, selected
    except (DownloadError, RuntimeError):
        pass

    extractor_args = _build_extractor_args(
        request,
        authenticated=authenticated,
    )
    info = _extract_info(
        youtube_dl,
        url,
        extractor_args,
        cookie_file,
    )
    selected = _choose_format(
        info.get("formats"),
        request["quality"],
        bool(request.get("network_metered")),
        request.get("pinned_format_id"),
    )
    return info, selected


def resolve_audio(request_json, runtime_path, cookie_directory):
    _ensure_runtime(runtime_path)
    from yt_dlp import YoutubeDL

    request = json.loads(request_json)
    cookie_file = _write_cookie_file(request.get("cookie"), cookie_directory)
    try:
        url = "https://www.youtube.com/watch?v=" + request["media_id"]
        info, selected = _extract_audio_info(
            YoutubeDL,
            url,
            request,
            cookie_file,
        )
        stream_url = selected["url"]
        content_length = selected.get("filesize") or 0
        stream_headers = {}
        for source_headers in (
            info.get("http_headers") or {},
            selected.get("http_headers") or {},
        ):
            for header_name, header_value in source_headers.items():
                if header_value is not None:
                    stream_headers[str(header_name)] = str(header_value)
        stream_headers.pop("Accept-Encoding", None)
        result = {
            "url": stream_url,
            "headers": stream_headers,
            "format_id": selected.get("format_id") or "",
            "mime_type": _mime_type(selected),
            "codecs": selected.get("acodec") or "",
            "bitrate": _bitrate(selected),
            "sample_rate": int(selected.get("asr") or 0),
            "content_length": int(content_length or 0),
            "expires_at_ms": _extract_expiry(stream_url),
            "title": info.get("title"),
            "duration_seconds": int(info.get("duration") or 0),
            "thumbnail_url": info.get("thumbnail"),
            "runtime_version": _runtime_version,
            "archive_loaded": _archive_loaded,
        }
        return json.dumps(result, separators=(",", ":"))
    finally:
        if cookie_file:
            try:
                os.remove(cookie_file)
            except FileNotFoundError:
                pass

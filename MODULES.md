# ArchiveTune Module Authoring

This guide defines the native `.arch` package format and module API version 1.
It applies to ArchiveTune builds containing `ModulePackageReader`, older releases
do not acquire ZIP package support by renaming a file. The package and API version
numbers are independent of your provider's own version.

A module supplies alternative audio for an existing song. ArchiveTune keeps the
song's identity, displayed metadata, library entry, and queue position. Modules
do not add a source picker, music search destination, or browsing screen.

No provider, account, endpoint, or catalog is bundled by this specification. You
must implement the integration with your chosen audio source. This guide does
not provide an audio service or a working lossless provider.

## Contents

- [Package format](#package-format)
- [Manifest](#manifest)
- [Settings](#settings)
- [JavaScript entry point](#javascript-entry-point)
- [Track search](#track-search)
- [Stream resolution and lossless audio](#stream-resolution-and-lossless-audio)
- [Host APIs and limits](#host-apis-and-limits)
- [Package and install](#package-and-install)
- [URLs, catalogs, and updates](#urls-catalogs-and-updates)
- [Failures and diagnostics](#failures-and-diagnostics)
- [Release checklist](#release-checklist)

## Package format

An `.arch` file is a standard ZIP archive with two required regular files and
an optional PNG icon at its root, with case-sensitive names:

```text
my-provider.arch
├── manifest.json
├── provider.js
└── icon.png        (optional)
```

The manifest and script must contain UTF-8 text, a UTF-8 byte-order mark is accepted. Use ZIP
Stored or Deflate compression. Password-protected archives, directory entries,
duplicate entries, extra files, nested paths, and alternate entry names are not
supported. The installer reads entries into bounded memory, it does not extract
paths to the filesystem. Bundle all JavaScript into `provider.js`. There is no
general asset loader, `require`, or relative script import API in v1. The optional
icon is handled by the installer and is not exposed as a script filesystem.

| Limit | Maximum |
| --- | --- |
| Downloaded/imported package | 4 MiB |
| Uncompressed `manifest.json` | 128 KiB |
| Uncompressed `provider.js` | 3 MiB |
| Installed normalized JavaScript, including manifest wrapper and embedded icon | 4 MiB |
| Optional uncompressed `icon.png` | 256 KiB, dimensions 1–1,024 pixels per axis |
| Manifest object/array nesting | 32 levels |

The `.arch` suffix is the distribution convention. The importer identifies ZIP
content from its bytes, so URL query strings, response MIME types, and filenames
do not determine the format. Existing supported text modules and catalogs retain
their content-based import paths.

## Manifest

Create this authoring manifest beside your real `provider.js`. The packager adds
the required `sha256` field to the copy inside the archive, it leaves your input
files unchanged. The identity and labels below illustrate the schema, not an
installed provider or a claim about a particular service.

```json
{
  "format": "archivetune.module",
  "formatVersion": 1,
  "apiVersion": 1,
  "entry": "provider.js",
  "module": {
    "id": "org.example.my-provider",
    "name": "My Provider",
    "displayName": "My Provider Audio",
    "version": "1.0.0",
    "author": "Your project name",
    "description": "Resolves audio from the service implemented by this provider.",
    "homepage": "https://example.org/my-provider",
    "repository": "https://github.com/example/my-provider",
    "license": "MIT",
    "type": ["audio_provider"],
    "requiredRuntimeFeatures": ["fetch@1", "settings@1", "streamResolution@1"],
    "labels": [],
    "capabilities": ["searchTracks", "getTrackStreamUrl"],
    "settings": {
      "apiToken": {
        "type": "password",
        "label": "API token",
        "description": "Your own account token for this provider.",
        "defaultValue": null,
        "required": true,
        "secret": true
      },
      "quality": {
        "type": "select",
        "label": "Audio quality",
        "description": "The quality requested from this provider.",
        "defaultValue": "lossless",
        "options": [
          {"label": "Lossless", "value": "lossless"},
          {"label": "Hi-Res lossless", "value": "hires"}
        ]
      }
    }
  }
}
```

Only declare qualities your implementation and service actually offer. Remove
the credential setting when your service does not need one.

| Field | Contract |
| --- | --- |
| `format` | Exactly `"archivetune.module"`. |
| `formatVersion` | Integer `1`. Other versions are rejected before JavaScript runs. |
| `apiVersion` | Integer `1`. Other versions are rejected before JavaScript runs. |
| `entry` | Exactly `"provider.js"`. |
| `sha256` | Required in the packaged manifest: 64 lowercase hexadecimal characters representing SHA-256 of the exact uncompressed script bytes, including any BOM or line endings. Generated by the packager. |
| `module.id` | Stable identifier matching `[a-z0-9][a-z0-9._-]{0,127}`. Keep it unchanged across updates. |
| `module.name` | Nonblank string, at most 256 characters. |
| `module.version` | Nonblank string, at most 128 characters. Semantic versioning is recommended, the updater compares source changes, not semantic version precedence. |
| `module.author` | Optional string. |
| `module.description` | Optional string, at most 8,192 characters. |
| `module.labels` | Optional array of strings. Informational provider declarations, not verification of an active stream. `tags` is also accepted and merged with labels. |
| `module.capabilities` | Required array of unique supported method names. Must include `searchTracks` and `getTrackStreamUrl` and exactly match the known methods actually exported by the script. |
| `module.settings` | Optional object mapping setting keys to field declarations, or an array of declarations with unique string `key` values. |
| `module.displayName` | Optional user-facing name, at most 256 characters. Used in installed rows, details, and installation review, the stable `id` is unchanged. Local name edits take precedence. |
| `module.homepage` / `module.repository` | Optional absolute HTTP(S) project/source URLs, at most 2,048 characters. Displayed as links in review/details. |
| `module.license` | Optional license identifier/text, at most 256 characters. Describes the provider, not the audio. |
| `module.type` | Optional category string or array of strings, informational. Does not enable new app destinations or host permissions. |
| `module.minAppVersion` | Optional minimum ArchiveTune version as `major.minor.patch`, with 1–6 digits per part. Installation and resolution reject newer requirements. The installed app's numeric release prefix is compared, build suffixes are ignored. |
| `module.requiredRuntimeFeatures` | Optional string or array of required feature IDs. Unknown IDs are rejected. |
| `module.icon` | Optional literal `"icon.png"`, referencing the packaged PNG. Omit the field and file for a module without an icon. |
| `iconSha256` | Required at package root when an icon is declared, SHA-256 of the exact PNG bytes. Added by the packager. |
| `module.qualityOptions` | Optional rich quality declarations, see below. Use these or an explicit `settings.quality` field, not both. |
| `module.defaultQuality` | Optional typed option ID, defaulting to the first declared quality. Must match an option. |
| `module.qualityLabel` | Optional label for the generated quality setting, defaults to the key `quality`. |

Supported required feature IDs are `fetch@1`, `settings@1`, `streamResolution@1`,
`packagedIcon@1`, and `qualityOptions@1`. These describe the host functionality
implemented by this build. publish the exact app build you verified, not just API v1.

### Optional icon

Set `module.icon` to `"icon.png"` and place that PNG beside the manifest and
script. The packager includes it and calculates `iconSha256`. The installer
checks its checksum, PNG header, declared dimensions, and size. Icons are
embedded in the normalized definition and survive restarts, updates, and portable
backups without a remote image request. A later package update controls its icon.
The image is shown beside installed author metadata, in installation review,
module details, and installed catalog entries. Missing icons reserve no space.
Decode failures use the application extension icon. Remote icon URLs, SVG,
arbitrary file paths, and additional assets are not supported.

### Relationship to other extension manifests

The default `.arch` runtime uses `module.exports` and the API documented below.
Packages can select the `extension` compatibility runtime, which uses
`registerExtension(...)` and a separate synchronous host API. Unmodified
extension packages and raw manifest URLs have their own import path, they do not
need conversion to `.arch`. See [Extension compatibility](#extension-compatibility)
for package, authentication, and transfer behavior.

Supported capability names are `searchTracks`, `getTrackStreamUrl`, `getAlbum`,
`getArtist`, and `getPlaylist`. Only the first two are called by automatic audio
resolution. Optional album/artist/playlist capabilities are informational in the
current application, declaring them does not create browsing UI.

Manifest identity, metadata, labels, and settings override corresponding script
properties. Do not duplicate them in `module.exports`. Capability declarations
are checked against callable exports during installation review. Installation
executes initialization to inspect the provider, it does not contact every
provider endpoint or prove that any song can be resolved.

The checksum detects script/manifest mismatches and damaged transfers. It is not
a publisher signature: someone who changes both files can recompute it. There
is no signing-key trust system in format version 1.

## Settings

Settings render in the installed module's configuration screen and require an
explicit Save. Use `defaultValue` for typed defaults. `null` means unconfigured,
a required null/blank value blocks playback until the user configures it.

| Type | Value | Additional fields |
| --- | --- | --- |
| `text` | JSON string | `label`, `description`, `required` |
| `password` | JSON string | Masked input, also treated as secret |
| `number` | Finite JSON number | Optional numeric `min` / `max` |
| `integer` | Finite whole JSON number | Optional numeric `min` / `max` |
| `boolean` | JSON boolean | Switch control |
| `select` | A declared option's JSON value | Nonempty `options` array, each option can be a value or an object with `label` and `value` |

There are at most 128 settings, 256 options per setting, 128 characters per key,
and 256 characters per field label. Setting keys must be nonblank. Unknown field
types are rejected for native packages. Lower bounds cannot exceed upper bounds.
Non-null defaults must satisfy their field's type, options, and bounds.

The existing text/string/url, switch/toggle/checkbox, and selector/dropdown aliases
are also accepted, but use the canonical types above for new packages. The
compatibility `value` property is accepted when `defaultValue` is absent.

Every declared setting arrives through `context.settings[key].value`, preserving
its JSON type. For example, `context.settings.apiToken.value` is the configured
string or null, it is not an object containing another nested settings schema.

Set `secret: true` for credentials. Password fields and keys resembling tokens,
passwords, secrets, credentials, API keys, cookies, or authorization are also
classified as sensitive. Configuration persistence is encrypted using the
application's Android Keystore-backed storage, sensitive values are omitted
from portable backups. Never place a real credential in a default, package,
catalog, diagnostic label, or published source.

### Independent audio quality

An alternative to declaring `settings.quality` is a `qualityOptions` array in
the module object. Each entry has a unique non-null scalar `id`, a nonblank
`label` (at most 256 characters), and optional `description` (2,048 characters)
and `kind` (64 characters). IDs retain their string, number, or boolean type.
For example, these module fields declare a provider-owned quality control:

```json
{
  "qualityLabel": "Audio quality",
  "defaultQuality": "LOSSLESS",
  "qualityOptions": [
    {
      "id": "LOSSLESS",
      "label": "Lossless",
      "description": "Request the lossless encoding offered by this source.",
      "kind": "lossless"
    },
    {
      "id": "HI_RES_LOSSLESS",
      "label": "Hi-Res lossless",
      "description": "Request the source's Hi-Res variant when available.",
      "kind": "lossless"
    }
  ]
}
```

Do not include this declaration alongside `settings.quality`, ambiguous duplicate
definitions are rejected. The installer creates the canonical select field from
the options, and uses `defaultQuality` or the first option's ID as its default.
Descriptions appear in the dropdown and below the selected control. `kind` is
descriptive metadata, it does not assert the codec of a resolved stream or grant
native spatial-audio, conversion, or download functionality.

For explicit select fields, options also accept `description` and `kind`. These
are retained across updates and backups. As an authoring convenience, `settings`
may instead be an array of objects with unique string `key` values, `default` is
accepted as a typed default after `defaultValue` and `value`. The installer
normalizes either authoring shape into the same keyed runtime settings object.

The module owns its quality vocabulary, values, default, and any fallback
policy. ArchiveTune does not translate those values through its YouTube quality
enum. If the exact key `quality` is declared, its configured value is also passed
as the second argument of `getTrackStreamUrl`. Null is passed as `undefined` in
that argument. Other quality-related fields remain available through context.

When there is no quality setting, the script uses its service's own default.
YouTube Wi-Fi/mobile quality preferences and low-data quality reductions do not
change module arguments or settings. General connectivity restrictions still
apply. Saved module configuration changes invalidate that module's resolutions,
editing its local display metadata does not change its playback revision.

## JavaScript entry point

Use CommonJS exports in the single entry file. Define your service integration's
`searchTracks` and `getTrackStreamUrl` functions and export them under these exact
names:

```javascript
module.exports = { searchTracks, getTrackStreamUrl },
```

This line is an export declaration, not a complete provider. Both functions must
be implemented against the real service before packaging. They may return
values directly or return Promises. Export an object synchronously, asynchronous
initialization belongs inside the methods. Keep top-level code limited to
definitions and lightweight setup because installation also executes it.

The entry file runs in QuickJS with the supplied host APIs. There is no Node.js,
DOM, Android object access, filesystem API, browser login session, application
account credential access, or package import system. Do not call the host's
internal names beginning with `__module` or depend on the installer's wrapper.
Global variables may survive calls while an engine is retained, but engines can
be closed after failure, configuration changes, eviction, or process shutdown.
They are not persistent storage.

## Track search

Signature: `searchTracks(query, limit, context)`.

- `query` is a title/primary-artist query created from the existing song.
- `limit` is currently `10`. Respect it, the host parses at most 100 results.
- `context.settings` contains typed values. Track context is not supplied to
  search in API v1.

Return an array of track objects, or an object containing `tracks: [...]`. An
empty array means the request succeeded and there were no results. Throw on
transport, authentication, response-parsing, or provider failures instead of
disguising them as an empty search.

| Track field | Meaning |
| --- | --- |
| `id` | Required nonblank provider track ID, returned to your stream resolver. |
| `title` | Accurate recording title. `name` is a compatibility alias. |
| `artists` | Prefer an array of artist-name strings. Artist objects with `name` and the singular `artist` form are also accepted. |
| `album` | Optional album title string or object with `title` / `name`. |
| `duration` | Optional positive running time in seconds. |
| `durationMs` | Optional running time in milliseconds, takes precedence over `duration`. |
| `explicit` | Optional JSON boolean, omit it when unknown. |
| `isrc` | Optional recording identifier. It is retained in track context, do not rely on it bypassing matching. |

Title and artist metadata are necessary for matching. ArchiveTune rejects weak
title/artist agreement, conflicting recording markers, known explicit-content
conflicts, and incompatible duration. Preserve live/remix/remaster/cover and
similar recording information. Do not relabel a different recording to force a
match. The highest-scoring acceptable candidate is resolved once per module,
returning many near-duplicates does not create additional stream attempts.

## Stream resolution and lossless audio

Signature: `getTrackStreamUrl(trackId, quality, context)`.

`trackId` is the selected provider ID. `context.track` contains the selected
provider candidate: `id`, `title`, `artist`, `artists`, and known album, duration,
explicit, and ISRC fields. `context.metadata` and `context.item` are aliases of
that same object. This is not a YouTube account or authentication context.

Return a JSON-serializable object describing an accessible audio resource:

| Field | Meaning |
| --- | --- |
| `url` | Required absolute HTTP(S) stream URL without embedded user information. `streamUrl` is also accepted. Return audio or a supported manifest, not a webpage or an asynchronous download job. |
| `headers` | Optional object of string request headers needed for playback/download requests. Credential-bearing values are provider data, do not publish them in catalogs. |
| `mimeType` | Supply explicitly, especially for extensionless/signed URLs. Examples: `audio/flac`, `audio/wav`, `audio/mp4`, `application/x-mpegURL`, `application/dash+xml`. |
| `codecs` | Optional actual codec identifier, such as `flac` or `alac`. `codec` is also accepted. |
| `bitrate` | Optional bitrate in bits per second, not kilobits. Use `0` when unknown. |
| `sampleRate` | Optional positive sample rate. Prefer integer hertz, such as `96000`. Values below 1000 are interpreted as kilohertz for compatibility. |
| `contentLength` | Optional resource length in bytes, or `0` when unknown. |
| `expiresAtMs` | Preferred absolute URL expiry in Unix milliseconds. Expired results are rejected. |
| `expiresInSeconds` | Relative lifetime if absolute expiry is absent, default 300 seconds, bounded to 1–86,400. |
| `variant` | Stable identity for the actual selected encoding/quality. Distinguish different source files and qualities. |
| `provider` | Optional upstream source name/identity when your module routes to multiple backends. |
| `track.sourceId` | Optional upstream audio ID if different from the searched ID. |

For an absolute expiry, `expiresAt` is a compatibility alias, values below
10,000,000,000 are interpreted as Unix seconds. `expiresIn` is an alias of
`expiresInSeconds`. Header aliases `streamHeaders` and `requestHeaders` are also
accepted. When using both aliases and `headers`, `headers` takes precedence.
An optional nested `track` object can supply technical metadata, codec, MIME,
and upstream identity, top-level fields are preferred for new providers.

The response may additionally use `streamType: "hls"` or `"dash"` when MIME is
absent. Otherwise the runtime can infer common formats from the URL's path
extension. Explicit MIME avoids ambiguity. Do not assume an `.m4a` URL is ALAC,
it can contain lossy audio.

FLAC needs a real FLAC resource from your service. A quality label or filename
does not convert audio or prove its provenance. Return whichever encoding the
server actually selected. Bit-depth and channel-count fields are not part of
the current stream model and are not persisted merely by adding JSON properties.
Decoder/device support still governs whether a particular encoding can play.

The application handles streaming, eligible progressive/HLS/DASH downloads,
headers, and cache separation. Do not download full audio through JavaScript
`fetch`: its bounded response API is intended for resolver requests. Return the
playable URL so the media/download pipeline can consume it.

Throw when a track has no usable stream. Your module may implement a quality
fallback only according to its own declared settings. The application continues
through the committed enabled-provider order and then YouTube when resolution
fails, a module cannot force the application's other providers to select a
particular quality.

## Host APIs and limits

| API | Available behavior |
| --- | --- |
| `fetch(url, options)` | HTTP(S), relative to the final module URL when applicable, method, string headers, body, and AbortController signal. Use absolute service URLs for portability to file imports. |
| Response | `ok`, `status`, final `url`, `headers`, asynchronous `text()`, `json()`, `arrayBuffer()`, `blob()`. Bodies are buffered. `clone()` returns the buffered response, it is not a streaming tee. |
| `Headers` | Construct, get/set/append/delete, has, entries/keys/forEach/iteration. Values are strings. |
| `URL`, `URLSearchParams` | URL resolution and query manipulation. These are limited host polyfills, not a complete browser URL/Request environment. |
| `TextEncoder`, `TextDecoder` | UTF-8 conversion. Arbitrary encoding labels/options are not implemented. |
| `atob`, `btoa` | Base64 / binary-string conversion. Encode Unicode to UTF-8 bytes first. |
| `Blob`, `FormData` | Buffered binary/text parts and multipart request bodies. |
| `setTimeout`, `clearTimeout` | One-shot timers, at most 128 active, delay bounded to 15 seconds. Timers are cleared after a method finishes. No interval API. |
| `AbortController` | Request cancellation through the fetch `signal` option. No automatic context signal is supplied. |
| `console` | log/info/warn/error/debug exist but do not publish logs. Use app diagnostics to investigate host failures. |

Fetch does not throw solely because a server returns HTTP 4xx/5xx, check
`response.ok` before parsing. Redirects are followed by the HTTP client, which
strips authorization/cookie/proxy-authorization headers when the origin changes.
There is no browser cookie jar or interactive OAuth UI supplied to modules.

| Resource | Current limit |
| --- | --- |
| Initialization inspection | 5 seconds |
| Search, matching, and stream resolution for one module | 15 seconds combined |
| Entire ordered module resolution sequence | 30 seconds |
| QuickJS memory / stack | 32 MiB / 1 MiB per engine |
| Fetch request body / response body | 8 MiB each |
| Concurrent HTTP requests | 8, up to 64 admitted requests total |
| HTTP connect / read / whole-call timeout | 10 / 15 / 20 seconds, also constrained by the enclosing operation |

Cancellation propagates from the owning app operation to host requests and
execution. Do not busy-loop, poll a download task, swallow abort errors, or keep
background work after returning. Do not depend on the exact number of cached
engines, it is an application implementation detail.

## Package and install

Python 3.9 or newer with its standard library is sufficient for the supplied
packager. Cloning ArchiveTune is optional: download the standalone script with
`curl` and run it against your own provider directory.

### Without cloning ArchiveTune

On Linux, macOS, or another POSIX shell:

```sh
curl --fail --silent --show-error --location --output package_module.py \
  https://raw.githubusercontent.com/rukamori/ArchiveTune/dev/scripts/package_module.py &&
python3 package_module.py "./my-provider" "./my-provider.arch"
```

On Windows PowerShell, use `curl.exe` to select the curl executable rather than
the Windows PowerShell alias:

```powershell
curl.exe --fail --silent --show-error --location --output package_module.py "https://raw.githubusercontent.com/rukamori/ArchiveTune/dev/scripts/package_module.py"
if ($LASTEXITCODE -eq 0) {
    py -3 .\package_module.py ".\my-provider" ".\my-provider.arch"
}
```

Replace the two provider paths with your input directory and desired output
file. The commands save `package_module.py` in the current directory and run it
only after curl succeeds. They download the packager, not a provider or the full
ArchiveTune repository. On Windows, `python` can replace `py -3` when that is how
Python 3.9 or newer is installed.

The URL above follows the `dev` branch, where the packager is currently
published. To use a fixed revision, replace `dev` in the URL with a commit SHA
or tag that contains `scripts/package_module.py`.

### From an existing checkout

From the ArchiveTune repository root:

```text
python scripts/package_module.py path/to/my-provider path/to/my-provider.arch
```

### Interactive terminal studio

Run the downloaded script without arguments to open the terminal interface:

```text
python package_module.py
```

On Windows, `py -3 package_module.py` also works. To start at a particular
workspace, use `python package_module.py ./my-provider --interactive`. This
mode requires an interactive terminal, so download the script before launching
it instead of piping its contents into Python's standard input.

The studio provides these actions:

| Action | Behavior |
| --- | --- |
| Review and validate | Displays provider identity, compatibility requirements, input files, hashes, typed settings, option descriptions, excluded files, and field-specific findings. |
| Edit metadata | Edits runtime selection, identity, display name, version, author, description, project links, license, categories, labels, capabilities, runtime requirements, and optional icon declaration. |
| Edit settings / quality | Replaces a declaration using a JSON value or `@path/to/value.json`. The extension profile also exposes permissions and signed-session configuration. Settings previews mask sensitive defaults. Use `-` to remove a declaration. |
| Save manifest | Validates manifest declarations and explicitly saves the draft atomically. This action does not require the provider script or icon to exist yet, packaging checks those files separately. A file changed by another editor requires confirmation before replacement. |
| Package .arch | Validates the current draft and actual files, asks for an output path, confirms writes/replacements, and produces a package report. |
| Inspect an .arch file | Checks archive entries, size limits, manifest declarations, script encoding, icon headers/dimensions, and stored checksums without extracting files or executing JavaScript. |
| Export last report | Writes the last validation, package, or inspection report to a separate JSON file. Exporting does not revalidate subsequent draft changes. |
| Change workspace / Exit | Confirms before discarding unsaved manifest changes. |

If an existing workspace has no manifest, the studio can create an authoring
draft with a stable ID, name, initial version, and the two mandatory capability
declarations. It does not generate `provider.js`, invent an endpoint, or supply
an audio provider. Implement the methods yourself before packaging.

Editing keeps a draft in memory. Saving the manifest and packaging it are
separate actions: packaging uses the current draft but does not silently save
those edits to the source manifest. Script and icon files are never edited by
the studio. They are read again when the user requests validation or packaging.

### Scripted validation, inspection, and reports

The original two-path packaging command remains supported. In scripted mode,
an existing destination package is replaced atomically without prompting.

```text
python package_module.py ./my-provider --check
python package_module.py --inspect ./my-provider.arch
python package_module.py ./my-provider --check --app-version 15.1.0
python package_module.py ./my-provider ./my-provider.arch --report ./package-report.json
python package_module.py --inspect ./my-provider.arch --json
```

Use your target ArchiveTune version with `--app-version`. Without it, the tool
validates the minimum-version declaration but does not claim that a particular
app build satisfies it. Unknown required runtime features are rejected using
this script's supported-feature list.

`--check` writes no package. `--inspect` reads an existing package without
changing it. `--json` sends a machine-readable report to standard output,
`--report FILE.json` also saves the report to a separate file. Required input
files and the package cannot be used as report destinations. Report files are
replaced atomically. Argument errors go to standard error and are not JSON.

Reports include entry byte counts and SHA-256 values, package size and SHA-256
when applicable, compression savings for created packages, typed setting
summaries and choices, findings with paths, and installation steps. Sensitive
defaults are masked using declared secrecy/password types and credential-like
keys, nested credential-like default keys are also redacted. Redaction is not
a substitute for excluding personal credentials from a distributed manifest.

Each report explicitly records runtime verification as not performed. These
checks do not execute JavaScript, verify callable exports, contact the provider,
decode audio, or prove that playback/downloads work. A script can pass local
validation and still fail installation or resolution in the app.

Exit codes are `0` for a successful operation, `1` for validation or I/O errors,
`2` for invalid command arguments, and `130` for interactive cancellation or
interruption. If a package was written successfully but saving its report fails,
the package remains on disk and the command exits with an error. Cancellation
preserves completed writes and discards uncommitted in-memory edits.

The input directory must contain your manifest and implemented entry script. The
destination directory must already exist. The command writes the required files
and declared optional icon into the archive, calculates their checksums, checks size limits, and
atomically replaces the destination file if it exists. It validates declarations
and package inputs but does not execute your script or validate the service
integration. The application remains the
authority for manifest, settings, and exported-capability validation.

If using another ZIP tool, calculate SHA-256 over the exact `provider.js` bytes,
write it into `manifest.json` as lowercase `sha256`, and ZIP the files themselves,
not their enclosing directory. Changing script line endings after hashing
invalidates the checksum. Extra editor files or directory records are rejected.

In a compatible app build:

1. Open **Settings → Modules → Add → Import file** and select the `.arch` file.
2. Review identity, version, capabilities, and required configuration.
3. Confirm installation, open the installed module, enter required values, and
   Save. Enable the module and **Use modules** when ready.
4. Use **Check module** to inspect initialization/configuration, then play an
   eligible song to exercise actual search, matching, and stream resolution.

Installation is staged: invalid packages or failed replacements leave the
installed provider intact. New installations append to playback priority,
updates preserve their installation ID, order, enabled state, compatible
configuration, and local display metadata. Removing a provider removes its
configuration, completed downloads remain usable.

## URLs, catalogs, and updates

Publish the `.arch` file at a direct download URL, such as a release asset on
your own repository. The URL must return the package bytes, not a repository
HTML page or login form. HTTPS is recommended. **Add module URL** installs it
through the same validation/review path as a file.

A descriptor is a JSON object with `download` (or `url`) pointing to a package or
another descriptor. Relative references resolve against the document's final
URL after redirects. A descriptor URL can stay stable while referring to a
new versioned release asset. Traversal is limited to four references, cycles
are rejected. A locally imported descriptor needs an absolute download URL.

For a catalog, publish a UTF-8 JSON document with this structure. The example
uses relative URLs resolved against your catalog's location:

```json
{
  "name": "My module catalog",
  "modules": [
    {
      "id": "org.example.my-provider",
      "name": "My Provider",
      "version": "1.0.0",
      "author": "Your project name",
      "description": "Provider for the audio service implemented by this project.",
      "download": "./releases/my-provider-1.0.0.arch",
      "type": "MODULE",
      "capabilities": ["searchTracks", "getTrackStreamUrl"],
      "labels": []
    }
  ]
}
```

Users add the catalog's URL through **Add catalog URL**. Refreshing a catalog
only refreshes entries, users explicitly install or update executable packages.
Catalog metadata does not replace package validation. Installed catalog entries
are shown first without changing playback order. Do not place credentials in
catalog URLs or descriptions.

Catalogs can reference other catalogs using `external_sources`, an array of
objects with a `url`, an optional `baseUrl` changes their module download base.
The reader limits catalog depth to four, documents to 32, and entries to 1,000,
it deduplicates repeated URLs to stop cycles. A direct JSON catalog URL is the
most predictable publishing target.

For URL installations, Check for update revisits the original import URL. If
the original URL is a descriptor, update that descriptor to point to the new
package. Direct versioned package URLs keep pointing to that version unless
their contents change. A file-only installation has no update URL, re-import
the updated package to replace the same provider ID. Changing a provider ID
creates a different installation or rejects an explicitly targeted replacement.
Do not change the identity just to bypass an installation/update failure.

Portable backups include normalized executable definitions, subscriptions,
priority, local metadata, and non-sensitive configuration. Restore requires
re-enabling executable modules and re-entering excluded credentials. A backup
is not a new `.arch` release and should not be distributed as a provider package.

## Failures and diagnostics

Package validation runs before the review is committed. Diagnostics use phases
and redacted codes, arbitrary provider exception messages are not displayed.

| Code / outcome | What to inspect |
| --- | --- |
| `ARCH_ZIP`, `ARCH_ENTRY`, missing-file errors | ZIP format, compression, exact root filenames, duplicates, extra files, encryption, or damaged entries. |
| `ARCH_SIZE` or invalid-document size errors | Compressed package, uncompressed entries, and normalized source limits. |
| `ARCH_UTF8` | Encode both files as UTF-8. |
| `ARCH_VERSION` | Format string and integer format/API versions. |
| `ARCH_CHECKSUM` | Pack again after script edits, do not hash a different file or text-normalized copy. |
| `ARCH_MANIFEST`, `ARCH_IDENTITY`, `ARCH_LABELS`, `ARCH_SETTINGS`, `ARCH_CAPABILITIES` | Manifest types, required identity, typed defaults, numeric bounds, known capability names. |
| `ARCH_SETTING_TYPE` | Replace an unknown setting type with a supported declaration. |
| `ARCH_ICON`, `ARCH_ICON_CHECKSUM` | Declare `icon.png` explicitly, include only that image, and repackage after editing it. Invalid PNG size, dimensions, or encoding are also rejected. |
| `ARCH_APP_VERSION`, `ARCH_RUNTIME_FEATURE` | The installed ArchiveTune build must satisfy the declared minimum version and every required host feature. |
| `ARCH_QUALITY_OPTIONS` | Remove duplicate quality declarations, check unique typed IDs and that the default matches an option. |
| `ARCH_UNSUPPORTED_…` | Remove declarations for host behavior this runtime does not implement, descriptive metadata does not activate those APIs. |
| `EXT_RUNTIME_FEATURE_…`, `EXT_PLAYBACK_PROVIDER` | The extension needs an unavailable host feature or lacks the playback-provider contract. Metadata-only extensions cannot supply automatic audio. |
| `EXT_NETWORK_PERMISSION` | Include the service, verification, and media domains in the extension's network declaration. Redirect destinations also need permission. |
| `EXT_INITIALIZATION_IO` | Move network requests out of top-level registration and into initialization or a provider method. Import inspection performs no network I/O. |
| `SESSION_VERIFICATION_REQUIRED` | Open installed module details and choose **Verify connection**. |
| `EXT_SESSION_SCHEME` | Declare the service's signing-protocol identifier in `signedSession.schemeLabel`. |
| `SESSION_CALLBACK_STATE`, `SESSION_CALLBACK_EXPIRED` | Start verification again from that module's settings. A callback must match a pending, unexpired state. |
| Provider TypeError during installation | Export an object and implement exactly the declared known capabilities. |
| Ready | Initialization/configuration succeeded, this does not prove search or audio access. |
| No acceptable match | Inspect recording title, artists, duration, explicit metadata, and whether results are actually empty. |
| Provider failed / HTTP status code | Service response, expired account credentials, non-JSON bodies, or script error. Do not return empty results to mask these failures. |
| Timeout | Keep the complete search-plus-resolution operation within its deadline. |

For actual playback, initialization and playback outcomes are recorded
separately. A successful health check does not erase the last failed playback
outcome. Use the module's **Copy diagnostics** action when reporting an issue,
do not attach account tokens or signed stream URLs publicly.

## Extension compatibility

This runtime loads single-file JavaScript extensions that use
`registerExtension(...)` and a separate `manifest.json`.
It adapts playback providers to ArchiveTune's existing automatic audio
resolution. It does not add music search, home feeds, URL handlers, or new
browsing destinations to the application.

### Installation formats

- Import a compatible ZIP-based extension package through **Settings → Modules →
  Add → Import file**, or supply its direct download URL. The extension needs
  root `manifest.json` and `index.js`. Optional root PNG/JPEG icons and the
  `LICENSE`, `COPYING`, and `NOTICE` files are accepted. Archives are read in
  memory without extracting paths into the application's filesystem.
- A raw manifest URL can load its adjacent `index.js` and declared icon. A
  standalone JavaScript file cannot supply the separate extension manifest.
- Add a raw `registry.json` URL as a catalog. The `extensions`, `download_url`,
  `display_name`, and optional `sha256` fields are recognized. Catalog refresh
  changes metadata only. A declared package digest must match before staging
  installation.
- Playback requires registered `searchTracks` and `download` functions.
  Metadata-only, lyrics-only, post-processing-only, and V3 account-pool
  extensions are not playback providers for this runtime.

The archive and input bounds remain 4 MiB total, 128 KiB for the manifest,
3 MiB for JavaScript, and 256 KiB for each icon. Icon dimensions must be within
1–1024 pixels. JPEG display icons are converted to PNG internally and must
also fit the PNG size bound. Unsafe paths, duplicate entries, invalid metadata,
and unknown required runtime features reject the staged import.

An imported extension's `minAppVersion` refers to its originating host. It is retained as
extension data, not compared numerically with ArchiveTune's version. Required
runtime features determine host compatibility. This implementation recognizes
`signedSession@3`, `sessionGrant@1`, `downloadSegments@1`, and
`preparedContext@1`, `apiV3@1` is not implemented.

### Authoring a compatible `.arch`

Use the existing `.arch` envelope, checksums, `provider.js`, stable module ID,
and capability-name array. Add the top-level field `"runtime": "extension"` and
write `registerExtension(...)` in the entry script. The declared capabilities
describe the resulting ArchiveTune provider: `searchTracks`,
`getTrackStreamUrl`, and any registered optional album/artist/playlist methods.
The adapter supplies `getTrackStreamUrl` from the extension's `download` method.

Select the runtime in the packager's **Edit metadata** action. **Edit settings /
quality** then exposes `permissions` and `signedSession` declarations as well.
The default `archivetune` runtime retains the `module.exports` contract and
does not acquire these APIs. `.arch` minimum-version checks still refer to
ArchiveTune even when the extension runtime is selected.

### Execution and host bindings

| API or contract | ArchiveTune behavior |
| --- | --- |
| `registerExtension(object)` | Exactly one registration with callable track-search and download methods is required. |
| `initialize(settings)` | Receives flat, typed setting values. Initialization runs when that engine's configuration changes, a returned `false` fails the operation. |
| `searchTracks(query, limit)` | Results are mapped from extension fields such as `name`, `artists`, `album_name`, and `duration_ms` before the existing recording matcher runs. |
| `download(id, quality, outputPath, onProgress, options)` | Runs in transfer-resolution mode. The host captures the first file-transfer request and stops the extension's download pipeline there. No successful file download or local output path is fabricated. |
| `http.get/head/delete(url, headers)` | Synchronous, cancellable HTTP with status, body, final URL, headers, and permission checks. Nested `headers`/`params` options are accepted for GET helpers. |
| `http.post/put/patch(url, body, headers)` | String bodies are preserved, object bodies are JSON encoded. Content type defaults to JSON when a body is present. |
| `http.request(url, options)` | Generic synchronous request with `method`, `headers`, and `body`. |
| `fetch(url, options)` | Synchronous response contract with `text()`, `json()`, `arrayBuffer()`, status, and headers. Awaiting these returned values also works. The default `.arch` runtime keeps its asynchronous fetch API. |
| `http.clearCookies()` | Clears that installation's cookie jar. Cookies are isolated from application and YouTube accounts. |
| `storage.get/set/remove/clear/keys` | Per-installation typed values, limited to 1 MiB and encrypted with an Android Keystore key. Requires `permissions.storage`. Import inspection uses disposable memory storage and permits no network requests. |
| `utils` | SHA-256/SHA-1/MD5 digests, UTF-8 Base64, JSON helpers, UUIDs, app version/user agent, bounded sleep, remaining resolution time, and cancellation state. `randomUserAgent()` uses the application's user agent. |
| `matching.compareStrings/compareDuration` | Normalized token similarity and duration proximity helpers. ArchiveTune's independent recording matcher remains the acceptance boundary. |
| `session` | Host-owned signed requests, authentication status, and session clearing. Browser grants are completed through the application's callback, not by handing a session secret to JavaScript. |

The existing URL, headers, encoding, typed-array, timer, and cancellation
bindings remain available. Synchronous requests execute away from the main
thread. Cancellation closes active requests and timers, the existing 15-second
provider and 30-second module-sequence playback budgets still apply.

`permissions.network` accepts exact domains and `*.domain` subdomains. It is
enforced on initial requests, redirects, manifests, and media requests. A
wildcard does not include the bare domain. Signed requests do not follow
redirects. Settings, HTTP bodies, cookies, and credentials are not shared with
YouTube. Optional settings buttons and discovery/URL declarations are retained
as provider data without adding application actions or destinations.

### Tidal verification and media transfer

For a signed-session provider, open its installed module details and choose
**Verify connection**. The host requests bootstrap state using the declared
namespace, service base URL, app-version scope, platform, and endpoint paths.
Signed-session manifests must declare `signedSession.schemeLabel` using the
protocol identifier supplied by their service. The host uses that value exactly,
it does not substitute the application name or supply a default scheme label.
If verification is required, it opens the service's HTTPS challenge with an
ArchiveTune callback. The return must contain exactly one matching unexpired
state and a grant or code. The host exchanges the grant and stores the session
secret encrypted. Requests use the declared HMAC-SHA256 scheme, rolling time
window, nonce, timestamp, and body digest. Expiring sessions use the declared
refresh endpoint. A gateway-invalidated session requires verification again.

Pending callbacks expire after three minutes. Repeated or unrelated callbacks
cannot complete a cleared session. Callback grants, signed-session records,
cookies, and extension storage are excluded from portable and Android backups.
Removing the module removes its runtime storage. A health check reports missing
verification as configuration-required instead of reporting the provider ready.

`file.download` hands its direct URL and request headers to the existing
playback/download system. `file.downloadSegments` uses the original DASH or HLS
manifest observed during resolution, including manifests decoded from an API's
Base64 `manifest` field. Segment URLs without a corresponding original manifest
are rejected, timing information is not guessed from a list of URLs. Requires
`permissions.file`. Headers and extension cookies remain available to native
manifest, segment, and key requests.

Tidal's standalone-file export can convert FLAC carried in MP4 into a `.flac`
file. ArchiveTune's playback and offline downloads use Media3's container
extraction and cache instead: the compressed audio stays in its original
container, with no conversion of the audio samples. The adapter stops before
file tagging and standalone-file conversion. It does not expose arbitrary
filesystem access, FFmpeg commands, external `gobackend` account APIs, OAuth
account-management APIs, or post-processing hooks. Such requirements are not
general-purpose host capabilities here.

The module's declared quality options and defaults own quality selection.
YouTube audio quality, Wi-Fi/mobile preferences, and low-data quality changes
are never passed to extension initialization or download resolution. A
successful import or verification does not prove a particular service response,
recording match, device codec, or actual lossless encoding, check those in the
target app/device before publishing compatibility claims.

## Release checklist

- Keep provider ID stable and increment your own version for releases.
- Verify exported capabilities and settings against this guide and the app.
- Package after the final script edit so the checksum matches.
- Confirm that both file import and your published direct URL reach installation
  review in the app build you target.
- Exercise configured and missing-credential cases, genuine empty searches,
  mismatched recordings, service failures, cancellation, and expiring URLs.
- Verify the actual returned encoding at every declared quality, a tag alone is
  not a quality check.
- Check that changing YouTube quality does not change your module's request.
- Verify playback and applicable downloads against your real service.
- Publish source, service requirements, supported qualities, version history,
  and the ArchiveTune build/API versions you actually checked. Do not claim
  compatibility based only on successful packaging or initialization.

The implementation references are [package validation](modules/src/main/kotlin/moe/rukamori/archivetune/modules/ModulePackageReader.kt),
[runtime and result normalization](modules/src/main/kotlin/moe/rukamori/archivetune/modules/ModuleRuntime.kt),
[host bindings](modules/src/main/assets/module-host.js), and
[model/setting contracts](modules/src/main/kotlin/moe/rukamori/archivetune/modules/ModuleModels.kt).

# Pull Request

## Summary

<!-- Describe the change in 2-5 concise sentences. Include the user-facing behavior, architectural change, or maintenance outcome. -->

## Linked Work

<!-- Link related issues, discussions, releases, or design notes. Use "Closes #123" only when the PR fully resolves the issue. -->

- Closes:
- Related:

## Change Type

<!-- Check every category that applies. -->

- [ ] Feature
- [ ] Bug fix
- [ ] UI / UX
- [ ] Performance / memory
- [ ] Playback / Media3
- [ ] Lyrics / provider integration
- [ ] Search / YouTube / network data
- [ ] Local library / Room database
- [ ] Widgets / notification / shortcuts
- [ ] Settings / preferences / DataStore
- [ ] Discord / Last.fm / ListenBrainz / external integration
- [ ] Localization / strings / fastlane metadata
- [ ] Build / Gradle / CI / release packaging
- [ ] Dependency update
- [ ] Documentation only

## Affected Surfaces

<!-- Be explicit so reviewers can focus on the correct runtime paths. -->

- [ ] `:app`
- [ ] `:core`
- [ ] `:lyrics`
- [ ] `:lastfm`
- [ ] `:canvas`
- [ ] `:shazamkit`
- [ ] `:spotifycore`
- [ ] Other:

## Screenshots / Recordings

<!-- Required for UI, widget, notification, player, lyrics, settings, and visual changes. Include before/after when possible. -->

| Before | After |
| --- | --- |
|  |  |

## Behavior Notes

<!-- Describe the exact behavior reviewers should verify. Include edge cases, fallback behavior, and migration behavior. -->

## Architecture Checklist

- [ ] The change preserves UDF flow: UI -> ViewModel -> UseCase/domain -> Repository/data.
- [ ] Screen state is represented structurally with `Loading`, `Success`, `Empty`, and `Error` where this PR introduces or changes screen state.
- [ ] Composables receive immutable, UI-specific domain models instead of raw Room, network, or service entities.
- [ ] Business work is not triggered directly from composition.
- [ ] Exceptions are surfaced through explicit state or result types instead of being swallowed.
- [ ] No `runBlocking` is introduced in app execution paths.

## Compose / Material Checklist

- [ ] UI state is hoisted out of composable layout code.
- [ ] Reactive state is collected with `collectAsStateWithLifecycle()`.
- [ ] New UI models are annotated with `@Immutable` or `@Stable`.
- [ ] Lazy layouts use stable `key` values and explicit `contentType`.
- [ ] Non-primitive constants, structural lambdas, and allocation-heavy objects in hot paths are remembered.
- [ ] Rapidly changing inputs such as scroll, gesture, animation, or playback progress use `derivedStateOf` where appropriate.
- [ ] UI strings come from `stringResource()` and duplicated visible strings on the same screen are avoided.
- [ ] Interactive elements keep a minimum 48dp touch target.
- [ ] Material 3 / Material 3 Expressive tokens are used for color, typography, shape, and motion instead of hardcoded UI values.
- [ ] Edge-to-edge layouts handle and consume `WindowInsets` correctly when this PR changes screen layout.

## Concurrency / Performance Checklist

- [ ] Business coroutines are scoped to `viewModelScope` or an existing lifecycle-owned application/service scope.
- [ ] Disk, database, network, parsing, and heavy mapping work is dispatched to `Dispatchers.IO` or `Dispatchers.Default`.
- [ ] Cancellation is handled explicitly where long-running work, playback, downloads, sync, lyrics fetching, or recognition is involved.
- [ ] The change avoids blocking the main thread.
- [ ] The change avoids unnecessary allocations in recomposition loops, playback loops, polling loops, and provider parsing paths.
- [ ] Large lists, images, lyrics payloads, and network responses are bounded, streamed, cached, paged, or mapped off the main thread as appropriate.

## Data / Persistence Checklist

- [ ] Room entity, DAO, or database changes include a schema update under `app/schemas`.
- [ ] Database migrations preserve existing user data and fail clearly when migration is impossible.
- [ ] DataStore or preference-key changes are backward compatible.
- [ ] Network DTOs remain isolated from UI models.
- [ ] Provider responses handle missing, malformed, regional, or rate-limited data without crashing the app.

## Playback / Integration Checklist

- [ ] Media3 playback, queue, cache, and service behavior remains stable across foreground, background, notification, and process recreation paths.
- [ ] Audio focus, notification controls, widgets, shortcuts, and media session actions are considered when playback behavior changes.
- [ ] External integrations handle absent credentials, revoked auth, network failure, and API changes.
- [ ] Native, AAR, or ABI-sensitive changes account for mobile/tv and `universal`, `arm64`, `armeabi`, `x86`, and `x86_64` variants.

## Localization / Assets Checklist

- [ ] User-facing strings are added to the base resources and translated resources are updated or intentionally left for translation follow-up.
- [ ] Unused string resources, drawables, and metadata are removed when replaced.
- [ ] Image assets have explicit display dimensions and are decoded at the displayed size.
- [ ] Fastlane metadata, screenshots, icons, or release text are updated when user-facing store behavior changes.

## Privacy / Security Checklist

- [ ] No secrets, keys, tokens, keystores, signing files, private certificates, or local machine paths are committed.
- [ ] Logs do not expose access tokens, cookies, auth headers, user identifiers, listening history, or local file paths.
- [ ] New network calls are justified by the feature and use existing client, proxy, timeout, and error-handling patterns.
- [ ] User data remains local unless the PR explicitly documents the integration and consent path.

## Verification

<!-- Do not leave this blank. If a check is not applicable, state why. -->

- [ ] Android Studio sync:
- [ ] Manual device/emulator verification:
- [ ] UI screenshot/recording attached:
- [ ] Accessibility or touch-target review:
- [ ] Regression areas checked:
- [ ] CI expectation:

## Reviewer Focus

<!-- Call out the exact files, architecture decisions, performance-sensitive paths, or risky behavior that need close review. -->

## Release Notes

<!-- Write one concise user-facing sentence, or "None" for internal-only changes. -->


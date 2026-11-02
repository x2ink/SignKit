# Contributing

Enable the project hooks after cloning:

```sh
./scripts/install-git-hooks.sh
```

The installer sets `core.hooksPath` in this repository's local Git configuration. It does not change global Git settings.

## Commit messages

`git commit` opens a numbered menu. Choose a number or type a name, then write the description in your usual editor. `git commit -m "add export"` also prompts for a type and adds the selected prefix.

Already formatted messages do not prompt:

```sh
git commit -m "feat: add bitmap export"
git commit -m "fix(pen): prevent oversized stroke endings"
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.

The format is `type(optional-scope): description`; `!` may precede the colon for a breaking change. Empty descriptions and unknown types are rejected. Standard merge and generated revert subjects are accepted. In non-interactive environments, supply a correctly formatted message explicitly.

## Validation

```sh
./gradlew :signkit:testDebugUnitTest :app:testDebugUnitTest
./gradlew :signkit:lintDebug :app:lintDebug
```

Connected-device tests:

```sh
./gradlew :signkit:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

# SignKit

A Kotlin Android signature pad featuring smooth Bézier strokes, velocity-based pen dynamics, optional pressure input, adjustable stroke width, stroke-by-stroke undo, bitmap export, and writing lifecycle callbacks.

## Features

- Fixed-width, velocity, pressure, and combined pen modes.
- Configurable stroke width and velocity response, with controlled stroke endings.
- Stroke-by-stroke undo, clear, and undo availability checks.
- Bitmap export with transparent or custom backgrounds.
- Writing start, progress, and end callbacks.
- Raw pressure, input tool, and device information.
- Demo app with pen controls and bitmap preview.

Requires Android 11 (API 30) or later. Pressure effects depend on the input values reported by the device.

## Usage

Include the `signkit` module in your Android project:

```kotlin
implementation(project(":signkit"))
```

Add the view to your layout:

```xml
<ink.x2.signkit.SignPadView
    android:id="@+id/signPad"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

Configure the pen and writing callbacks:

```kotlin
binding.signPad.apply {
    penMode = SignPadView.PenMode.VELOCITY
    strokeWidth = 12f * resources.displayMetrics.density
    setOnWriteListener(object : SignPadView.OnWriteListener {
        override fun onWriteStart(view: SignPadView) {}
        override fun onWriting(view: SignPadView) {}
        override fun onWriteEnd(view: SignPadView) {}
    })
}

binding.signPad.undo()
binding.signPad.clear()
val bitmap = binding.signPad.exportBitmap()
```

Pen changes apply to new strokes. `strokeWidth` uses pixels and acts as the maximum width in dynamic modes. Bitmap export requires a laid-out view.

## Development

After cloning, enable the repository's commit hooks:

```sh
./scripts/install-git-hooks.sh
```

Run `git commit` to choose a commit type interactively. A message already written as `feat: ...` or `fix(pen): ...` can be supplied with `-m`. Both paths are validated before committing.

```sh
./gradlew :app:assembleDebug :signkit:testDebugUnitTest
./gradlew :signkit:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

See [architecture](docs/ARCHITECTURE.md) and [contributing](docs/CONTRIBUTING.md).

## Releases

Download SDK binaries and the demo APK from [GitHub Releases](https://github.com/x2ink/SignKit/releases). Version tags trigger the build and publish workflow; see [releasing](docs/RELEASING.md).

## Acknowledgments

Parts of the dynamic stroke implementation are adapted from [android-signaturepad](https://github.com/gcacace/android-signaturepad), licensed under Apache-2.0. Attribution and the upstream license are included in the library's `META-INF` resources.

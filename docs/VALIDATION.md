# 0.2.0 local preflight

Validated locally with JDK21, Android SDK37.0, Gradle9.6.1 and the repository's pinned dependencies:

- `:app:compileDebugKotlin`: passed.
- `:app:testDebugUnitTest`: **13 tests passed** (3 codec +10 transfer-protocol tests),0 failures/errors.
- `:app:lintDebug`: passed,0 errors; lint warnings remain (dependency recommendations, framework EXIF recommendation, style and application-context retention checks).
- `python roundtrip_test.py`: both fixtures pixel-identical after rebuilding. Captured120,956→115,216bytes; demo106,056→106,056bytes.
- The upload asset's SHA256 equals the original captured binary: `29d80e83e92c048f3f526edd9535bc477961bef6355c97a99610c1afcec05a5a`.

Protocol tests exercise split/coalesced/noisy framing, bounded buffers, exact byte transfer/final commands, same-offset packet-size fallback, default20-byte packets, duplicate-block progress, early/invalid completion, excessive retries, ambiguous-write abort, missing-block timeouts, optional BA reply and cancellation.

This is **local validation**, not a GitHub Actions result for0.2.0. Android GATT callbacks, UI/permission behavior on a physical phone, and watch installation have not been hardware-tested. Successful build/tests do not establish watch compatibility.

The2GiB build environment needed one worker, in-process Kotlin compilation and a reduced heap. Example local override (not required on the larger CI runner):

```sh
./gradlew --no-daemon --no-parallel --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx768m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:lintDebug
```

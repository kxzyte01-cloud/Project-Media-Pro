# kotlinx.serialization ships its own consumer ProGuard/R8 rules inside the library artifact
# (as documented at https://github.com/Kotlin/kotlinx.serialization#android).
# CI runs `assembleRelease` with `isMinifyEnabled = true` to verify R8 shrinking and serialization rules.

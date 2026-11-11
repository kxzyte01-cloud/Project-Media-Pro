# AI Governance Rules for Project Media Pro

1. **GitHub `main` is the Single Truth:** No code zips passed between AIs. All changes go through PRs and CI checks.
2. **Zero Internet Permission in V1:** The app requests no internet permission to ensure complete user privacy.
3. **Atomic File Persistence:** All project saves must use atomic file replacement (e.g. `AtomicFile` or temp file + `renameTo`) to prevent data corruption.
4. **Synchronized A/V Speed:** Speed changes must use unified Media3 APIs (`EditedMediaItem.Builder#setSpeed()` or experimental speed changing effects) to avoid progressive A/V drift.
5. **Test Proof:** Every PR must include unit test verification.
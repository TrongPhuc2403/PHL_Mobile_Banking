# Legacy source verification suite

This is an archived design/source/APK check suite, **not an end-to-end banking/security test**. It is kept for traceability rather than used as a release gate.

Known stale assumptions include fixed Retrofit endpoint counts, old demo usernames/account numbers, an Android-only modification cutoff, string-exact Java formatting and an unconditional camera-enable expression. The current app intentionally keeps camera/OTP/eKYC gates conditional. Do not weaken application security to satisfy those assertions.

Generated output in `reports/` and `build/` is ignored by Git. The previous239/245 baseline is historical, not the result of the current formatted source. The PowerShell runner also retains its original scope-policy checks and may report out-of-Android changes for this full-stack project.

Current verification:

```powershell
cd ../../backend
npm run format:check
npm run typecheck
npm run test:security
npm run test:enrollment
npm run build
python face_engine/test_engine.py
cd ../android
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug --no-daemon
```

The instrumented UI smoke test navigates visible controls. Camera permission must be granted by the user; no forced permission/identity bypass is used. Full webcam enrollment is still unverified.

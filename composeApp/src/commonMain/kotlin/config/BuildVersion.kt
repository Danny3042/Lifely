package config

/**
 * The installed app's version as the store sees it:
 * Android → versionName in androidApp/build.gradle.kts; iOS → Version (MARKETING_VERSION) in Xcode.
 */
expect fun appVersionName(): String

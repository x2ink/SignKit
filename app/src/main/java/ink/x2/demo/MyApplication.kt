package ink.x2.demo

import android.app.Application
import android.util.Log
import ink.x2.logkit.LogKit
import java.io.File

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val logDirectory = File(
            requireNotNull(getExternalFilesDir(null)),
            "logs",
        )
        check(logDirectory.isDirectory || logDirectory.mkdirs()) {
            "无法创建日志目录：${logDirectory.absolutePath}"
        }

        LogKit.init(
            LogKit.Config(
                enable = true,
                minLevel = Log.VERBOSE,
                logFilePath = logDirectory,
                logMaxBuffer = 5L * 1024 * 1024,
                logType = LogKit.LogType.LINE,
                lineSize = 4000,
            ),
        )
    }
}
package com.shams.srk.nrtoggle.shizuku

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object ShizukuShell {

    private const val TAG = "ShizukuShell"
    private const val TIMEOUT_SEC = 3L

    private val newProcessMethod = AtomicReference<Method?>()
    private val execLock = ReentrantLock()

    fun isReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Runs a shell command as the Shizuku (ADB shell) identity.
     * Serialized — concurrent execs race Shizuku's Process streams.
     */
    fun exec(command: String): Result = execLock.withLock {
        if (!isReady()) {
            return Result(exitCode = -1, stdout = "", stderr = "Shizuku not ready")
        }
        return try {
            val process = newRemoteProcess(arrayOf("sh", "-c", command))
                ?: return Result(exitCode = -1, stdout = "", stderr = "newProcess unavailable")
            try {
                process.outputStream.close()
            } catch (_: Throwable) {
            }

            val timedOut = AtomicBoolean(false)
            val watchdog = Thread {
                try {
                    Thread.sleep(TIMEOUT_SEC * 1000)
                    timedOut.set(true)
                    process.destroyForcibly()
                } catch (_: InterruptedException) {
                }
            }.also { it.isDaemon = true; it.start() }

            val stdoutBox = AtomicReference("")
            val stderrBox = AtomicReference("")
            val outThread = Thread {
                try {
                    stdoutBox.set(process.inputStream.bufferedReader().use(BufferedReader::readText))
                } catch (_: Throwable) {
                }
            }.also { it.isDaemon = true; it.start() }
            val errThread = Thread {
                try {
                    stderrBox.set(process.errorStream.bufferedReader().use(BufferedReader::readText))
                } catch (_: Throwable) {
                }
            }.also { it.isDaemon = true; it.start() }

            outThread.join(TIMEOUT_SEC * 1000)
            errThread.join(1_000)
            watchdog.interrupt()
            try {
                watchdog.join(400)
            } catch (_: Throwable) {
            }

            if (timedOut.get()) {
                return Result(
                    exitCode = -1,
                    stdout = stdoutBox.get().trim(),
                    stderr = "timeout after ${TIMEOUT_SEC}s"
                )
            }

            val code = try {
                process.waitFor()
            } catch (_: Throwable) {
                try {
                    process.exitValue()
                } catch (_: IllegalThreadStateException) {
                    -1
                }
            }
            Result(
                exitCode = code,
                stdout = stdoutBox.get().trim(),
                stderr = stderrBox.get().trim()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "exec failed: $command", t)
            Result(exitCode = -1, stdout = "", stderr = t.message ?: t.toString())
        }
    }

    private fun newRemoteProcess(args: Array<String>): Process? {
        val method = newProcessMethod.get() ?: synchronized(this) {
            newProcessMethod.get() ?: run {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                m.isAccessible = true
                newProcessMethod.set(m)
                m
            }
        }
        return method.invoke(null, args, null, null) as Process
    }

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}

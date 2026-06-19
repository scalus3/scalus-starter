package starter

import scribe.format.*

/** Logging configuration.
  *
  * Scalus logs via scribe. Its default formatter includes the thread name, e.g.
  * {{{2026.06.19 10:16:51:892 scala-execution-context-global-113 INFO scalus...submit:595}}}
  * This reconfigures the root logger with the same layout but without the thread name.
  */
object Logging {

    /** scribe's default format minus the `$threadName` block. */
    private val formatterWithoutThread: Formatter =
        formatter"$dateFull $levelColored - $messages$mdc"

    /** Apply the thread-less formatter to the root logger. Idempotent. */
    def configure(): Unit = {
        scribe.Logger.root
            .clearHandlers()
            .withHandler(formatter = formatterWithoutThread)
            .replace()
    }
}

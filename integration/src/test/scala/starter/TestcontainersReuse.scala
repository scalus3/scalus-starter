package starter

import java.nio.file.{Files, Paths}
import java.util.Properties
import scala.util.Using

/** Detects whether Testcontainers container reuse is enabled.
  *
  * Mirrors testcontainers-java semantics: the `TESTCONTAINERS_REUSE_ENABLE` environment variable
  * takes precedence, falling back to `testcontainers.reuse.enable` in
  * `~/.testcontainers.properties`.
  */
object TestcontainersReuse {

    lazy val enabled: Boolean = sys.env.get("TESTCONTAINERS_REUSE_ENABLE") match
        case Some(value) => value.trim.equalsIgnoreCase("true")
        case None        => propertiesFileEnabled

    private def propertiesFileEnabled: Boolean = {
        val path = Paths.get(sys.props("user.home"), ".testcontainers.properties")
        Files.exists(path) && {
            val props = new Properties()
            Using.resource(Files.newInputStream(path))(props.load)
            Option(props.getProperty("testcontainers.reuse.enable"))
                .exists(_.trim.equalsIgnoreCase("true"))
        }
    }
}

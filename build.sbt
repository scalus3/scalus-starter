val scalusVersion = "0.14.1"
val scalusPluginVersion = scalusVersion

resolvers += Resolver.sonatypeCentralSnapshots

// Latest Scala 3 LTS version
ThisBuild / scalaVersion := "3.3.7"

ThisBuild / scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked")

// Add the Scalus compiler plugin
addCompilerPlugin("org.scalus" %% "scalus-plugin" % scalusPluginVersion)

// Main application
lazy val core = (project in file("."))
    .settings(
      libraryDependencies ++= Seq(
        // Scalus
        "org.scalus" %% "scalus" % scalusVersion,
        "org.scalus" %% "scalus-testkit" % scalusVersion,
        "org.scalus" %% "scalus-bloxbean-cardano-client-lib" % scalusVersion,
        // Cardano Client library
        "com.bloxbean.cardano" % "cardano-client-lib" % "0.7.1",
        "com.bloxbean.cardano" % "cardano-client-backend-blockfrost" % "0.7.1",
        // Tapir for API definition
        "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.13.3",
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.13.3",
        // Argument parsing
        "com.monovore" %% "decline" % "2.5.0",
        "org.slf4j" % "slf4j-simple" % "2.0.17"
      ),
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.2.19" % Test,
        "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test,
        "org.scalacheck" %% "scalacheck" % "1.19.0" % Test
      )
    )

// Integration tests
lazy val integration = (project in file("integration"))
    .dependsOn(core) // your current subproject
    .settings(
      publish / skip := true,
      // test dependencies
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.2.19" % Test,
        "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test,
        "org.scalacheck" %% "scalacheck" % "1.19.0" % Test,
        // Testcontainers for integration testing
        "com.dimafeng" %% "testcontainers-scala-core" % "0.41.5" % Test,
        "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.5" % Test,
        // Yaci DevKit for Cardano local devnet
        "com.bloxbean.cardano" % "yaci-cardano-test" % "0.1.0" % Test
      )
    )

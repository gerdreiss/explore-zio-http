val scala3Version = "3.2.0"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "explore-zio-http",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"         % "2.0.2",
      "dev.zio" %% "zio-streams" % "2.0.2",
      "dev.zio" %% "zio-json"    % "0.3.0",
      "io.d11"  %% "zhttp"       % "2.0.0-RC11",
    ),
  )

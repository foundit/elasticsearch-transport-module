import sbt.Keys._
import sbt._

object Build extends Build {
  val foundOrganizationName = "Found AS"
  val foundOrgnizationPrefix = "no.found"

  val elasticsearchVersion = "1.0.0"

  val transportOrganization = foundOrgnizationPrefix + ".elasticsearch"
  val transportName = "elasticsearch-transport-module"
  val transportVersion = "0.8.6-1000-SNAPSHOT"

  var transportDependencies = Seq[ModuleID]()

  transportDependencies ++= Seq(
    "org.elasticsearch" % "elasticsearch" % elasticsearchVersion % "provided",

    "junit" % "junit" % "4.11" % "test",

    "org.mockito" % "mockito-all" % "1.9.5" % "test"
  )

  lazy val root = Project(id = transportName, base=file("."), settings = Project.defaultSettings).settings(
    organizationName := foundOrganizationName,
    organization := transportOrganization,

    // don't build with _{scalaVersion}-suffix
    crossPaths := false,

    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (version.value.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false,

    licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),

    homepage := Some(url("https://github.com/foundit/elasticsearch-transport-module")),

    pomExtra := <scm>
        <url>git@github.com:foundit/elasticsearch-transport-module.git</url>
        <connection>scm:git:git@github.com:foundit/elasticsearch-transport-module.git</connection>
      </scm>
      <developers>
        <developer>
          <id>nkvoll</id>
          <name>Njal Karevoll</name>
          <url>http://www.found.no</url>
        </developer>
      </developers>,

    version := transportVersion,

    scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature"),

    libraryDependencies := transportDependencies
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)

  lazy val integration = Project("integration", file("./integration"))
    .dependsOn(root, root % "test->test")
    .settings(Project.defaultSettings : _*)
    .settings(
      organizationName := foundOrganizationName,

      libraryDependencies ++= Seq(
        "org.elasticsearch" % "elasticsearch" % elasticsearchVersion
      ),

      // Integration tests are not intended to be run in parallel.
      parallelExecution in Test := false,
      logBuffered in Test := false
    )

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }
}

import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin._
import sbtrelease.ReleaseStep
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Utilities._
import com.typesafe.sbt.SbtPgp.PgpKeys._

object Build extends Build {
  val foundOrganizationName = "Found AS"
  val foundOrganizationPrefix = "no.found"

  val elasticsearchVersion = "1.3.4"

  val transportOrganization = foundOrganizationPrefix + ".elasticsearch"
  val transportName = "elasticsearch-transport-module"

  var transportDependencies = Seq[ModuleID]()

  transportDependencies ++= Seq(
    "org.elasticsearch" % "elasticsearch" % elasticsearchVersion % "provided",
    "junit" % "junit" % "4.11" % "test",
    "org.mockito" % "mockito-all" % "1.9.5" % "test",
    "log4j" % "log4j" % "1.2.17" % "test",
    "com.novocode" % "junit-interface" % "0.10" % "test"
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

    scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature"),

    libraryDependencies := transportDependencies
  ).settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*).settings(releaseSettings: _*).settings(
      ReleaseKeys.releaseVersion := { ver => ver.replace("-SNAPSHOT", "") },
      ReleaseKeys.nextVersion := { ver =>
        if(ver.contains("-")) {
          val parts = ver.split("-", 2)
          val part0 = sbtrelease.Version(parts(0)).map(_.bumpBugfix.string).getOrElse(sbtrelease.versionFormatError)
          val part1 = sbtrelease.Version(parts(1)).map(_.asSnapshot.string).getOrElse(sbtrelease.versionFormatError)
          s"$part0-$part1"
        } else ver
      },
      ReleaseKeys.releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        runTest,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        publishArtifacts.copy(action = publishSignedAction),
        setNextVersion,
        commitNextVersion,
        pushChanges
      )
  )

  lazy val publishSignedAction = { st: State =>
    val extracted = st.extract
    val thisBuildVersion = extracted.get(version in ThisBuild)

    val nst = extracted.append(Seq(version := thisBuildVersion), st)

    val nextracted = nst.extract
    val ref = nextracted.get(thisProjectRef)

    nextracted.runAggregated(publishSigned in ThisBuild in ref, nst)
  }

  lazy val integration = Project("integration", file("./integration"))
    .dependsOn(root, root % "test->test")
    .settings(Project.defaultSettings : _*)
    .settings(
      organizationName := foundOrganizationName,

      libraryDependencies ++= Seq(
        "org.elasticsearch" % "elasticsearch" % elasticsearchVersion,
        "log4j" % "log4j" % "1.2.17"
      ),

      // Integration tests are not intended to be run in parallel.
      parallelExecution in Test := false,
      logBuffered in Test := false,

      publish := (),
      publishLocal := (),
      // required until these tickets are closed https://github.com/sbt/sbt-pgp/issues/42,
      // https://github.com/sbt/sbt-pgp/issues/36
      publishTo := None
    )

  // configure prompt to show current project
  override lazy val settings = super.settings :+ {
    shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
  }
}

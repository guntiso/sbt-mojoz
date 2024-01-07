name := "sbt-mojoz-test"

organization := "org.mojoz"

version := "0.1"

scalaVersion := "2.12.18"

lazy val root = (project in file(".")).enablePlugins(MojozPlugin, MojozGenerateSchemaPlugin)

mojozMdConventions := org.mojoz.metadata.io.MdConventions

mojozDtosImports := Seq("sbtmojoz.test._")

mojozSchemaSqlDirectory := file("db/creation")

mojozQuerease :=
  new org.mojoz.querease.Querease {
    override lazy val yamlMetadata        = mojozRawYamlMetadata.value
    override lazy val metadataConventions = mojozMdConventions.value
    override lazy val typeDefs            = mojozTypeDefs.value
    override lazy val tableMetadata       = mojozTableMetadata.value
    override lazy val macrosClass         = mojozTresqlMacrosClass.value.orNull
    override lazy val resourceLoader      = mojozResourceLoader.value
    override lazy val uninheritableExtras = mojozUninheritableExtras.value
    override def compileAllQueries(
      previouslyCompiledQueries: Set[String],
      showFailedViewQuery: Boolean,
      log: => String => Unit,
    ): (Set[String], Map[String, Array[Byte]]) = {
      val (compiledQueries, _) = super.compileAllQueries(previouslyCompiledQueries, showFailedViewQuery, log)
      (compiledQueries, Map("my-qe-cache.txt" -> "[my-qe-cache-body]".getBytes("UTF-8")))
    }
  },

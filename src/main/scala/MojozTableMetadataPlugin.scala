package org.mojoz


import org.mojoz.metadata._
import org.mojoz.metadata.in.{YamlMd, YamlTableDefLoader, YamlTypeDefLoader}
import org.mojoz.metadata.io.MdConventions
import org.mojoz.querease.TresqlMetadata
import sbt.Keys.{baseDirectory, resourceGenerators, resourceManaged, unmanagedResources}
import sbt._
import sbt.plugins.JvmPlugin

import java.io.InputStream

object MojozTableMetadataPlugin extends AutoPlugin {
  object autoImport {
    val mojozCompilerCacheFolder = settingKey[File]("Mojoz compiler cache folder")
    val mojozResourceLoader = taskKey[String => InputStream]("Resource loader for compilation")

    val mojozMdConventions = taskKey[MdConventions]("Mojoz metadata conventions")
    val mojozMetadataFileFilterPredicate = settingKey[File => Boolean]("Predicate to filter files in metadata folders")
    val mojozDbNaming = settingKey[String => String]("Db naming rules. Transformation function from metadata name to database name")

    val mojozCustomTypesFile = taskKey[Option[File]]("Mojoz custom types file")
    val mojozTypeDefs = taskKey[collection.immutable.Seq[TypeDef]]("Mojoz type definitions")

    val mojozTableMetadataFolders = settingKey[Seq[File]]("Mojoz table metadata folders")
    val mojozTableMetadataFiles = taskKey[Seq[(File, String)]]("All table metadata files + relative paths they are kept in")
    val mojozRawTableMetadata = taskKey[Seq[YamlMd]]("Raw table metadata")
    val mojozTableMetadata = taskKey[TableMetadata]("Table metadata")
    val mojozDbNames       = taskKey[Seq[String]]("Database names extracted from mojozTableMetadata, used by other tasks. May contain null for default database")

    val mojozTresqlTableMetadataFileName = settingKey[String]("File name for tresql table metadata for compiler, defaults to \"tresql-table-metadata.yaml\"")
    val mojozGenerateTresqlTableMetadata = taskKey[File]("Generates tresql table metadata for tresql compiler")
  }

  import autoImport._
  override def trigger = noTrigger
  override def requires = JvmPlugin

  override val projectSettings = Seq(
    mojozCompilerCacheFolder :=
      (Compile / resourceManaged).value,
    mojozResourceLoader := {
      (r: String) =>
        ((Compile / unmanagedResources).value ++
         (mojozCompilerCacheFolder.value ** "*").get
        )
          .find(_.getAbsolutePath endsWith r)
          .map(new java.io.FileInputStream(_)).getOrElse(getClass.getResourceAsStream(r))
    },
    mojozTableMetadataFolders := Seq(baseDirectory .value / "tables"),
    mojozMetadataFileFilterPredicate := (f => f.getName.endsWith(".yaml")),
    mojozDbNaming := identity,

    mojozTableMetadataFiles := mojozTableMetadataFolders.value.flatMap { mojozTableMetadataFolder =>
      Path.selectSubpaths(mojozTableMetadataFolder, _.isFile).map {
        case (f, p) => (f, mojozTableMetadataFolder.getName + "/" + p.replace('\\', '/'))
      }.filter(f => mojozMetadataFileFilterPredicate.value(f._1))
    },
    mojozRawTableMetadata := mojozTableMetadataFiles.value.map(_._1).flatMap(YamlMd.fromFile),

    mojozMdConventions := {
      import MdConventions._
      val resourceLoader = mojozResourceLoader.value
      new SimplePatternMdConventions(
        booleanNamePatternStrings  = namePatternsFromResource(defaultBooleanNamePatternSource,  resourceLoader),
        dateNamePatternStrings     = namePatternsFromResource(defaultDateNamePatternSource,     resourceLoader),
        dateTimeNamePatternStrings = namePatternsFromResource(defaultDateTimeNamePatternSource, resourceLoader),
      )
    },

    mojozCustomTypesFile :=
      ((Compile / unmanagedResources).value ** "mojoz-custom-types.yaml").get.headOption,

    mojozTypeDefs :=
      mojozCustomTypesFile.value
        .map(YamlMd.fromFile)
        .map(new YamlTypeDefLoader(_).typeDefs)
        .map(TypeMetadata.mergeTypeDefs(_, TypeMetadata.defaultTypeDefs))
        .getOrElse(TypeMetadata.customizedTypeDefs),

    mojozTableMetadata := new TableMetadata(new YamlTableDefLoader(mojozRawTableMetadata.value.toList, mojozMdConventions.value, mojozTypeDefs.value).tableDefs, mojozDbNaming.value),
    mojozDbNames       := mojozTableMetadata.value.tableDefs.map(_.db).distinct.sortBy(Option(_) getOrElse ""),

    mojozTresqlTableMetadataFileName := ((Compile / resourceManaged).value / "tresql-table-metadata.yaml").getAbsolutePath,
    mojozGenerateTresqlTableMetadata := {
      val file =  new File(mojozTresqlTableMetadataFileName.value)
      val contents = new TresqlMetadata(mojozTableMetadata.value.tableDefs.sortBy(_.name), mojozTypeDefs.value).tableMetadataString
      IO.write(file, contents)
      file
    },

    Compile / resourceGenerators += mojozGenerateTresqlTableMetadata.map(Seq(_)).taskValue
  )
}

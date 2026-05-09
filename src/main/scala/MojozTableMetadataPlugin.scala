package org.mojoz


import org.mojoz.metadata.*
import org.mojoz.metadata.in.{YamlMd, YamlTableDefLoader, YamlTypeDefLoader}
import org.mojoz.metadata.io.MdConventions
import org.mojoz.querease.{QuereaseMetadata, TresqlMetadata}
import sbt.Keys.{baseDirectory, resourceDirectories, resourceGenerators, resourceManaged, unmanagedResources}
import sbt.*
import sbt.plugins.JvmPlugin

import java.io.InputStream

object MojozTableMetadataPlugin extends AutoPlugin {
  object autoImport {
    val mojozMdConventions = taskKey[MdConventions]("Mojoz metadata conventions")
    val mojozMetadataFileFilterPredicate = settingKey[File => Boolean]("Predicate to filter files in metadata folders")
    val mojozDbNaming = settingKey[String => String]("Db naming rules. Transformation function from metadata name to database name")

    val mojozCustomTypesFile = taskKey[Option[File]]("Mojoz custom types file")
    val mojozTypeDefs = taskKey[collection.immutable.Seq[TypeDef]]("Mojoz type definitions")

    val mojozDbAliasToDb = taskKey[Map[String, String]]("Mojoz database alias to database for views")
    val mojozTableMetadataFolders = settingKey[Seq[File]]("Mojoz table metadata folders")
    val mojozTableMetadataFiles = taskKey[Seq[(File, String)]]("All table metadata files + relative paths they are kept in")
    val mojozRawTableMetadata = taskKey[Seq[YamlMd]]("Raw table metadata")
    val mojozTableMetadata = taskKey[TableMetadata]("Table metadata")

    val mojozTresqlTableMetadataFileName = settingKey[String]("File name for tresql table metadata for compiler, defaults to \"tresql-table-metadata.yaml\"")
    val mojozGenerateTresqlTableMetadata = taskKey[File]("Generates tresql table metadata for tresql compiler")
  }

  import autoImport._
  override def trigger = noTrigger
  override def requires = JvmPlugin

  override val projectSettings = Seq(
    mojozTableMetadataFolders := Seq(baseDirectory .value / "tables"),
    mojozMetadataFileFilterPredicate := (f => f.getName.endsWith(".yaml")),
    mojozDbNaming := identity,

    mojozTableMetadataFiles := mojozTableMetadataFolders.value.flatMap { mojozTableMetadataFolder =>
      Path.selectSubpaths(mojozTableMetadataFolder, _.isFile).map {
        case (f, p) => (f, mojozTableMetadataFolder.getName + "/" + p.replace('\\', '/'))
      }.filter(f => mojozMetadataFileFilterPredicate.value(f._1))
    },
    mojozRawTableMetadata := mojozTableMetadataFiles.value.map(_._1).flatMap(YamlMd.fromFile),

    mojozMdConventions :=
      new MdConventions.SimplePatternMdConventions(mojozResourceLoader((Compile / resourceDirectories).value)),

    mojozCustomTypesFile :=
      ((Compile / unmanagedResources).value ** "mojoz-custom-types.yaml").get.headOption,

    mojozTypeDefs :=
      mojozCustomTypesFile.value
        .map(YamlMd.fromFile)
        .map(new YamlTypeDefLoader(_).typeDefs)
        .map(TypeMetadata.mergeTypeDefs(_, TypeMetadata.defaultTypeDefs))
        .getOrElse(TypeMetadata.customizedTypeDefs),

    mojozDbAliasToDb   := QuereaseMetadata.aliasToDb(mojozResourceLoader((Compile / resourceDirectories).value)),
    mojozTableMetadata :=
      new TableMetadata(
        new YamlTableDefLoader(mojozRawTableMetadata.value.toList, mojozMdConventions.value, mojozTypeDefs.value).tableDefs,
        mojozDbNaming.value,
        mojozDbAliasToDb.value,
      ),

    mojozTresqlTableMetadataFileName := ((Compile / resourceManaged).value / "tresql-table-metadata.yaml").getAbsolutePath,
    mojozGenerateTresqlTableMetadata := {
      val file =  new File(mojozTresqlTableMetadataFileName.value)
      val contents = new TresqlMetadata(mojozTableMetadata.value.tableDefs.sortBy(_.name), mojozTypeDefs.value).tableMetadataString
      IO.write(file, contents)
      file
    },

    Compile / resourceGenerators += mojozGenerateTresqlTableMetadata.map(Seq(_)).taskValue
  )

  def mojozResourceLoader(files: Seq[File]) = (r: String) => MojozPlugin
    .getMojozResourceClassLoader(files)
    .getResourceAsStream(r.stripPrefix("/"))  // URL class loader probably will fail with resource prefix '/'
}

package org.mojoz

import mojoz.metadata._
import mojoz.metadata.in.{YamlMd, YamlViewDefLoader}
import org.tresql.MacroResourcesImpl
import org.tresql.compiling.CompilerFunctionMetadata
import querease._
import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.util.FileInfo

import scala.compat.Platform
import scala.util.control.NonFatal

object MojozPlugin extends AutoPlugin {
  object autoImport {

    val mojozDtosPackage = settingKey[String]("Package where all dtos should be placed in")
    val mojozDtosImports = settingKey[Seq[String]]("List of imports for Dtos.scala")

    val mojozViewMetadataFolders = settingKey[Seq[File]]("Mojoz view metadata folders")

    val mojozViewMetadataFiles = taskKey[Seq[(File, String)]]("All view metadata files + relative paths they are kept in")

    val mojozMetadataFilesForResources = taskKey[Seq[(File, String)]]("All metadata files to be copied to resources and included in -md-files.txt")
    val mojozMdFilesFileName = settingKey[String]("File name for metadata filename list of all table and view files, defaults to \"-md-files.txt\"")
    val mojozShouldGenerateMdFileList = settingKey[Boolean]("Should -md-files.txt be generated, defaults to true")
    val mojozGenerateMdFileList = taskKey[Seq[File]]("Generates -md-files.txt")

    val mojozRawViewMetadata = taskKey[Seq[YamlMd]]("Raw view metadata")

    val mojozViewMetadataLoader = taskKey[YamlViewDefLoader]("View metadata loader")
    val mojozViewMetadata = taskKey[List[ViewDef[FieldDef[Type]]]]("View metadata")
    val mojozShouldCompileViews = settingKey[Boolean]("Should views be compiled, defaults to true")
    val mojozShowFaildedViewQuery = settingKey[Boolean]("Show query string if view fails to compile, defaults to false")
    val mojozCompileViews = taskKey[Unit]("View compilation task")
    val mojozAllSourceFiles = taskKey[Seq[File]]("All source files - for mojozCompileViews cache invalidation. Customize if mojozTresqlMacros and / or mojozFunctionSignaturesClass is customized")
    val mojozFunctionSignaturesClass = settingKey[Class[_]]("Function signatures class for view compilation")
    val mojozQuerease = taskKey[Querease]("Creates an instance of Querease for view compilation etc.")
    val mojozTresqlMacros = settingKey[Option[Any]]("Object containing tresql compiler macro functions")
    val mojozGenerateDtosScalaFileName = settingKey[String]("File name where dtos are stored, default  Dtos.scala")
    val mojozGenerateDtosViewMetadata = taskKey[List[ViewDef[FieldDef[Type]]]]("View metadata for dtos generation")
    val mojozGenerateDtosScala = taskKey[File]("Generates Dtos.scala")
    val mojozGenerateDtosMappingsScala = taskKey[String]("Generates extras (name to class mappings etc.) for Dtos.scala")
    val mojozGeneratedFiles = taskKey[Seq[String]]("Lists files generated by MojozPlugin")
    val mojozScalaClassWriter = taskKey[mojoz.metadata.out.ScalaClassWriter]("Creates scala (i.e. Dtos) generator")
  }

  import autoImport._
  import MojozTableMetadataPlugin.autoImport._
  override def trigger = noTrigger
  override def requires = JvmPlugin && MojozTableMetadataPlugin

  override val projectSettings = Seq(

    mojozDtosPackage := "dto",
    mojozDtosImports := Seq(
      "org.tresql._",
    ),

    mojozViewMetadataFolders := Seq(baseDirectory.value / "views"),
    mojozViewMetadataFiles := mojozViewMetadataFolders.value.flatMap { mojozViewMetadataFolder =>
      Path.selectSubpaths(mojozViewMetadataFolder, _.isFile).map {
        case (f, p) => (f, mojozViewMetadataFolder.getName + "/" + p)
      }.filter(f => mojozMetadataFileFilterPredicate.value(f._1))
    },

    mojozMetadataFilesForResources := mojozTableMetadataFiles.value ++ mojozViewMetadataFiles.value,
    mojozMdFilesFileName := ((resourceManaged in Compile).value / "-md-files.txt").getAbsolutePath,

    mojozShouldGenerateMdFileList := true,
    mojozGenerateMdFileList := {
      if(mojozShouldGenerateMdFileList.value) {
        val file = new File(mojozMdFilesFileName.value)
        val contents = mojozMetadataFilesForResources.value.map(_._2).sorted.mkString("", "\n", "\n")
        IO.write(file, contents)
        Seq(file)
      } else Seq()
    },

    resourceGenerators in Compile += mojozGenerateMdFileList.taskValue,

    mojozRawViewMetadata := mojozViewMetadataFiles.value.map(_._1).flatMap(YamlMd.fromFile),

    mojozViewMetadataLoader := YamlViewDefLoader(
      mojozTableMetadata.value,
      mojozRawViewMetadata.value.toList,
      TresqlJoinsParser(mojozTableMetadata.value.tableDefs, mojozTypeDefs.value, mojozFunctionSignaturesClass.value),
      mojozMdConventions.value,
      collection.immutable.Seq(),
      mojozTypeDefs.value),

    mojozViewMetadata := mojozViewMetadataLoader.value.viewDefs,
    mojozGenerateDtosViewMetadata := mojozViewMetadata.value,

    mojozFunctionSignaturesClass := classOf[org.tresql.compiling.TresqlFunctionSignatures],
    mojozTresqlMacros := None,
    mojozShouldCompileViews := true,
    mojozShowFaildedViewQuery := false,
    mojozQuerease := {
      val tableMd = mojozTableMetadata.value
      val xViewDefs = mojozViewMetadataLoader.value.extendedViewDefs
      val childViews = xViewDefs.values.flatMap(_.fields.filter(_.type_.isComplexType)).map(_.type_.name).toSet
      new Querease with ScalaDtoQuereaseIo {
        override lazy val typeDefs = mojozTypeDefs.value
        override lazy val viewDefs = xViewDefs.asInstanceOf[Map[String, ViewDef]]
        override lazy val tableMetadata = tableMd
        override lazy val functionSignaturesClass: Class[_] = mojozFunctionSignaturesClass.value
        override lazy val joinsParser = TresqlJoinsParser(mojozTableMetadata.value.tableDefs, mojozTypeDefs.value, mojozFunctionSignaturesClass.value)
      }
    },
    mojozAllSourceFiles := {
      Seq(
        mojozMdConventionsResources.value,
        mojozCustomTypesFile.value.toSeq,
        mojozTableMetadataFiles.value.map(_._1),
        mojozViewMetadataFiles.value.map(_._1),
        // TODO include macros files in mojozAllSourceFiles,
        // TODO include function signatures files in mojozAllSourceFiles,
      ).flatMap(x => x)
    },
    mojozCompileViews := {
      // TODO recompile only changed views
      def compileViews: String = {
        val tableMd = mojozTableMetadata.value
        val xViewDefs = mojozViewMetadataLoader.value.extendedViewDefs
        val childViews = xViewDefs.values.flatMap(_.fields.filter(_.type_.isComplexType)).map(_.type_.name).toSet
        val qe = mojozQuerease.value

        val log = streams.value.log
        val startTime = Platform.currentTime
        val viewsToCompile =
          xViewDefs.values.toList
          .filter(viewDef => !childViews.contains(viewDef.name)) // compile only top-level views
          .filter(viewDef =>
            viewDef.fields != null && viewDef.fields.nonEmpty &&
            (viewDef.table != null || viewDef.joins != null && viewDef.joins.nonEmpty)
          )
          .sortBy(_.name)
        log.info(s"Compiling ${viewsToCompile.size} top-level, table-based views (${xViewDefs.size} views total)")
        val compiler = new org.tresql.compiling.Compiler {
          override val macros = new MacroResourcesImpl(mojozTresqlMacros.value.orNull)
          override val metadata = new TresqlMetadata(tableMd.tableDefs, mojozTypeDefs.value) with CompilerFunctionMetadata {
            override def compilerFunctionSignatures = mojozFunctionSignaturesClass.value
          }
        }
        viewsToCompile
          .foreach { viewDef =>
            val q = qe.queryStringAndParams(viewDef, Map.empty)._1
            // TODO cache it for identical compiler
            try compiler.compile(compiler.parseExp(q)) catch { case NonFatal(ex) =>
              val msg = s"\nFailed to compile viewdef ${viewDef.name}: ${ex.getMessage}" +
                (if (mojozShowFaildedViewQuery.value) s"\n$q" else "")
              throw new RuntimeException(msg, ex)
            }
          }
        val endTime = Platform.currentTime
        log.info(s"View compilation done in ${endTime - startTime} ms")
        s"${viewsToCompile.size} views compiled"
      }
      import sbt.util.CacheImplicits._
      import scala.language.existentials
      val cacheStore = streams.value.cacheStoreFactory make "mojoz-all-source-file-hashes"
      val allSourceFiles = mojozAllSourceFiles.value
      val cachedCompileViews = Tracked.inputChanged[Seq[HashFileInfo], String](cacheStore) {
        case (isChanged: Boolean, _) => if (isChanged) compileViews else "not changed"
      }
      cachedCompileViews(allSourceFiles.map(FileInfo.hash(_)))
    },

    mojozScalaClassWriter := new querease.ScalaDtoGenerator(mojozQuerease.value),
    mojozGenerateDtosScalaFileName := "Dtos.scala",

    mojozGenerateDtosMappingsScala := {
      val tableMd = mojozTableMetadata.value
      val viewDefs = mojozGenerateDtosViewMetadata.value
      val classBuilder = mojozScalaClassWriter.value

      val mapping = s"""
        |object Tables {
        |  ${tableMd.tableDefs.map(t => s"class ${classBuilder.scalaClassName(t.name)} {}").mkString("\n  ")}
        |}
        |object DtoMapping {
        |  val viewNameToClass = Map[String, Class[_ <: Dto]](
        |    ${viewDefs.map(v => s""""${v.name}" -> classOf[${classBuilder.scalaClassName(v.name)}]""").mkString(",\n    ")}
        |  )
        |  val viewClassToTableClass = Map[Class[_ <: Dto], Class[_]](
        |    ${viewDefs.filter(_.table != null).map(v =>
                s"classOf[${classBuilder.scalaClassName(v.name)}] -> classOf[Tables.${classBuilder.scalaClassName(v.table)}]"
               ).mkString(",\n    ")
             }
        |  )
        |}
        |""".stripMargin.trim
      mapping
    },

    mojozGenerateDtosScala := {
      val compiled = mojozCompileViews.value

      val file = (sourceManaged in Compile).value / mojozGenerateDtosScalaFileName.value
      val tableMd = mojozTableMetadata.value
      val viewDefs = mojozGenerateDtosViewMetadata.value
      val classBuilder = mojozScalaClassWriter.value

      val mapping = mojozGenerateDtosMappingsScala.value
      val contents = classBuilder.createScalaClassesString(
        List("package "+mojozDtosPackage.value, "") ++ mojozDtosImports.value.map("import "+_) ++ List(""), viewDefs, List("", mapping))
      IO.write(file, contents)
      file
    },

    sourceGenerators in Compile += mojozGenerateDtosScala.map(Seq(_)).taskValue,

    copyResources in Compile := {
      val taskStreams = streams.value
      val classDir = (classDirectory in Compile).value
      val cacheStore = streams.value.cacheStoreFactory make "copy-resources"
      val mappings = mojozMetadataFilesForResources.value.map(f => (f._1, classDir / f._2))

      taskStreams.log.debug("result" + mappings.mkString("\n\t","\n\t",""))
      Sync.sync(cacheStore)( mappings )
      (copyResources in Compile).value ++ mappings
    },

    mojozGeneratedFiles := {
      val tableMd = mojozTableMetadata.value
      val viewDefs = mojozGenerateDtosViewMetadata.value
      val classBuilder = mojozScalaClassWriter.value
      val packagePath = mojozDtosPackage.value.replaceAllLiterally(".", "/")
      def classFilePathFromName(name: String) = packagePath + "/" + name + ".class"
      Seq(mojozMdFilesFileName.value, mojozGenerateDtosScalaFileName.value) ++
        Seq("Tables$", "Tables", "DtoMapping$", "DtoMapping").map(classFilePathFromName) ++
        viewDefs.map(v => classFilePathFromName(classBuilder.scalaClassName(v.name))) ++
        tableMd.tableDefs.map(v => classFilePathFromName("Tables$" + classBuilder.scalaClassName(v.name)))
    },

    // sbt tilde must watch changes in yaml files
    watchSources := {
      watchSources.value ++
      mojozTableMetadataFolders.value.map(WatchSource(_)) ++
      mojozViewMetadataFolders.value.map(WatchSource(_)) ++
      mojozTableMetadataFiles.value.map(f => WatchSource(f._1)) ++
      mojozViewMetadataFiles.value.map(f => WatchSource(f._1))
    }
  )
}

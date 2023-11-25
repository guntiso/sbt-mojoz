package org.mojoz

import org.mojoz.metadata.ViewDef
import org.mojoz.metadata.in.{JoinsParser, YamlMd, YamlViewDefLoader}
import org.tresql.{Cache, MacroResourcesImpl, SimpleCache}
import org.mojoz.querease.{Querease, TresqlJoinsParser, TresqlMetadata}
import sbt.{AutoPlugin, Compile, File, HashFileInfo, IO, NothingFilter, Path}
import sbt.{settingKey, SimpleFilter, Sync, taskKey, Tracked, WatchSource}
import sbt.Keys._
import sbt.plugins.JvmPlugin

import java.io.InputStream
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
    val mojozViewMetadata = taskKey[List[ViewDef]]("View metadata")
    val mojozShouldCompileViews = settingKey[Boolean]("Should views be compiled, defaults to true")
    val mojozShowFailedViewQuery = settingKey[Boolean]("Show query string if view fails to compile, defaults to false")
    val mojozCompileViews = taskKey[Seq[File]]("View compilation task. Returns view compiler cache files")
    val mojozCompilerCacheFolder = settingKey[File]("Mojoz view compiler cache folder")
    val mojozResourceGenerators = taskKey[Seq[File]]("All mojoz resource generation tasks")
    val mojozSourceGenerators = taskKey[Seq[File]]("All mojoz source generation tasks")
    val mojozAllSourceFiles = taskKey[Seq[File]]("All mojoz source files - for source watch and view compilation")
    val mojozAllCompilerMetadataFiles = taskKey[Seq[File]]("All compiler metadata files - for mojozCompileViews cache invalidation. Customize if mojozTresqlMacrosClass is customized")
    val mojozJoinsParser = taskKey[JoinsParser]("Joins parser")
    val mojozJoinsParserCacheFactory = taskKey[String => Option[Cache]]("Joins parser cache factory")
    val mojozTresqlMacrosClass = settingKey[Option[Class[_]]] ("Macros class for view compilation. Defaults to org.tresql.Macros. Customization rarely needed - use tresql-macros.txt instead")
    val mojozQuerease = taskKey[Querease]("Creates an instance of Querease for view compilation etc.")
    val mojozGenerateDtosScalaFileName = settingKey[String]("File name where dtos are stored, default  Dtos.scala")
    val mojozGenerateDtosViewMetadata = taskKey[List[ViewDef]]("View metadata for dtos generation")
    val mojozGenerateDtosMappingsViewMetadata = taskKey[List[ViewDef]]("View metadata for dtos mappings generation")
    val mojozGenerateDtosScala = taskKey[File]("Generates Dtos.scala")
    val mojozGenerateDtosMappingsScala = taskKey[String]("Generates extras (name to class mappings etc.) for Dtos.scala")
    val mojozGeneratedFiles = taskKey[Seq[String]]("Lists files generated by MojozPlugin")
    val mojozResourceLoader = taskKey[String => InputStream]("Resource loader for view compilation")
    val mojozScalaGenerator = taskKey[org.mojoz.metadata.out.ScalaGenerator]("Creates scala (i.e. Dtos) generator")
    val mojozTresqlMetadata = taskKey[TresqlMetadata]("Tresql metadata for view compilation")
    val mojozUninheritableExtras = settingKey[collection.immutable.Seq[String]]("View extensions not to be inherited")
  }

  import autoImport._
  import MojozTableMetadataPlugin.autoImport._
  override def trigger = noTrigger
  override def requires = JvmPlugin && MojozTableMetadataPlugin

  import sbt._
  import sbt.util.FileInfo

  override val projectSettings = Seq(

    mojozDtosPackage := "dto",
    mojozDtosImports := Seq(
      "org.tresql._",
    ),

    mojozViewMetadataFolders := Seq(baseDirectory.value / "views"),
    mojozViewMetadataFiles := mojozViewMetadataFolders.value.flatMap { mojozViewMetadataFolder =>
      Path.selectSubpaths(mojozViewMetadataFolder, _.isFile).map {
        case (f, p) => (f, mojozViewMetadataFolder.getName + "/" + p.replace('\\', '/'))
      }.filter(f => mojozMetadataFileFilterPredicate.value(f._1))
    },

    mojozMetadataFilesForResources := mojozTableMetadataFiles.value ++ mojozViewMetadataFiles.value,
    mojozMdFilesFileName := ((Compile / resourceManaged).value / "-md-files.txt").getAbsolutePath,

    mojozShouldGenerateMdFileList := true,
    mojozGenerateMdFileList := {
      if(mojozShouldGenerateMdFileList.value) {
        val file = new File(mojozMdFilesFileName.value)
        val contents = mojozMetadataFilesForResources.value.map(_._2).toSet.toSeq.sorted.mkString("", "\n", "\n")
        IO.write(file, contents)
        Seq(file)
      } else Seq()
    },

    mojozResourceGenerators :=
      mojozCompileViews.value ++
      mojozGenerateMdFileList.value,

    mojozRawViewMetadata := mojozViewMetadataFiles.value.map(_._1).flatMap(YamlMd.fromFile),

    mojozJoinsParser := new TresqlJoinsParser(
      mojozTresqlMetadata.value,
      mojozJoinsParserCacheFactory.value,
    ),

    mojozJoinsParserCacheFactory := { _: String => Some(new SimpleCache(4096)) },

    mojozViewMetadataLoader := YamlViewDefLoader(
      mojozTableMetadata.value,
      mojozRawViewMetadata.value.toList,
      mojozJoinsParser.value,
      mojozMdConventions.value,
      mojozUninheritableExtras.value,
      mojozTypeDefs.value),

    mojozViewMetadata := mojozViewMetadataLoader.value.plainViewDefs,
    mojozGenerateDtosViewMetadata := mojozViewMetadata.value,
    mojozGenerateDtosMappingsViewMetadata := mojozViewMetadata.value,

    mojozTresqlMacrosClass := Some(classOf[org.tresql.Macros]),
    mojozShouldCompileViews := true,
    mojozShowFailedViewQuery := false,
    mojozResourceLoader := {
      (r: String) =>
        ((Compile / unmanagedResources).value ++
         (mojozCompilerCacheFolder.value ** "*").get
        )
          .find(_.getAbsolutePath endsWith r)
          .map(new java.io.FileInputStream(_)).getOrElse(getClass.getResourceAsStream(r))
    },
    mojozTresqlMetadata :=
      TresqlMetadata(
        mojozTableMetadata.value.tableDefs,
        mojozTypeDefs.value,
        mojozTresqlMacrosClass.value.orNull,
        mojozResourceLoader.value,
      ),
    mojozQuerease :=
      new Querease {
        override lazy val yamlMetadata        = mojozRawViewMetadata.value.toVector
        override lazy val metadataConventions = mojozMdConventions.value
        override lazy val typeDefs            = mojozTypeDefs.value
        override lazy val tableMetadata       = mojozTableMetadata.value
        override lazy val macrosClass         = mojozTresqlMacrosClass.value.orNull
        override lazy val tresqlMetadata      = mojozTresqlMetadata.value
        override lazy val joinsParser         = mojozJoinsParser.value
        override lazy val viewDefLoader       = mojozViewMetadataLoader.value
      },
    mojozAllCompilerMetadataFiles := {
      Seq(
        (mojozMdConventionsResources.value ** "*-patterns.txt").get,
        mojozCustomTypesFile.value.toSeq,
        mojozTableMetadataFiles.value.map(_._1),
        ((Compile / unmanagedResources).value ** "tresql-function-signatures*.txt").get,
        ((Compile / unmanagedResources).value ** "tresql-macros.txt").get,
        ((Compile / unmanagedResources).value ** "tresql-scala-macro.properties").get,
      ).flatMap(x => x)
    },
    mojozAllSourceFiles :=
      mojozAllCompilerMetadataFiles.value ++
      mojozViewMetadataFiles.value.map(_._1),
    mojozCompilerCacheFolder :=
      (Compile / resourceManaged).value,
    mojozCompileViews := {
      var compilerCacheFileNames: Seq[String] = Nil
      def compileViews(previouslyCompiledQueries: Set[String] = Set.empty): Set[String] = {
        val (compiledViews, caches) =
          mojozQuerease.value.compileAllQueries(
            previouslyCompiledQueries,
            mojozShowFailedViewQuery.value,
            streams.value.log.info(_),
          )
        compilerCacheFileNames =
          caches.map { case (name, cache) =>
            val file = mojozCompilerCacheFolder.value / name
            IO.write(file, cache)
            name
          }.toSeq
        compiledViews
      }
      import sbt.util.CacheImplicits._
      val allSourceFiles = mojozAllSourceFiles.value
      val compilerMetadataFiles = mojozAllCompilerMetadataFiles.value

      lazy val compiledQueriesCacheStore  = streams.value.cacheStoreFactory make "mojoz-compiled-queries"
      lazy val cachedCompileViewsQ        = Tracked.lastOutput[Boolean, Set[String]](compiledQueriesCacheStore) {
        case (_, None) /* no previous output */        => compileViews()
        case (true, _) /* compiler metadata changed */ => compileViews()
        case (_, Some(previouslyCompiledQueries))      => compileViews(previouslyCompiledQueries)
      }

      lazy val compilerMetadataCacheStore = streams.value.cacheStoreFactory make "mojoz-compiler-metadata-file-hashes"
      lazy val cachedCompileViewsM        = Tracked.inputChanged[Seq[HashFileInfo], Any](compilerMetadataCacheStore) {
        case (isCompilerMetadataChanged: Boolean, _)   => cachedCompileViewsQ(isCompilerMetadataChanged)
      }

      lazy val allSourcesCacheStore       = streams.value.cacheStoreFactory make "mojoz-all-source-file-hashes"
      lazy val cachedCompileViews         = Tracked.inputChanged[Seq[HashFileInfo], Any](allSourcesCacheStore) {
        case (true, _)  => cachedCompileViewsM(compilerMetadataFiles.map(FileInfo.hash(_)))
        case (false, _) =>
      }

      if (mojozShouldCompileViews.value)
        cachedCompileViews(allSourceFiles.map(FileInfo.hash(_)))

      lazy val cacheFilenamesCacheStore   = streams.value.cacheStoreFactory make "mojoz-compiler-cache-file-names"
      lazy val cachedCacheFileNames       = Tracked.lastOutput[Set[String], Set[String]](compiledQueriesCacheStore) {
        case (fileNames, previousFileNamesOpt) => fileNames ++ previousFileNamesOpt.getOrElse(Set.empty)
      }

      cachedCacheFileNames(compilerCacheFileNames.toSet)
        .toSeq.sorted
        .map(mojozCompilerCacheFolder.value / _)
        .filter(_.exists)
    },

    mojozScalaGenerator := new org.mojoz.querease.ScalaDtoGenerator(mojozQuerease.value),
    mojozGenerateDtosScalaFileName := "Dtos.scala",

    mojozGenerateDtosMappingsScala := {
      val tableMd = mojozTableMetadata.value
      val viewDefs = mojozGenerateDtosMappingsViewMetadata.value
      val classBuilder = mojozScalaGenerator.value
      val tableNames = tableMd.tableDefs.map(_.name)
      // FIXME do not search for distinct table names, change mapping structure to support multi-db tables instead?
      val distinctTableNames  = tableNames.distinct.sorted
      def scalaClassNameString(name: String) = classBuilder.scalaNameString(classBuilder.scalaClassName(name))
      val mapping = s"""
        |object Tables {
        |  ${distinctTableNames.map(t => s"class ${scalaClassNameString(t)} {}").mkString("\n  ")}
        |}
        |object DtoMapping {
        |  val viewNameToClass = Map[String, Class[_ <: Dto]](
        |    ${viewDefs.map(v => s""""${v.name}" -> classOf[${scalaClassNameString(v.name)}]""").mkString(",\n    ")}
        |  )
        |  val viewClassToTableClass = Map[Class[_ <: Dto], Class[_]](
        |    ${viewDefs.filter(_.table != null).map(v =>
                s"classOf[${scalaClassNameString(v.name)}] -> classOf[Tables.${scalaClassNameString(v.table)}]"
               ).mkString(",\n    ")
             }
        |  )
        |}
        |""".stripMargin.trim
      mapping
    },

    mojozGenerateDtosScala := {
      val file = (Compile / sourceManaged).value / mojozGenerateDtosScalaFileName.value
      val tableMd = mojozTableMetadata.value
      val viewDefs = mojozGenerateDtosViewMetadata.value
      val allViewDefsMap = mojozViewMetadata.value.map(v => v.name -> v).toMap

      val classBuilder = mojozScalaGenerator.value

      val mapping = mojozGenerateDtosMappingsScala.value
      val contents = classBuilder.generateScalaSource(
        List("package "+mojozDtosPackage.value, "") ++
          mojozDtosImports.value.map("import "+_)   ++
          List(""),
        viewDefs,
        List("", mapping),
        allViewDefsMap,
      )
      IO.write(file, contents)
      file
    },

    mojozSourceGenerators := {
      mojozCompileViews.value.filter(_ => false) ++ // XXX Compile views at this point (no source generation here)
      Seq(mojozGenerateDtosScala.value)
    },

    mojozUninheritableExtras := collection.immutable.Seq("api"),

    Compile / resourceGenerators += mojozResourceGenerators.taskValue,

    Compile / sourceGenerators   += mojozSourceGenerators.taskValue,

    Compile / copyResources := {
      val taskStreams = streams.value
      val classDir = (Compile / classDirectory).value
      val cacheStore = streams.value.cacheStoreFactory make "copy-resources"
      val mappings = mojozMetadataFilesForResources.value.map(f => (f._1, classDir / f._2))

      taskStreams.log.debug("result" + mappings.mkString("\n\t","\n\t",""))
      Sync.sync(cacheStore)( mappings )
      (Compile / copyResources).value ++ mappings
    },

    mojozGeneratedFiles := {
      val tableMd = mojozTableMetadata.value
      val viewDefs = mojozGenerateDtosViewMetadata.value
      val classBuilder = mojozScalaGenerator.value
      val packagePath = mojozDtosPackage.value.replaceAllLiterally(".", "/")
      def classFileName(name: String) =
        // TODO classFileNameFromName
        classBuilder.scalaClassName(name)
          .replace(".", "$u002E")
      def classFilePathFromName(name: String) = packagePath + "/" + name + ".class"
      mojozResourceGenerators.value.map(_.getAbsolutePath) ++
        mojozSourceGenerators.value.map(_.getAbsolutePath) ++
        Seq("Tables$", "Tables", "DtoMapping$", "DtoMapping").map(classFilePathFromName) ++
        viewDefs.map(v => classFilePathFromName(classFileName(v.name))) ++
        tableMd.tableDefs.map(t => classFilePathFromName("Tables$" + classFileName(t.name)))
    },

    // sbt tilde must watch changes in yaml files
    watchSources := {
      watchSources.value ++
      mojozTableMetadataFolders.value.map(WatchSource(_)) ++
      mojozViewMetadataFolders.value.map(WatchSource(_)) ++
      mojozAllSourceFiles.value.groupBy(_.getParentFile.getAbsolutePath).map {
        case (path, files) =>
          val names = files.map(_.getName).toSet
          WatchSource(new File(path), includeFilter = new SimpleFilter(names.contains), excludeFilter = NothingFilter)
      }
    }
  )
}

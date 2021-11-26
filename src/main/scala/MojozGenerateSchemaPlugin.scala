package org.mojoz

import org.mojoz.metadata.out.SqlGenerator
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin


object MojozGenerateSchemaPlugin extends AutoPlugin {
  object autoImport {
    val mojozSchemaSqlDirectory     = settingKey[File]          ("Directory for generated schema files, can be used by mojozSchemaSqlFiles")
    val mojozSchemaSqlDbNames       = taskKey[Seq[String]]      ("Database names for schema generation, may contain null for default database")
    val mojozSchemaSqlFiles         = taskKey[Seq[File]]        ("Files where to write schema sql, corresponding to mojozSchemaSqlDbNames")
    val mojozSchemaSqlGenerators    = taskKey[Seq[SqlGenerator]]("SqlGenerators (per db) used to generate schema, see org.mojoz.metadata.out.SqlGenerator for available generators")
    val mojozGenerateSchemaSqlFiles = taskKey[Seq[File]]        ("Generates schema sql")
    val mojozPrintSchemaSql         = inputKey[Unit]            ("Prints schema sql string for (space-delimited) table name(s)")

    // Lists schema sql files generated by projects current project depends on
    val mojozDependencyGeneratedSqls = Def.taskDyn {
      val dependencies = thisProject.value.dependencies.map(_.project)
      val allSettings = Project.extract(state.value).structure.settings
      val projectsWithSchemaSetting = allSettings.filter(_.key.key == mojozGenerateSchemaSqlFiles.key)
        .flatMap(_.key.scope.project.toOption.map(Seq(_)).getOrElse(Seq())).toSet
      val depsWithSetting = dependencies.filter(projectsWithSchemaSetting)
      mojozGenerateSchemaSqlFiles.all(ScopeFilter(inProjects(depsWithSetting: _*)))
    }
  }

  import autoImport._
  import MojozTableMetadataPlugin.autoImport._
  override def trigger = noTrigger
  override def requires = JvmPlugin && MojozTableMetadataPlugin

  override val projectSettings = Seq(
    mojozSchemaSqlDirectory     := (Compile / resourceManaged).value,
    mojozSchemaSqlDbNames       := mojozDbNames.value,
    mojozSchemaSqlFiles         := mojozSchemaSqlDbNames.value.map { db =>
      mojozSchemaSqlDirectory.value / s"db-schema${Option(db).map("-" + _) getOrElse ""}.sql"
    },
    mojozSchemaSqlGenerators := {
      val typeDefs = mojozTypeDefs.value
      mojozSchemaSqlDbNames.value.map { db => org.mojoz.metadata.out.SqlGenerator.postgresql(typeDefs = typeDefs) }
    },

    mojozGenerateSchemaSqlFiles := {
      val sqlFile = mojozDependencyGeneratedSqls.value
      val tableMd = mojozTableMetadata.value
      val dbToTableDefs = tableMd.tableDefs.groupBy(_.db)
      (mojozSchemaSqlDbNames.value, mojozSchemaSqlFiles.value, mojozSchemaSqlGenerators.value).zipped.toList map {
        case (db, schemaFile, sqlGenerator) =>
          val allTables = dbToTableDefs(db).map(_.name).sorted
          IO.write(schemaFile, sqlGenerator.schema(allTables.map(tableMd.tableDef(_, db))))
          schemaFile
      }
    },

    mojozPrintSchemaSql := {
      import sbt.complete.DefaultParsers._
      import org.mojoz.metadata.out.SqlGenerator
      // get the result of parsing
      val args: Seq[String] = spaceDelimited("<arg>").parsed
      val tableMd           = mojozTableMetadata.value
      val allTableNames     = tableMd.tableDefs.map(_.name).distinct.sorted
      val allTableNamesSet  = allTableNames.toSet
      if (args.isEmpty) {
        println("Please specify * for all tables or (space delimited) table name(s), one or more of:")
        println(allTableNames.mkString(" "))
      } else {
        val tableNames =
          if (args.size == 1 && args(0) == "*") allTableNames
          else args
        val missingNames = tableNames.filterNot(allTableNamesSet.contains).distinct.sorted
        if (missingNames.nonEmpty)
          println("Tables not found: " + missingNames.mkString(", "))
        else
          (mojozSchemaSqlDbNames.value zip mojozSchemaSqlGenerators.value) foreach {
            case (db, sqlGenerator) =>
              val tableDefs = tableNames.toList.map(tableMd.tableDefOption(_, db)).filter(_.isDefined).map(_.get)
              if (tableDefs.nonEmpty) {
                if (db != null)
                  println(s"\n----- $db -----\n")
                println(sqlGenerator.schema(tableDefs))
              }
          }
      }
    },

    // not exactly source generation, but we want schema to be generated during compilation
    // to disable this 'effect' add to your build - mojozGenerateSchemaSqlFiles := { null }
    Compile / sourceGenerators += mojozGenerateSchemaSqlFiles.map(_ => Seq()).taskValue
  )
}

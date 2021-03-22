package com.pennsieve.service.utilities

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationInfo

class MigrationRunner(
  jdbcUrl: String,
  postgresUser: String,
  postgresPassword: String,
  schema: Option[String] = None,
  scriptLocation: Option[String] = None,
  metadataTable: Option[String] = None
) {
  val migrator = new Flyway()

  migrator.setDataSource(jdbcUrl, postgresUser, postgresPassword)
  schema.foreach(s => migrator.setSchemas(s))
  scriptLocation.foreach(l => migrator.setLocations(l))
  metadataTable.foreach(t => migrator.setTable(t))

  def run(): (Int, Seq[MigrationInfo]) = {
    val count = migrator.migrate
    val migrationInfo = migrator.info.applied.seq.takeRight(count)

    (count, migrationInfo)
  }
}

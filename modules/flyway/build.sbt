

// Database Migrations:
// run with "sbt flywayMigrate"
// http://flywaydb.org/getstarted/firststeps/sbt.html

//$ export DB_DEFAULT_URL="jdbc:h2:/tmp/example.db"
//$ export DB_DEFAULT_USER="sa"
//$ export DB_DEFAULT_PASSWORD=""

libraryDependencies += "org.flywaydb" % "flyway-core" % "4.2.0"

lazy val databaseUrl = sys.env.getOrElse("DB_DEFAULT_URL", "jdbc:postgresql://localhost/isolated_slick_example")
lazy val databaseUser = sys.env.getOrElse("DB_DEFAULT_USER", "")
lazy val databasePassword = sys.env.getOrElse("DB_DEFAULT_PASSWORD", "")

flywayLocations := Seq("classpath:db/migration")

flywayUrl := databaseUrl
flywayUser := databaseUser
flywayPassword := databasePassword

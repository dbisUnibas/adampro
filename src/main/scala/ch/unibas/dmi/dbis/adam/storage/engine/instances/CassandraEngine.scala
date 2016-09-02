package ch.unibas.dmi.dbis.adam.storage.engine.instances

import java.net.InetAddress

import ch.unibas.dmi.dbis.adam.datatypes.FieldTypes._
import ch.unibas.dmi.dbis.adam.datatypes.feature.{FeatureVectorWrapper, FeatureVectorWrapperUDT}
import ch.unibas.dmi.dbis.adam.entity.AttributeDefinition
import ch.unibas.dmi.dbis.adam.entity.Entity.EntityName
import ch.unibas.dmi.dbis.adam.exception.GeneralAdamException
import ch.unibas.dmi.dbis.adam.main.AdamContext
import ch.unibas.dmi.dbis.adam.storage.engine.KeyValueEngine
import ch.unibas.dmi.dbis.adam.utils.Logging
import com.datastax.driver.core.Session
import com.datastax.spark.connector.cql.{CassandraConnector, PasswordAuthConf}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.ArrayType
import org.apache.spark.sql.{DataFrame, SaveMode}

import scala.util.{Failure, Success, Try}

/**
  * ADAMpro
  *
  * Ivan Giangreco
  * September 2016
  */
class CassandraEngine(private val url: String, private val port: Int, private val user: String, private val password: String, protected val keyspace: String = "public") extends KeyValueEngine with Logging with Serializable {
  private val conn = CassandraConnector(hosts = Set(InetAddress.getByName(url)), port = port, authConf = PasswordAuthConf(user, password))

  def init(): Unit = {
    val keyspaceRes = conn.withClusterDo(_.getMetadata).getKeyspace(keyspace)

    if (keyspaceRes == null) {
      conn.withSessionDo { session =>
        createKeyspace(session)
      }
    }
  }

  init()

  private def createKeyspaceCql(keyspacename: String = keyspace) =
    s"""
       |CREATE KEYSPACE IF NOT EXISTS $keyspacename
       |WITH REPLICATION = { 'class': 'SimpleStrategy', 'replication_factor': 1 }
       |AND durable_writes = false
       |""".stripMargin

  private def dropKeyspaceCql(keyspacename: String) =
    s"""
       |DROP KEYSPACE IF EXISTS $keyspacename
       |""".stripMargin

  private def createTableCql(tablename: EntityName, schema: String) =
    s"""
       |CREATE TABLE IF NOT EXISTS $tablename
       | ($schema)
       |""".stripMargin

  private def dropTableCql(tablename: EntityName) =
    s"""
       |DROP TABLE IF EXISTS $tablename
       |""".stripMargin


  private def createKeyspace(session: Session, name: String = keyspace): Unit = {
    session.execute(dropKeyspaceCql(name))
    session.execute(createKeyspaceCql(name))
  }

  /**
    * Create the entity in the key-value store.
    *
    * @param bucketname name of bucket to store feature to
    * @param attributes attributes of the entity
    * @return true on success
    */
  def create(bucketname: String, attributes: Seq[AttributeDefinition])(implicit ac: AdamContext): Try[Option[String]] = {
    try {
      val attributeString = attributes.map(attribute => {
        val name = attribute.name
        val cqlType = getCQLType(attribute.fieldtype)
        val pk = if (attribute.pk) {
          "PRIMARY KEY"
        } else {
          ""
        }

        s"$name $cqlType $pk".trim
      }).mkString(", ")

      conn.withSessionDo { session =>
        session.execute("use " + keyspace)
        session.execute(createTableCql(bucketname, attributeString))
      }

      Success(Some(bucketname))
    } catch {
      case e: Exception =>
        log.error("fatal error when creating bucket in cassandra", e)
        Failure(e)
    }
  }

  /**
    *
    * @param fieldtype
    * @return
    */
  private def getCQLType(fieldtype: FieldType): String = fieldtype match {
    case INTTYPE => "INT"
    case AUTOTYPE => "BIGINT"
    case LONGTYPE => "BIGINT"
    case FLOATTYPE => "FLOAT"
    case DOUBLETYPE => "DOUBLE"
    case STRINGTYPE => "TEXT"
    case BOOLEANTYPE => "BOOLEAN"
    case FEATURETYPE => "LIST<FLOAT>"
    case _ => throw new GeneralAdamException("field type " + fieldtype.name + " is not supported in cassandra handler")
  }

  /**
    *
    * @param bucketname name of bucket to check if bucket exists
    * @return
    */
  override def exists(bucketname: String)(implicit ac: AdamContext): Try[Boolean] = {
    try {
      var exists = false
      conn.withSessionDo { session =>
        val tableMeta = session.getCluster.getMetadata.getKeyspace(keyspace).getTable(bucketname)

        if (tableMeta != null) {
          exists = true
        }
      }
      Success(exists)
    } catch {
      case e: Exception =>
        log.error("fatal error when checking for existence in cassandra", e)
        Failure(e)
    }
  }

  /**
    * Read entity from key-value store.
    *
    * @param bucketname name of bucket to read features from
    * @return
    */
  override def read(bucketname: String)(implicit ac: AdamContext): Try[DataFrame] = {
    try {
      import org.apache.spark.sql.functions.udf
      val castToFeature = udf((c: Seq[Float]) => {
        new FeatureVectorWrapper(c)
      })

      val df = ac.sqlContext.read
        .format("org.apache.spark.sql.cassandra")
        .options(Map("table" -> bucketname, "keyspace" -> keyspace))
        .load()

      var data = df

      df.schema.fields.filter(_.dataType.isInstanceOf[ArrayType]).foreach { field =>
        data = data.withColumn(field.name, castToFeature(col(field.name)))
      }

      Success(data)
    } catch {
      case e: Exception =>
        log.error("fatal error when reading from cassandra", e)
        Failure(e)
    }
  }

  /**
    * Write entity to the key-value store.
    *
    * @param bucketname name of bucket to write features to
    * @param df         data
    * @param mode       save mode (append, overwrite, ...)
    * @return true on success
    */
  override def write(bucketname: String, df: DataFrame, mode: SaveMode)(implicit ac: AdamContext): Try[Void] = {
    try {
      if (mode != SaveMode.Append) {
        throw new UnsupportedOperationException("only appending is supported")
      }


      var data = df
      import org.apache.spark.sql.functions.{col, udf}
      val castToSeq = udf((c: FeatureVectorWrapper) => {
        c.toSeq
      })
      df.schema.fields.filter(_.dataType.isInstanceOf[FeatureVectorWrapperUDT]).foreach { field =>
        data = data.withColumn(field.name, castToSeq(col(field.name)))
      }

      data.write
        .format("org.apache.spark.sql.cassandra")
        .options(Map( "table" -> bucketname, "keyspace" -> keyspace))
        .save()

      Success(null)
    } catch {
      case e: Exception =>
        log.error("fatal error when writing to cassandra", e)
        Failure(e)
    }
  }

  /**
    * Drop the entity from the key-value store.
    *
    * @param bucketname name of bucket to be dropped
    * @return true on success
    */
  override def drop(bucketname: String)(implicit ac: AdamContext): Try[Void] = {
    try {
      conn.withSessionDo { session =>
        session.execute("use " + keyspace)
        session.execute(dropTableCql(bucketname))
      }
      Success(null)
    } catch {
      case e: Exception =>
        log.error("fatal error when dropping from in cassandra", e)
        Failure(e)
    }
  }
}
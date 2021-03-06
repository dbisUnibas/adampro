package org.vitrivr.adampro.shared.catalog

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import com.mchange.v2.c3p0.ComboPooledDataSource
import org.vitrivr.adampro.data.entity.Entity.EntityName
import org.vitrivr.adampro.process.SharedComponentContext
import org.vitrivr.adampro.utils.exception.GeneralAdamException
import org.vitrivr.adampro.query.ast.generic.QueryExpression
import org.vitrivr.adampro.query.ast.internal.IndexScanExpression
import org.vitrivr.adampro.query.query.RankingQuery
import org.vitrivr.adampro.shared.catalog.catalogs._
import org.vitrivr.adampro.utils.Logging
import slick.dbio.NoStream
import slick.driver.H2Driver.api._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * ADAMpro
  *
  * Ivan Giangreco
  * September 2016
  */
class LogManager(internalsPath : String) extends Logging {
  private val MAX_WAITING_TIME: Duration = 100.seconds

  private val ds = new ComboPooledDataSource()
  ds.setDriverClass("org.h2.Driver")
  ds.setJdbcUrl("jdbc:h2:" + internalsPath + "/ap_catalog" + "")

  private val DB = Database.forDataSource(ds)

  private val _measurements = TableQuery[MeasurementLog]
  private val _queries = TableQuery[QueryLog]

  private[catalog] val LOGS = Seq(
    _queries, _measurements
  )


  /**
    * Initializes the catalog. Method is called at the beginning (see below).
    */
  private def init() {
    val connection = Database.forURL("jdbc:h2:" + internalsPath + "/ap_logs")

    try {
      val actions = new ListBuffer[DBIOAction[_, NoStream, _]]()

      val schema = CatalogManager.SCHEMA

      val schemaExists = Await.result(connection.run(sql"""SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '#$schema'""".as[Int]), MAX_WAITING_TIME).headOption

      if (schemaExists.isEmpty || schemaExists.get == 0) {
        //schema might not exist yet
        Await.result(connection.run(DBIO.seq(sqlu"""CREATE SCHEMA IF NOT EXISTS #$schema;""").transactionally), MAX_WAITING_TIME)
      }

      val tables = Await.result(connection.run(sql"""SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '#$schema'""".as[String]), MAX_WAITING_TIME).toSeq

      LOGS.foreach { catalog =>
        if (!tables.contains(catalog.baseTableRow.tableName)) {
          actions += catalog.schema.create
        } else {
        }
      }

      Await.result(connection.run(DBIO.seq(actions.toArray: _*).transactionally), MAX_WAITING_TIME)
    } catch {
      case e: Exception =>
        log.error("fatal error when creating catalogs", e)
        System.exit(1)
        throw new GeneralAdamException("fatal error when creating catalogs")
    } finally {
      connection.close()
    }
  }

  init()

  /**
    * Executes operation.
    *
    * @param desc description to display in log
    * @param op   operation to perform
    * @return
    */
  private def execute[T](desc: String)(op: => T): Try[T] = {
    try {
      log.trace("performed catalog operation: " + desc)
      val res = op
      Success(res)
    } catch {
      case e: Exception =>
        log.error("error in catalog operation: " + desc, e)
        Failure(e)
    }
  }

  /**
    *
    * @param qexpr
    * @return
    */
  def addQuery(qexpr: QueryExpression): Try[Void] = {
    execute("add query") {
      if (qexpr.children.nonEmpty) {
        qexpr.children.foreach { child =>
          addQuery(child)
        }
      }

      if (qexpr.isInstanceOf[IndexScanExpression]) {
        val ise = qexpr.asInstanceOf[IndexScanExpression]
        val entityname = ise.index.entityname
        val nnq = ise.nnq

        addQuery(entityname, nnq)
      }

      null
    }
  }

  /**
    *
    * @param entityname
    * @param nnq
    * @return
    */
  def addQuery(entityname: EntityName, nnq: RankingQuery): Try[String] = {
    execute("add query") {
      val key = java.util.UUID.randomUUID.toString
      val sernnq = serialize(nnq)

      val query = _queries.+=(key, entityname.toString, nnq.attribute, sernnq)
      DB.run(query)

      key
    }
  }


  /**
    * Gets measurements for given key.
    *
    * @param entityname
    * @param attribute
    * @return
    */
  def getQueries(entityname: EntityName, attribute: String): Try[Seq[RankingQuery]] = {
    execute("get measurement") {
      val query = _queries.filter(_.entityname === entityname.toString).filter(_.attribute === attribute).map(_.query).result
      Await.result(DB.run(query), MAX_WAITING_TIME).map(deserialize[RankingQuery](_))
    }
  }

  /**
    *
    * @param o
    * @tparam T
    * @return
    */
  private def serialize[T](o: T): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bos)
    oos.writeObject(o)
    oos.close()
    bos.toByteArray
  }

  /**
    *
    * @param bytes
    * @tparam T
    * @return
    */
  private def deserialize[T](bytes: Array[Byte]): T = {
    val bis = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(bis)
    ois.readObject.asInstanceOf[T]
  }


  /**
    * Adds a measurement to the catalog
    *
    * @param key
    * @param source
    * @param nresults
    * @param time
    * @return
    */
  def addMeasurement(key: String, source: String, nresults: Long, time: Long): Try[Void] = {
    //TODO: permanently log query times (useful?)
    execute("add measurement") {
      val query = _measurements.+=(key, source, nresults, time)
      DB.run(query)

      null
    }
  }

  /**
    * Drops measurements for given key.
    *
    * @param key
    * @return
    */
  def dropMeasurements(key: String): Try[Void] = {
    execute("drop measurements") {
      Await.result(DB.run(_measurements.filter(_.key === key).delete), MAX_WAITING_TIME)
      null
    }
  }

  /**
    * Drops measurements for given key.
    *
    * @return
    */
  def dropAllMeasurements(): Try[Void] = {
    execute("drop all measurements") {
      Await.result(DB.run(_measurements.delete), MAX_WAITING_TIME)
      null
    }
  }
}


object LogManager {
  /**
    * Create log manager and fill it
    * @return
    */
  def build()(implicit ac: SharedComponentContext): LogManager = new LogManager(ac.config.internalsPath)
}
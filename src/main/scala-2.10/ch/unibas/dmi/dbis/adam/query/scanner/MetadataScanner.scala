package ch.unibas.dmi.dbis.adam.query.scanner

import ch.unibas.dmi.dbis.adam.config.FieldNames
import ch.unibas.dmi.dbis.adam.entity.Entity
import ch.unibas.dmi.dbis.adam.main.SparkStartup
import ch.unibas.dmi.dbis.adam.query.query.BooleanQuery
import org.apache.log4j.Logger
import org.apache.spark.sql.DataFrame

/**
  * adamtwo
  *
  * Scans the metadata.
  *
  * Ivan Giangreco
  * November 2015
  */
object MetadataScanner {
  val log = Logger.getLogger(getClass.getName)

  /**
    * Performs a Boolean query on the metadata.
    *
    * @param entity
    * @param query
    * @return the ids of the tuples which match the query
    */
  def ids(entity: Entity, query: Option[BooleanQuery]): Option[DataFrame] = {
    val res = apply(entity, query)

    if(res.isDefined){
      Option(res.get.select(FieldNames.idColumnName))
    } else {
      None
    }
  }

  /**
    * Performs a Boolean query on the metadata.
    *
    * @param entity
    * @param query
    * @return the ids of the tuples which match the query
    */
  def apply(entity: Entity, query: Option[BooleanQuery]): Option[DataFrame] = {
    if(query.isDefined){
      retrieve(entity, query.get)
    } else {
      retrieveAll(entity)
    }
  }


  /**
    * Performs a Boolean query on the metadata.
    *
    * @param entity
    * @param query
    * @return the ids of the tuples which match the query
    */
  private def retrieve(entity: Entity, query: BooleanQuery): Option[DataFrame] = {
    if (entity.hasMetadata && query.where.isDefined) {
      var df = entity.getMetadata.get

      if (query.join.isDefined) {
        log.debug("join tables to results")
        val joins = query.join.get

        for (i <- (0 until joins.length)) {
          val join = joins(i)
          log.debug("join " + join._1 + " on " + join._2.mkString("(", ", ", ")"))
          val newDF = SparkStartup.metadataStorage.read(join._1)
          df = df.join(newDF, join._2)
        }
      }

      val where = query.buildWhereClause()
      log.debug("query metadata using where clause: " + where)
      Option(df.filter(where))
    } else {
      log.warn("asked for metadata, but entity " + entity + " has no metadata available")
      None
    }
  }


  /**
    * Returns all metadata tuples from the given entity.
    *
    * @param entity
    * @return
    */
  private def retrieveAll(entity: Entity): Option[DataFrame] = {
    if (entity.hasMetadata) {
      val df = entity.getMetadata.get
      Option(df)
    } else {
      log.warn("asked for metadata, but entity " + entity + " has no metadata available")
      None
    }
  }

  /**
    * Performs a Boolean query on the metadata where the ID only is compared.
    *
    * @param entity
    * @param filter tuple ids to filter on
    * @return
    */
  def apply(entity: Entity, filter: DataFrame): Option[DataFrame] = {
    if (entity.hasMetadata) {
      val df = entity.getMetadata.get
      Option(filter.join(df, FieldNames.idColumnName))
    } else {
      log.warn("asked for metadata, but entity " + entity + " has no metadata available")
      None
    }
  }
}

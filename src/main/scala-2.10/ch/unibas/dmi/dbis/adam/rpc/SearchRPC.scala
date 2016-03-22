package ch.unibas.dmi.dbis.adam.rpc

import java.util.concurrent.TimeUnit

import ch.unibas.dmi.dbis.adam.api.QueryOp
import ch.unibas.dmi.dbis.adam.config.FieldNames
import ch.unibas.dmi.dbis.adam.datatypes.feature.Feature._
import ch.unibas.dmi.dbis.adam.http.grpc.adam._
import ch.unibas.dmi.dbis.adam.index.structures.IndexTypes
import ch.unibas.dmi.dbis.adam.query.distance.NormBasedDistanceFunction
import ch.unibas.dmi.dbis.adam.query.handler.QueryHints
import ch.unibas.dmi.dbis.adam.query.progressive.ProgressiveQueryStatus
import ch.unibas.dmi.dbis.adam.query.query.{BooleanQuery, NearestNeighbourQuery}
import io.grpc.stub.StreamObserver
import org.apache.log4j.Logger
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/**
  * adamtwo
  *
  * Ivan Giangreco
  * March 2016
  */
class SearchRPC extends AdamSearchGrpc.AdamSearch {
  val log = Logger.getLogger(getClass.getName)

  /**
    *
    * @param option
    * @return
    */
  private def prepareNearestNeighbourQuery(option: Option[NearestNeighbourQueryMessage]): NearestNeighbourQuery = {
    if (option.isEmpty) {
      throw new Exception("No kNN query specified.")
    }

    val nnq = option.get

    NearestNeighbourQuery(nnq.query, NormBasedDistanceFunction(nnq.norm), nnq.k, nnq.indexOnly, nnq.options)
  }

  /**
    *
    * @param option
    * @return
    */
  private def prepareBooleanQuery(option: Option[BooleanQueryMessage]): Option[BooleanQuery] = {
    if (option.isDefined) {
      val bq = option.get
      Option(BooleanQuery(bq.where.map(bqm => (bqm.field, bqm.value)), Option(bq.joins.map(x => (x.table, x.columns)))))
    } else {
      None
    }
  }


  /**
    *
    * @param df
    * @return
    */
  private def prepareResults(df: DataFrame): QueryResponseListMessage = {
    def toJSON(rowSchema: StructType)(row: Row): String = {
      //TODO: switch to JSON
      row.toString()
    }

    val responseMsgs = df.collect().map(row => QueryResponseMessage(
      row.getAs[Long](FieldNames.idColumnName),
      row.getAs[Float](FieldNames.distanceColumnName),
      toJSON(df.schema)(row)
    ))

    QueryResponseListMessage(responseMsgs)
  }

  /**
    *
    * @param request
    * @return
    */
  override def doStandardQuery(request: SimpleQueryMessage): Future[QueryResponseListMessage] = {
    log.debug("rpc call for standard query operation")

    try {
      val entity = request.entity
      val hint = QueryHints.withName(request.hint)
      val nnq = prepareNearestNeighbourQuery(request.nnq)
      val bq = prepareBooleanQuery(request.bq)
      val meta = request.withMetadata

      val results = QueryOp(entity, hint, nnq, bq, meta)
      Future.successful(prepareResults(results))
    } catch {
      case e: Exception => Future.failed(e)
    }
  }


  /**
    *
    * @param request
    * @return
    */
  override def doSequentialQuery(request: SimpleSequentialQueryMessage): Future[QueryResponseListMessage] = {
    log.debug("rpc call for sequential query operation")

    try {
      val entity = request.entity

      val nnq = prepareNearestNeighbourQuery(request.nnq)
      val bq = prepareBooleanQuery(request.bq)
      val meta = request.withMetadata

      val results = QueryOp.sequential(entity, nnq, bq, meta)
      Future.successful(prepareResults(results))
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  /**
    *
    * @param request
    * @return
    */
  override def doIndexQuery(request: SimpleIndexQueryMessage): Future[QueryResponseListMessage] = {
    log.debug("rpc call for index query operation")

    try {
      val entity = request.entity
      val indextype = IndexTypes.withIndextype(request.indextype)
      if (indextype.isEmpty) {
        throw new Exception("No existing index type specified.")
      }
      val nnq = prepareNearestNeighbourQuery(request.nnq)
      val bq = prepareBooleanQuery(request.bq)
      val meta = request.withMetadata

      val results = QueryOp.index(entity, indextype.get, nnq, bq, meta)
      Future.successful(prepareResults(results))
    } catch {
      case e: Exception => Future.failed(e)
    }
  }


  /**
    *
    * @param request
    * @return
    */
  override def doSpecifiedIndexQuery(request: SimpleSpecifiedIndexQueryMessage): Future[QueryResponseListMessage] = {
    log.debug("rpc call for index query operation")

    try {
      val index = request.index
      val nnq = prepareNearestNeighbourQuery(request.nnq)
      val bq = prepareBooleanQuery(request.bq)
      val meta = request.withMetadata

      val results = QueryOp.index(index, nnq, bq, meta)
      Future.successful(prepareResults(results))
    } catch {
      case e: Exception => Future.failed(e)
    }
  }


  /**
    *
    * @param request
    * @param responseObserver
    */
  override def doProgressiveQuery(request: SimpleQueryMessage, responseObserver: StreamObserver[QueryResponseInfoMessage]): Unit = {
    log.debug("rpc call for progressive query operation")

    try {
      val entity = request.entity
      val nnq = prepareNearestNeighbourQuery(request.nnq)
      val bq = prepareBooleanQuery(request.bq)
      val meta = request.withMetadata

      val onComplete =
        (status: ProgressiveQueryStatus.Value, results: DataFrame, confidence: Float, deliverer : String, info: Map[String, String]) => ({
          responseObserver.onNext(QueryResponseInfoMessage(confidence, IndexTypes.withName(info.getOrElse("type", "")).get.indextype, Option(prepareResults(results))))
        })


      QueryOp.progressive(entity, nnq, bq, onComplete, meta)
    } catch {
      case e: Exception => Future.failed(e)
    }
  }


  /**
    *
    * @param request
    * @return
    */
  override def doTimedProgressiveQuery(request: TimedQueryMessage): Future[QueryResponseInfoMessage] = {
    log.debug("rpc call for timed progressive query operation")

    try {
      val entity = request.entity
      val time = request.time
      val nnq = prepareNearestNeighbourQuery(request.nnq)
      val bq = prepareBooleanQuery(request.bq)
      val meta = request.withMetadata

      val (results, confidence, deliverer) = QueryOp.timedProgressive(entity, nnq, bq, Duration(time, TimeUnit.MILLISECONDS), meta)
      Future.successful(QueryResponseInfoMessage(confidence, IndexTypes.withName(deliverer).get.indextype, Option(prepareResults(results))))
    } catch {
      case e: Exception => Future.failed(e)
    }
  }
}

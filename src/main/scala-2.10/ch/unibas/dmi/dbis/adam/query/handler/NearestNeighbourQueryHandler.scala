package ch.unibas.dmi.dbis.adam.query.handler

import java.util.concurrent.TimeUnit

import ch.unibas.dmi.dbis.adam.entity.Entity
import ch.unibas.dmi.dbis.adam.entity.Entity.EntityName
import ch.unibas.dmi.dbis.adam.entity.Tuple.TupleID
import ch.unibas.dmi.dbis.adam.index.Index
import ch.unibas.dmi.dbis.adam.index.Index.IndexName
import ch.unibas.dmi.dbis.adam.query.Result
import ch.unibas.dmi.dbis.adam.query.progressive._
import ch.unibas.dmi.dbis.adam.query.query.NearestNeighbourQuery
import ch.unibas.dmi.dbis.adam.query.scanner.{FeatureScanner, IndexScanner}
import ch.unibas.dmi.dbis.adam.storage.engine.CatalogOperator
import org.apache.spark.Logging

import scala.collection.immutable.HashSet
import scala.collection.mutable.{Map => mMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration.Duration


/**
 * adamtwo
 *
 * Ivan Giangreco
 * August 2015
 */
object NearestNeighbourQueryHandler extends Logging {

  /**
   *
   * @param entityname
   * @param filter
   * @return
   */
  def sequentialQuery(entityname: EntityName, query : NearestNeighbourQuery, filter: Option[HashSet[TupleID]]): Seq[Result] = {
    FeatureScanner(Entity.retrieveEntity(entityname), query, filter)
  }

  /**
   *
   * @param indexname
   * @param query
   * @param filter
   * @return
   */
  def indexQuery(indexname : IndexName, query : NearestNeighbourQuery, filter: Option[HashSet[TupleID]]): Seq[Result] = {
    if(query.indexOnly){
      indexOnlyQuery(indexname, query, filter)
    } else {
      indexQueryWithResults(indexname, query, filter)
    }
  }

  /**
   *
   * @param indexname
   * @param query
   * @param filter
   * @return
   */
  def indexQueryWithResults(indexname : IndexName, query : NearestNeighbourQuery, filter: Option[HashSet[TupleID]]): Seq[Result] = {
    val entityname = CatalogOperator.getIndexEntity(indexname)

    val future = Future {
      Entity.retrieveEntity(entityname)
    }

    val tidList = IndexScanner(Index.retrieveIndex(indexname), query, filter)

    val entity = Await.result[Entity](future, Duration(100, TimeUnit.SECONDS))
    FeatureScanner(entity, query, Some(tidList))
  }

  /**
   *
   * @param indexname
   * @param query
   * @param filter
   * @return
   */
  def indexOnlyQuery(indexname : IndexName, query : NearestNeighbourQuery, filter: Option[HashSet[TupleID]]): Seq[Result] = {
    IndexScanner(Index.retrieveIndex(indexname), query, filter).map(Result(0, _, null)).toSeq
  }

  /**
   *
   * @param entityname
   * @param query
   * @param filter
   * @param onComplete
   * @return
   */
  def progressiveQuery(entityname: EntityName, query : NearestNeighbourQuery, filter: Option[HashSet[TupleID]], onComplete: (ProgressiveQueryStatus.Value, Seq[Result], Float, Map[String, String]) => Unit): ProgressiveQueryStatusTracker = {
    val indexnames = Index.getIndexnames(entityname)

    val options = mMap[String, String]()

    val tracker = new ProgressiveQueryStatusTracker(query.queryID.get)

    //index scans
    val indexScanFutures = indexnames.par.map { indexname =>
      val isf = new IndexScanFuture(indexname, query, onComplete, tracker)
    }

    //sequential scan
    val ssf = new SequentialScanFuture(entityname, query, onComplete, tracker)
    
    tracker
  }


  /**
   *
   * @param entityname
   * @param query
   * @param filter
   * @return
   */
  def timedProgressiveQuery(entityname: EntityName, query : NearestNeighbourQuery, filter: Option[HashSet[TupleID]], timelimit : Duration): (Seq[Result], Float) = {
    val indexnames = Index.getIndexnames(entityname)

    val options = mMap[String, String]()

    val tracker = new ProgressiveQueryStatusTracker(query.queryID.get)

    val timerFuture = Future{
      val sleepTime = Duration(500.toLong, "millis")
      var nSleep = (timelimit / sleepTime).toInt

      while(tracker.status != ProgressiveQueryStatus.FINISHED && nSleep > 0){
        nSleep -= 1
        Thread.sleep(sleepTime.toMillis)
      }
    }

    //index scans
    val indexScanFutures = indexnames.par.map { indexname =>
      val isf = new IndexScanFuture(indexname, query, (status, result, confidence, info) => (), tracker)
    }

    //sequential scan
    val ssf = new SequentialScanFuture(entityname, query, (status, result, confidence, info) => (), tracker)

    Await.result(timerFuture, timelimit)
    tracker.stop()
    tracker.results
  }
}



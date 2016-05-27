package ch.unibas.dmi.dbis.adam.query

import java.util.concurrent.TimeUnit

import ch.unibas.dmi.dbis.adam.AdamTestBase
import ch.unibas.dmi.dbis.adam.api._
import ch.unibas.dmi.dbis.adam.config.FieldNames
import ch.unibas.dmi.dbis.adam.datatypes.feature.FeatureVectorWrapper
import ch.unibas.dmi.dbis.adam.index.structures.IndexTypes
import ch.unibas.dmi.dbis.adam.main.SparkStartup
import ch.unibas.dmi.dbis.adam.main.SparkStartup.Implicits._
import ch.unibas.dmi.dbis.adam.query.handler.internal.AggregationExpression.{ExpressionEvaluationOrder, IntersectExpression}
import ch.unibas.dmi.dbis.adam.query.handler.internal.{CompoundQueryExpression, IndexScanExpression}
import ch.unibas.dmi.dbis.adam.query.progressive.{AllProgressivePathChooser, ProgressiveObservation}
import ch.unibas.dmi.dbis.adam.query.query.{BooleanQuery, NearestNeighbourQuery}
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration.Duration


/**
  * adampro
  *
  * Ivan Giangreco
  * March 2016
  */
class QueryTestSuite extends AdamTestBase with ScalaFutures {
  feature("standard query") {
    /**
      *
      */
    scenario("perform a sequential query") {
      withQueryEvaluationSet { es =>
        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.sequential(es.entity.entityname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.zip(es.nnResults).foreach {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
      }
    }

    scenario("perform a weighted sequential query") {
      withQueryEvaluationSet { es =>
        When("performing a kNN query")
        val weights = new FeatureVectorWrapper(Seq.fill(es.feature.vector.length)(0.toFloat)).vector
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, Some(weights), es.distance, es.k)
        val results = QueryOp.sequential(es.entity.entityname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.foreach { res =>
            assert(res._1 == 0)
        }
      }
    }

    /**
      *
      */
    scenario("perform a ecp index query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.ECPINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(index.get.indexname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should have a match at least in the first element")
        Seq(results.zip(es.nnResults).head).map {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }

        log.info("score: " + getScore(es.nnResults.map(_._2), results.map(_._2)))
      }
    }

    /**
      *
      */
    scenario("perform a lsh index query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.LSHINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(index.get.indexname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should have a match at least in the first element")
        Seq(results.zip(es.nnResults).head).map {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }

        log.info("score: " + getScore(es.nnResults.map(_._2), results.map(_._2)))
      }
    }

    /**
      *
      */
    scenario("perform a pq index query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.PQINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(index.get.indexname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should have a match at least in the first element")
        Seq(results.zip(es.nnResults).head).map {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }

        log.info("score: " + getScore(es.nnResults.map(_._2), results.map(_._2)))
      }
    }


    /**
      *
      */
    scenario("perform a sh index query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.SHINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(index.get.indexname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should have a match at least in the first element")
        Seq(results.zip(es.nnResults).head).map {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }

        log.info("score: " + getScore(es.nnResults.map(_._2), results.map(_._2)))
      }
    }


    /**
      *
      */
    scenario("perform a vaf index query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(index.get.indexname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.zip(es.nnResults).foreach {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
      }
    }

    /**
      *
      */
    scenario("perform a vaf index query without specifying index name") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(es.entity.entityname, IndexTypes.VAFINDEX, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.zip(es.nnResults).foreach {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
      }
    }


    /**
      *
      */
    scenario("perform a vav index query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAVINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val results = QueryOp.index(index.get.indexname, nnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.zip(es.nnResults).foreach {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
      }
    }


    /**
      *
      */
    scenario("perform a vaf index query and a boolean query") {
      withQueryEvaluationSet { es =>
        Given("an index")
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)
        assert(index.isSuccess)

        When("performing a kNN query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val bq = BooleanQuery(es.where)
        val results = QueryOp.index(index.get.indexname, nnq, Option(bq)).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.zip(es.nnbqResults).foreach {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
      }
    }


    /**
      *
      */
    scenario("perform a boolean query on the joined metadata") {
      withQueryEvaluationSet { es =>
        Given("a joinable table")

        val df = es.fullData

        val metadataname = es.entity.entityname + "_metadata"

        val metadata = df.withColumn("tid100", df("tid") * 100).select("tid", "tid100")

        SparkStartup.metadataStorage.write(metadataname, metadata)

        When("performing a boolean query on the joined metadata")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)

        val inStmt = "IN " + es.nnbqResults.map {
          case (distance, tid) =>
            (tid * 100).toString
        }.mkString("(", ", ", ")")
        val whereStmt = Option(Seq("tid100" -> inStmt))

        val bq = BooleanQuery(whereStmt, Some(Seq((metadataname, Seq("tid")))))
        val results = QueryOp.sequential(es.entity.entityname, nnq, Option(bq)).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors")
        results.zip(es.nnbqResults).foreach {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
        //clean up
        SparkStartup.metadataStorage.drop(metadataname)
      }
    }

    /**
      *
      */
    scenario("perform a simple Boolean query (without NN)") {
      withQueryEvaluationSet { es =>
        Given("a joinable table")

        val df = es.fullData

        val metadataname = es.entity.entityname + "_metadata"

        val metadata = df.withColumn("tid100", df("tid") * 100).select("tid", "tid100")

        SparkStartup.metadataStorage.write(metadataname, metadata)

        When("performing a boolean query on the joined metadata")
        val inStmt = "IN " + es.nnbqResults.map {
          case (distance, tid) =>
            (tid * 100).toString
        }.mkString("(", ", ", ")")
        val whereStmt = Option(Seq("tid100" -> inStmt))

        val bq = BooleanQuery(whereStmt, Some(Seq((metadataname, Seq("tid")))))

        val results = QueryOp.booleanQuery(es.entity.entityname, Option(bq)).get.get
          .map(r => r.getAs[Long]("tid")).collect() //get here TID of metadata
          .sorted.toSeq

        Then("we should retrieve the metadata")
        results.zip(es.nnbqResults.map(_._2).sorted).foreach {
          case (res, gt) =>
            assert(res == gt)
        }

        //clean up
        SparkStartup.metadataStorage.drop(metadataname)
      }
    }
  }

  feature("progressive query") {
    /**
      *
      */
    scenario("perform a progressive query") {
      withQueryEvaluationSet { es =>
        Given("some indexes")
        IndexOp(es.entity.entityname, "featurefield", IndexTypes.SHINDEX, es.distance)
        IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)

        def processResults(po: ProgressiveObservation) {
          val results = po.results.get.map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
            .sortBy(_._1).toSeq

          Then("eventually we should retrieve the k nearest neighbors")
          if (math.abs(po.confidence - 1.0) < EPSILON) {

            results.zip(es.nnResults).foreach {
              case (res, gt) =>
                assert(res._2 == gt._2)
                assert(math.abs(res._1 - gt._1) < EPSILON)
            }
          }
        }

        When("performing a kNN progressive query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val statusTracker = QueryOp.progressive(es.entity.entityname, nnq, None, new AllProgressivePathChooser(), processResults).get

        whenReady(statusTracker) { result =>
          Then("the confidence should be 1.0")
          assert(math.abs(result.observation.confidence - 1.0) < EPSILON)
          assert(result.observation.results.get.count() >= es.k)
        }
      }
    }


    /**
      *
      */
    scenario("perform a timed query") {
      withQueryEvaluationSet { es =>
        Given("some indexes")
        IndexOp(es.entity.entityname, "featurefield", IndexTypes.SHINDEX, es.distance)
        IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)

        val timelimit = Duration(10, TimeUnit.SECONDS)

        When("performing a kNN progressive query")
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k)
        val po = QueryOp.timedProgressive(es.entity.entityname, nnq, None, new AllProgressivePathChooser(), timelimit).get

        Then("we should have a match at least in the first element")
        val results = po.results.get.map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        Seq(results.zip(es.nnResults).head).map {
          case (res, gt) =>
            assert(res._2 == gt._2)
            assert(math.abs(res._1 - gt._1) < EPSILON)
        }
      }
    }
  }



  feature("compound query") {
    /**
      *
      */
    scenario("perform a compound query with various index types") {
      withQueryEvaluationSet { es =>
        Given("some indexes")

        val shidx = IndexOp(es.entity.entityname, "featurefield", IndexTypes.SHINDEX, es.distance)
        val vaidx = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)

        When("performing a kNN query of two indices and performing the intersect")
        //nnq has numOfQ  = 0 to avoid that by the various randomized q's the results get different
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k, true, Map("numOfQ" -> "0"), None)

        val shqh = new IndexScanExpression(shidx.get.indexname)(nnq)()
        val vhqh = new IndexScanExpression(vaidx.get.indexname)(nnq)()

        val results = time {
          new CompoundQueryExpression(new IntersectExpression(shqh, vhqh)).prepareTree().evaluate().get
            .map(r => (r.getAs[Long]("tid"))).collect().sorted
        }

        val shres = shqh.prepareTree().evaluate().get.map(r => r.getAs[Long]("tid")).collect()
        val vhres = vhqh.prepareTree().evaluate().get.map(r => r.getAs[Long]("tid")).collect()

        Then("we should have a match in the aggregated list")
        val gt = vhres.intersect(shres).sorted

        results.zip(gt).map {
          case (res, gt) =>
            assert(res == gt)
        }
      }
    }


    /**
      *
      */
    scenario("perform a compound query with the same index type at different coarseness levels") {
      withQueryEvaluationSet { es =>
        Given("some indexes")

        val vaidx1 = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance, Map("maxMarks" -> "4"))
        val vaidx2 = IndexOp(es.entity.entityname, "featurefield", IndexTypes.VAFINDEX, es.distance)

        When("performing a kNN query of two indices and performing the intersect")
        //nnq has numOfQ  = 0 to avoid that by the various randomized q's the results get different
        val nnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k, true, Map("numOfQ" -> "0"), None)

        val va1qh = new IndexScanExpression(vaidx1.get.indexname)(nnq)()
        val va2qh = new IndexScanExpression(vaidx2.get.indexname)(nnq)()

        val results = time {
          CompoundQueryExpression(new IntersectExpression(va1qh, va2qh, ExpressionEvaluationOrder.Parallel)).prepareTree().evaluate().get
            .map(r => (r.getAs[Long]("tid"))).collect().sorted
        }

        //results (note we truly compare the id-column here and not the metadata "tid"
        val vh1res = va1qh.prepareTree().evaluate().get.map(r => r.getAs[Long]("tid")).collect()
        val vh2res = va2qh.prepareTree().evaluate().get.map(r => r.getAs[Long]("tid")).collect()

        Then("we should have a match in the aggregated list")
        val gt = vh1res.intersect(vh2res).sorted

        results.zip(gt).map {
          case (res, gt) =>
            assert(res == gt)
        }
      }
    }
  }
}

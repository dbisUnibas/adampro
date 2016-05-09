package ch.unibas.dmi.dbis.adam.api

import ch.unibas.dmi.dbis.adam.datatypes.feature.FeatureVectorWrapper
import ch.unibas.dmi.dbis.adam.entity.Entity.EntityName
import ch.unibas.dmi.dbis.adam.entity.EntityHandler
import ch.unibas.dmi.dbis.adam.main.SparkStartup.Implicits._
import org.apache.log4j.Logger
import org.apache.spark.sql.types.{DataType, StructType, UserDefinedType}
import org.apache.spark.sql.{Row, types}

import scala.util.Random

/**
  * adampro
  *
  * Ivan Giangreco
  * March 2016
  */
object RandomDataOp {
  val log = Logger.getLogger(getClass.getName)

  /**
    *
    * @param entityname
    * @param collectionSize
    * @param vectorSize
    */
  def apply(entityname: EntityName, collectionSize: Int, vectorSize: Int): Unit = {
    log.debug("perform generate data operation")

    val limit = math.min(collectionSize, 100000)

    val entity = EntityHandler.load(entityname)
    if (entity.isFailure) {
      log.error("entity could not be loaded")
      throw entity.failed.get
    }

    //schema of random data dataframe to insert
    val schema = entity.get.schema.fields

    //data
    (0 until collectionSize).sliding(limit, limit).foreach { seq =>
      val rdd = ac.sc.parallelize(
        seq.map(idx => {
          var data = schema.map(field => randomGenerator(field.dataType)())
          Row(data: _*)
        })
      )
      val data = sqlContext.createDataFrame(rdd, StructType(schema))

      log.debug("inserting data batch")
      EntityHandler.insertData(entityname, data, true)
    }


    def randomGenerator(datatype: DataType): () => Any = {
      datatype match {
        case _: types.IntegerType => () => (Random.nextInt)
        case _: types.LongType => () => (Random.nextLong)
        case _: types.FloatType => () => (Random.nextFloat)
        case _: types.DoubleType => () => (Random.nextDouble)
        case _: types.StringType => () => (Random.nextString(10))
        case _: types.BooleanType => () => (Random.nextBoolean)
        case _: UserDefinedType[_] => () => new FeatureVectorWrapper(Seq.fill(vectorSize)(Random.nextFloat()))
      }
    }
  }
}



package org.vitrivr.adampro.main

import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.{SparkConf, SparkContext}
import org.vitrivr.adampro.api.EntityOp
import org.vitrivr.adampro.config.AdamConfig
import org.vitrivr.adampro.datatypes.bitString.BitStringUDT
import org.vitrivr.adampro.datatypes.feature.FeatureVectorWrapperUDT
import org.vitrivr.adampro.datatypes.gis.GeometryWrapperUDT
import org.vitrivr.adampro.entity.Entity
import org.vitrivr.adampro.helpers.optimizer.OptimizerRegistry
import org.vitrivr.adampro.utils.Logging

/**
  * adamtwo
  *
  * Ivan Giangreco
  * August 2015
  */
object SparkStartup extends Logging {
  val sparkConfig = new SparkConf().setAppName("ADAMpro")
    .set("spark.driver.maxResultSize", "1g")
    .set("spark.executor.memory", "2g")
    .set("spark.akka.frameSize", "1024")
    .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    .set("spark.kryoserializer.buffer.max", "2047m")
    .set("spark.kryoserializer.buffer", "2047")
    .registerKryoClasses(Array(classOf[BitStringUDT], classOf[FeatureVectorWrapperUDT], classOf[GeometryWrapperUDT]))
    .set("spark.scheduler.allocation.file", AdamConfig.schedulerFile)
    .set("spark.driver.allowMultipleContexts", "true")
    .set("spark.sql.parquet.compression.codec", "snappy")
    .set("spark.sql.hive.convertMetastoreParquet.mergeSchema", "false")
    .set("parquet.enable.summary-metadata", "false")


  if (AdamConfig.master.isDefined) {
    sparkConfig.setMaster(AdamConfig.master.get)
  }

  object Implicits extends AdamContext {
    implicit lazy val ac = this

    @transient implicit lazy val sc = new SparkContext(sparkConfig)
    sc.hadoopConfiguration.set("parquet.enable.summary-metadata", "false")
    sc.setLogLevel(AdamConfig.loglevel)
    //TODO: possibly switch to a jobserver (https://github.com/spark-jobserver/spark-jobserver), pass sqlcontext around

    //TODO: possibly adjust block and page size
    // val blockSize = 1024 * 1024 * 16      // 16MB
    // sc.hadoopConfiguration.setInt( "dfs.blocksize", blockSize )
    // sc.hadoopConfiguration.setInt( "parquet.block.size", blockSize )
    //also consider: https://issues.apache.org/jira/browse/SPARK-7263

    @transient implicit lazy val sqlContext = new HiveContext(sc)
  }

  val mainContext = Implicits.ac
  val contexts = Seq(mainContext)

  AdamConfig.engines.foreach { engine => mainContext.storageHandlerRegistry.value.register(engine)(mainContext) }
  OptimizerRegistry.loadDefault()(mainContext)

  //cache in beginning
  EntityOp.list().map(_.map(entityname => Entity.load(entityname)(mainContext)))
}
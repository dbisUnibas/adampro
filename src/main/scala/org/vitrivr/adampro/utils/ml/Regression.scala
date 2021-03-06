package org.vitrivr.adampro.utils.ml


import java.io.File

import breeze.linalg.DenseVector
import org.apache.commons.io.FileUtils
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression._
import org.apache.spark.mllib.tree.{DecisionTree, GradientBoostedTrees, RandomForest}
import org.apache.spark.mllib.tree.configuration.BoostingStrategy
import org.apache.spark.mllib.tree.model.{DecisionTreeModel, GradientBoostedTreesModel, RandomForestModel}
import org.apache.spark.rdd.RDD
import org.vitrivr.adampro.process.SharedComponentContext
import org.vitrivr.adampro.utils.Logging
import org.vitrivr.adampro.utils.ml.Regression._


/**
  * ADAMpro
  *
  * Ivan Giangreco
  * August 2017
  */
class Regression(val path: String)(@transient implicit val ac: SharedComponentContext) extends Logging with Serializable {
  def modelPath(name : String) = path + "/model/model_" + name + MODEL_SUFFIX

  val DEFAULT_ALGORITHM = "lin"

  val algorithms = Map(
    "lin"  ->  new LinearRegression(modelPath("lin")),
    "rig"  ->  new RidgeRegression(modelPath("rig")),
    "lasso" -> new LassoRegression(modelPath("lasso")),
    "dt"  ->  new DecisionTreeRegression(modelPath("dt")),
    "rf"  ->  new RandomForestRegression(modelPath("rf")),
    "gbt"  ->  new GBTRegression(modelPath("gbt"))
  )

  /**
    *
    * @param data
    */
  def train(data: Seq[TrainingSample]): Unit = {
    val labelledData = data.map(x => LabeledPoint(x.time, Vectors.dense(x.f.toArray)))
    val rdd = ac.sc.parallelize(labelledData)

    rdd.saveAsObjectFile(path + "/data/data_" + (System.currentTimeMillis() / 1000L).toString + DATA_SUFFIX)

    val trainingData = new File(path + "/data/").listFiles.filter(_.getName.endsWith(DATA_SUFFIX)).map { dataFile =>
      ac.sc.objectFile[LabeledPoint](dataFile.getAbsolutePath)
    }.reduce(_ union _)

    algorithms.foreach { case(name, algo) =>
      algo.train(trainingData)
    }
  }

  /**
    *
    * @param f
    * @param algorithmName
    * @return
    */
  def test(f: DenseVector[Double], algorithmName : Option[String]): Double = {
    val algorithm = if(algorithmName.isEmpty || algorithms.get(algorithmName.get).isEmpty){
      log.error("Algorithm for regression not correctly specified.")
      algorithms.get(DEFAULT_ALGORITHM).get
    } else {
      algorithms.get(algorithmName.get).get
    }

    algorithm.test(f)
  }
}

object Regression {
  val MODEL_SUFFIX = ".model"
  val DATA_SUFFIX = ".data"

  def train(data: Seq[TrainingSample], path : String)(implicit ac: SharedComponentContext): Unit ={
    new Regression(path).train(data)
  }


  abstract class RegressionModelClass(val path : String) {
    def train(input: RDD[LabeledPoint]) : Unit = {
      if(new File(path).exists()){ //remove old model
        FileUtils.deleteDirectory(new File(path))
      }

      run(input)
    }

    protected def run(input: RDD[LabeledPoint]) : Unit

    def test(f: DenseVector[Double]) : Double
  }

  /**
    *
    * @param path
    * @param ac
    */
  case class LinearRegression(override val path: String)(@transient implicit val ac: SharedComponentContext) extends RegressionModelClass(path) {
    var model: Option[LinearRegressionModel] =  None

    override def run(input: RDD[LabeledPoint]) : Unit = {
      model = None
      new LinearRegressionWithSGD().run(input).save(ac.sc, path)
    }

    override def test(f: DenseVector[Double]) : Double = {
      if(model.isEmpty){
        model = Some(LinearRegressionModel.load(ac.sc, path))
      }

      model.get.predict(Vectors.dense(f.toArray))
    }
  }

  /**
    *
    * @param path
    * @param ac
    */
  case class RidgeRegression(override val path: String)(@transient implicit val ac: SharedComponentContext) extends RegressionModelClass(path) {
    var model: Option[RidgeRegressionModel] =  None

    override def run(input: RDD[LabeledPoint]) : Unit = {
      model = None
      new RidgeRegressionWithSGD().run(input).save(ac.sc, path)
    }

    override def test(f: DenseVector[Double]) : Double = {
      if(model.isEmpty){
        model = Some(RidgeRegressionModel.load(ac.sc, path))
      }

      model.get.predict(Vectors.dense(f.toArray))
    }
  }

  /**
    *
    * @param path
    * @param ac
    */
  case class LassoRegression(override val path: String)(@transient implicit val ac: SharedComponentContext) extends RegressionModelClass(path) {
    var model: Option[LassoModel] =  None

    override def run(input: RDD[LabeledPoint]) : Unit = {
      model = None
      new LassoWithSGD().run(input).save(ac.sc, path)
    }

    override def test(f: DenseVector[Double]) : Double = {
      if(model.isEmpty){
        model = Some(LassoModel.load(ac.sc, path))
      }

      model.get.predict(Vectors.dense(f.toArray))
    }
  }

  /**
    *
    * @param path
    * @param ac
    */
  case class DecisionTreeRegression(override val path: String)(@transient implicit val ac: SharedComponentContext) extends RegressionModelClass(path) {
    var model: Option[DecisionTreeModel] =  None

    override def run(input: RDD[LabeledPoint]) : Unit = {
      model = None

      val categoricalFeaturesInfo = Map[Int, Int]()
      val impurity = "variance"
      val maxDepth = 5
      val maxBins = 32

      DecisionTree.trainRegressor(input, categoricalFeaturesInfo, impurity,
        maxDepth, maxBins).save(ac.sc, path)
    }

    override def test(f: DenseVector[Double]) : Double = {
      if(model.isEmpty){
        model = Some(DecisionTreeModel.load(ac.sc, path))
      }

      model.get.predict(Vectors.dense(f.toArray))
    }
  }

  /**
    *
    * @param path
    * @param ac
    */
  case class RandomForestRegression(override val path: String)(@transient implicit val ac: SharedComponentContext) extends RegressionModelClass(path) {
    var model: Option[RandomForestModel] =  None

    override def run(input: RDD[LabeledPoint]) : Unit = {
      model = None

      val categoricalFeaturesInfo = Map[Int, Int]()
      val numTrees = 10
      val featureSubsetStrategy = "auto"
      val impurity = "variance"
      val maxDepth = 4
      val maxBins = 32

      RandomForest.trainRegressor(input, categoricalFeaturesInfo,
        numTrees, featureSubsetStrategy, impurity, maxDepth, maxBins).save(ac.sc, path)
    }

    override def test(f: DenseVector[Double]) : Double = {
      if(model.isEmpty){
        model = Some(RandomForestModel.load(ac.sc, path))
      }

      model.get.predict(Vectors.dense(f.toArray))
    }
  }


  /**
    *
    * @param path
    * @param ac
    */
  case class GBTRegression(override val path: String)(@transient implicit val ac: SharedComponentContext) extends RegressionModelClass(path) {
    var model: Option[GradientBoostedTreesModel] =  None

    override def run(input: RDD[LabeledPoint]) : Unit = {
      model = None

      val boostingStrategy = BoostingStrategy.defaultParams("Regression")
      boostingStrategy.setNumIterations(20)
      boostingStrategy.treeStrategy.setMaxDepth(5)

      GradientBoostedTrees.train(input, boostingStrategy).save(ac.sc, path)
    }

    override def test(f: DenseVector[Double]) : Double = {
      if(model.isEmpty){
        model = Some(GradientBoostedTreesModel.load(ac.sc, path))
      }

      model.get.predict(Vectors.dense(f.toArray))
    }
  }
}
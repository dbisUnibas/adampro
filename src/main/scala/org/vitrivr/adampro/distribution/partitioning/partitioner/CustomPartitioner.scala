package org.vitrivr.adampro.distribution.partitioning.partitioner

import org.apache.spark.sql.DataFrame
import org.vitrivr.adampro.data.datatypes.vector.Vector._
import org.vitrivr.adampro.data.entity.Entity.AttributeName
import org.vitrivr.adampro.data.entity.EntityNameHolder
import org.vitrivr.adampro.distribution.partitioning.PartitionerChoice
import org.vitrivr.adampro.process.SharedComponentContext

/**
  * ADAMpar.
  *
  * Currently the partitioners have a lot of code which looks similar. Maybe from a separation-of concerns point of view it would be good to have more methods here
  * which call abstract methods. i.e. the Repartitioning-Code looks very similar and dropping the old and inserting the new Partitioner in the catalog is not something
  * every Partitioner should have to think of
  *
  * Additionally, this partitioning implementation is linked very closely to the index structure.
  * In theory, a partitioner should just partition Data Frames and not work this closely with indices.
  *
  * Silvan Heller
  * June 2016
  */
abstract class CustomPartitioner {

  /** Which partitioner this is */
  def partitionerName: PartitionerChoice.Value

  /**
    * Maybe in the future the indexname will be removed and each partitioner will train on their own keys.
    *
    * @param data        DataFrame you want to partition
    * @param attribute   Attribute you want to perform the partition on. If none are provided, the index pk is used instead
    * @param indexName   Will be used to store partitioner information in the catalog
    * @param nPartitions how many partitions shall be created
    * @return the partitioned DataFrame
    */
  def apply(data: DataFrame, attribute: Option[AttributeName] = None, indexName: Option[EntityNameHolder] = None, nPartitions: Int, options: Map[String, String] = Map[String, String]())(implicit ac: SharedComponentContext): DataFrame

  /** Returns the partitions to be queried for a given Featurevector */
  def getPartitions(q: MathVector, dropPercentage: Double, indexName: EntityNameHolder)(implicit ac: SharedComponentContext): Seq[Int]
}

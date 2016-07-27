package ch.unibas.dmi.dbis.adam.config

/**
  * adamtwo
  *
  * Ivan Giangreco
  * March 2016
  */
object FieldNames {
  val distanceColumnName = "adamprodistance"
  val featureIndexColumnName = "adamproindexfeature"

  val partitionColumnName = "adampropartition"
  val provenanceColumnName = "adamproprovenance"

  val reservedNames = Seq(distanceColumnName, featureIndexColumnName)
}

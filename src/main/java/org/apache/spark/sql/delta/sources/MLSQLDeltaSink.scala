package org.apache.spark.sql.delta.sources

import org.apache.hadoop.fs.Path
import org.apache.spark.sql.delta._
import org.apache.spark.sql.delta.commands.UpsertTableInDelta
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.{DataFrame, SQLContext}


class MLSQLDeltaSink(
                      sqlContext: SQLContext,
                      path: Path,
                      partitionColumns: Seq[String],
                      outputMode: OutputMode,
                      options: DeltaOptions,
                      parameters: Map[String, String]
                    ) extends DeltaSink(
  sqlContext: SQLContext,
  path: Path,
  partitionColumns: Seq[String],
  outputMode: OutputMode,
  options: DeltaOptions) {

  private val deltaLog = DeltaLog.forTable(sqlContext.sparkSession, path)

  private val sqlConf = sqlContext.sparkSession.sessionState.conf

  override def addBatch(batchId: Long, data: DataFrame): Unit = {


    if (parameters.contains(UpsertTableInDelta.ID_COLS)) {
      def _run() = {
        UpsertTableInDelta(data, None, Option(outputMode), deltaLog,
          new DeltaOptions(Map[String, String](), sqlContext.sparkSession.sessionState.conf),
          Seq(),
          parameters ++ Map(UpsertTableInDelta.BATCH_ID -> batchId.toString)).run(sqlContext.sparkSession)
      }

      val TRY_MAX_TIMES = 3
      var count = 0L
      while (count <= TRY_MAX_TIMES) {
        try {
          _run
          count += Long.MaxValue
        } catch {
          case e: DeltaConcurrentModificationException =>
            count += 1
            logInfo(s"try ${count} times", e)
          case e: Exception => throw e;
        }
      }


    } else {
      super.addBatch(batchId, data)
    }
  }
}

import FileSystem.FileSystem
import VotingSystem.FileSuiteManager

import java.io.{BufferedWriter, FileWriter}
import scala.collection.mutable.ListBuffer
import scala.util.Random
import com.opencsv.CSVWriter

import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}

object Experiment {

  /**
   * Output path
   */
  val outputPath: String = "C:\\Users\\blok_\\Desktop\\Consistency.csv"

  /**
   * File system parameters
   */
  val numContainers: Int = 5
  val latencies: Seq[Seq[Int]] = Seq(Seq(1, 1, 1, 1, 1))
  val blockingProbs: Seq[Seq[Double]] = Seq(Seq(0.00, 0.00, 0.00, 0.00, 0.00),
                                            Seq(0.05, 0.05, 0.05, 0.05, 0.05),
                                            Seq(0.10, 0.10, 0.10, 0.10, 0.10),
                                            Seq(0.15, 0.15, 0.15, 0.15, 0.15),
                                            Seq(0.20, 0.20, 0.20, 0.20, 0.20),
                                            Seq(0.25, 0.25, 0.25, 0.25, 0.25),
                                            Seq(0.30, 0.30, 0.30, 0.30, 0.30),
                                            Seq(0.35, 0.35, 0.35, 0.35, 0.35),
                                            Seq(0.40, 0.40, 0.40, 0.40, 0.40),
                                            Seq(0.45, 0.45, 0.45, 0.45, 0.45),
                                            Seq(0.50, 0.50, 0.50, 0.50, 0.50))

  /**
   * Testbench parameters
   */
  val readPortions: Seq[Double] = Seq(0.5)
  val transactionLengths: Seq[Int] = Seq(1)
  val numTransactions: Int = 10000

  /**
   * file suite parameters
   */
  val suiteId: Int = 1
  val suiteRWPairs: Seq[(Int, Int)] = Seq((1, 5), (2, 4), (3, 3), (4, 2), (5, 1))
  val repWeights: Seq[Seq[Int]] = Seq(Seq(1, 1, 1, 1, 1))

  // Perform statistical experiment
  def main(args: Array[String]): Unit = {

    val r: Random = scala.util.Random
    var event: Double = r.nextDouble()

    val file = new BufferedWriter(new FileWriter(outputPath))
    val csvWriter = new CSVWriter(file)
    val records = new ListBuffer[Array[String]]()
    val csvColumns = Array(
      "latencies",
      "blocking probability",
      "read percentage",
      "write percentage",
      "read/write ratio",
      "transaction length",
      "transaction count",
      "r",
      "w",
      "representative weights",
      "successful call percentage",
      "failed call percentage",
      "transaction commit percentage",
      "transaction abort percentage",
      "average call latency",
      "consistency percentage"
    )
    records += csvColumns

    // For every combination of parameters
    for (latency <- latencies;
         blockingProb <- blockingProbs;
         readPortion <- readPortions;
         transactionLength <- transactionLengths;
         rw <- suiteRWPairs;
         weights <- repWeights) {

      val fileSystem = FileSystem(numContainers, latency, blockingProb)
      val manager = FileSuiteManager(fileSystem)
      fileSystem.createRepresentatives(suiteId, rw._1, rw._2, weights)
      var successCount: Int = 0
      var commitCount: Int = 0
      var totalLatency: Int = 0
      var consistencyCount: Int = 0
      var inconsistencyCount: Int = 0
      var committedContent: Int = -1
      var tentativeContent: Int = -1

      for (i <- 0 until numTransactions) {
        var aborted: Boolean = false
        tentativeContent = committedContent

        manager.begin()
        breakable { for (j <- 0 until transactionLength) {
          if (event <= readPortion) {
            val result = manager.read(suiteId)
            result match {
              case Left(f) => {}
              case Right(r) => {
                successCount += 1
                totalLatency += r._2
                if (r._1 == tentativeContent) {
                  consistencyCount += 1
                }
                else {
                  inconsistencyCount += 1
                }
              }
            }
          }
          else {
            val result = manager.write(suiteId, tentativeContent + 1)
            result match {
              case Left(f) => {
                manager.abort()
                aborted = true
                break
              }
              case Right(r) => {
                tentativeContent += 1
                successCount += 1
                totalLatency += r
              }
            }
          }
          event = r.nextDouble()
        }}

        if (!aborted) {
          commitCount += 1
          committedContent = tentativeContent
          manager.commit()
        }
      }

      // Calculate statistical results
      val readPercentage: Double = readPortion * 100.0
      val writePercentage: Double = 100.0 - readPercentage
      val readWriteRatioString: String = readPortion + "/" + (1.0 - readPortion)
      val avgLatency: Double = totalLatency.toDouble / successCount.toDouble
      val successCallPercentage: Double = (successCount.toDouble / (numTransactions.toDouble * transactionLength.toDouble)) * 100.0
      val failCallPercentage: Double = 100.0 - successCallPercentage
      val commitPercentage: Double = (commitCount.toDouble / numTransactions.toDouble) * 100.0
      val abortPercentage: Double = 100.0 - commitPercentage
      val consistencyPercentage: Double = (consistencyCount.toDouble / (consistencyCount.toDouble + inconsistencyCount.toDouble)) * 100.0

      // Gather results in ListBuffer in String format
      records += Array(
        latency.toString,
        blockingProb.toString,
        readPercentage.toString,
        writePercentage.toString,
        readWriteRatioString,
        transactionLength.toString,
        numTransactions.toString,
        rw._1.toString,
        rw._2.toString,
        weights.toString(),
        successCallPercentage.toString,
        failCallPercentage.toString,
        commitPercentage.toString,
        abortPercentage.toString,
        avgLatency.toString,
        consistencyPercentage.toString
      )

      println(
        "Experiment statistics: " + "\n" +
        "latencies: " + latency + "\n" +
        "blocking probability: " + blockingProb + "\n" +
        "read percentage: " + readPercentage + "\n" +
        "write percentage: " + writePercentage + "\n" +
        "read/write ratio: " + readWriteRatioString + "\n" +
        "transaction length: " + transactionLength + "\n" +
        "transaction count: " + numTransactions + "\n" +
        "r: " + rw._1 + "\n" +
        "w: " + rw._2 + "\n" +
        "representative weights: " + weights + "\n" +
        "successful call percentage: " + successCallPercentage + "\n" +
        "failed call percentage: " + failCallPercentage + "\n" +
        "transaction commit percentage: " + commitPercentage + "\n" +
        "transaction abort percentage: " + abortPercentage + "\n" +
        "average call latency: " + avgLatency + "\n" +
        "consistency percentage: " + consistencyPercentage + "\n\n"
      )
    }

    // Write results to CSV file
    try {
      csvWriter.writeAll(records.toList.asJava)
    }
    catch {
      case e: java.io.IOException => println("failed to write")
    }
    file.close()
  }
}

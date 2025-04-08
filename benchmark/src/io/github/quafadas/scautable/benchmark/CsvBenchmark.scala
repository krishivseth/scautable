package io.github.quafadas.scautable.benchmark

import io.github.quafadas.scautable.CSVParser
import java.nio.file.{Files, Paths}
import java.io.{File, PrintWriter, FileWriter}
import scala.jdk.CollectionConverters._
import java.time.{Duration, Instant}
import java.text.NumberFormat

/**
 * Comprehensive benchmarking utility for CSV parsing
 */
object CsvBenchmark {
  
  /** Benchmark configuration */
  case class BenchmarkConfig(
    rowSizes: Seq[Int] = Seq(10, 100, 1000, 10000, 100000),
    columnSizes: Seq[Int] = Seq(5, 10, 50, 100),
    iterations: Int = 3,
    warmupIterations: Int = 1,
    maxCellCount: Int = 10000000, // Skip combinations exceeding this cell count
    reportDir: String = "benchmark-reports"
  )
  
  /** Single benchmark result */
  case class BenchmarkResult(
    rows: Int,
    columns: Int,
    fileSize: Long,
    parseTimeMs: Long,
    memoryUsedMB: Double,
    rowsPerSecond: Double,
    mbPerSecond: Double
  )
  
  def main(args: Array[String]): Unit = {
    val config = if (args.nonEmpty) {
      // Parse config from args
      BenchmarkConfig(
        rowSizes = args(0).split(",").map(_.trim.toInt).toSeq,
        columnSizes = args(1).split(",").map(_.trim.toInt).toSeq,
        iterations = if (args.length > 2) args(2).toInt else 3
      )
    } else {
      BenchmarkConfig()
    }
    
    println(s"Running CSV benchmarks with configuration:")
    println(s"- Row sizes: ${config.rowSizes.mkString(", ")}")
    println(s"- Column sizes: ${config.columnSizes.mkString(", ")}")
    println(s"- Iterations: ${config.iterations} (with ${config.warmupIterations} warmup)")
    println(s"- Max cell count: ${NumberFormat.getNumberInstance().format(config.maxCellCount)}")
    println()
    
    // Ensure report directory exists
    val reportDir = new File(config.reportDir)
    if (!reportDir.exists()) {
      reportDir.mkdirs()
    }
    
    // Run benchmarks
    val results = runBenchmarks(config)
    
    // Print results
    printResults(results)
    
    // Save results to CSV
    val timestamp = java.time.LocalDateTime.now().toString.replace(":", "-")
    val csvFile = new File(reportDir, s"benchmark-results-$timestamp.csv")
    saveResultsToCsv(results, csvFile.getPath)
    
    // Generate reports
    val reportFile = new File(reportDir, s"benchmark-report-$timestamp.md")
    generateMarkdownReport(results, config, reportFile.getPath)
    
    // Generate recommendations
    val recommendations = generateRecommendations(results)
    println("\n" + recommendations)
    
    // Save recommendations
    val recommendationsFile = new File(reportDir, s"recommendations-$timestamp.md")
    new PrintWriter(recommendationsFile) { write(recommendations); close() }
    
    println(s"\nResults and reports saved to: ${reportDir.getAbsolutePath}")
  }
  
  def runBenchmarks(config: BenchmarkConfig): Seq[BenchmarkResult] = {
    val results = for {
      rows <- config.rowSizes
      cols <- config.columnSizes 
      if rows * cols <= config.maxCellCount
    } yield {
      println(s"Benchmarking $rows rows × $cols columns...")
      
      // Run multiple iterations
      val iterationResults = for (i <- 0 until (config.warmupIterations + config.iterations)) yield {
        // Generate test file
        val file = generateCsvFile(rows, cols)
        val fileSize = file.length()
        
        try {
          // For warmup iterations, just parse without measuring
          if (i < config.warmupIterations) {
            val lines = Files.readAllLines(file.toPath).iterator.asScala
            while (lines.hasNext) {
              CSVParser.parseLine(lines.next())
            }
            // Return dummy result for warmup
            BenchmarkResult(rows, cols, fileSize, 0, 0, 0, 0)
          } else {
            // Force garbage collection before test
            System.gc()
            Thread.sleep(100)
            val startMemory = getUsedMemory()
            
            // Measure parsing time
            val startTime = Instant.now()
            val content = Files.readAllLines(file.toPath).asScala
            val parsedData = content.map(line => CSVParser.parseLine(line))
            val endTime = Instant.now()
            val durationMs = Duration.between(startTime, endTime).toMillis
            
            // Measure memory usage
            System.gc()
            Thread.sleep(100)
            val endMemory = getUsedMemory()
            val memoryUsedMB = (endMemory - startMemory) / (1024.0 * 1024.0)
            
            // Calculate metrics
            val rowsPerSecond = rows * 1000.0 / durationMs
            val mbPerSecond = fileSize * 1000.0 / (1024.0 * 1024.0 * durationMs)
            
            BenchmarkResult(
              rows = rows,
              columns = cols,
              fileSize = fileSize,
              parseTimeMs = durationMs,
              memoryUsedMB = memoryUsedMB,
              rowsPerSecond = rowsPerSecond,
              mbPerSecond = mbPerSecond
            )
          }
        } finally {
          file.delete()
        }
      }
      
      // Skip warmup results and compute averages
      val measurementResults = iterationResults.drop(config.warmupIterations)
      
      // Calculate average of all metrics
      BenchmarkResult(
        rows = rows,
        columns = cols,
        fileSize = measurementResults.map(_.fileSize).sum / config.iterations,
        parseTimeMs = measurementResults.map(_.parseTimeMs).sum / config.iterations,
        memoryUsedMB = measurementResults.map(_.memoryUsedMB).sum / config.iterations,
        rowsPerSecond = measurementResults.map(_.rowsPerSecond).sum / config.iterations,
        mbPerSecond = measurementResults.map(_.mbPerSecond).sum / config.iterations
      )
    }
    
    results
  }
  
  private def getUsedMemory(): Long = {
    val runtime = Runtime.getRuntime
    runtime.totalMemory() - runtime.freeMemory()
  }
  
  private def generateCsvFile(rows: Int, cols: Int): File = {
    val file = File.createTempFile("benchmark", ".csv")
    file.deleteOnExit()
    
    val writer = new PrintWriter(new FileWriter(file))
    try {
      // Write header row
      writer.println((0 until cols).map(i => s"col$i").mkString(","))
      
      // Write data rows
      for (row <- 0 until rows) {
        writer.println((0 until cols).map(col => s"val${row}_${col}").mkString(","))
      }
    } finally {
      writer.close()
    }
    
    file
  }
  
  private def printResults(results: Seq[BenchmarkResult]): Unit = {
    println("\nCSV Performance Benchmarks")
    println("=========================")
    println(f"| ${"Rows"}%10s | ${"Columns"}%7s | ${"File Size"}%10s | ${"Parse Time"}%10s | ${"Rows/s"}%10s | ${"MB/s"}%8s |")
    println(f"| ${"-" * 10}%10s | ${"-" * 7}%7s | ${"-" * 10}%10s | ${"-" * 10}%10s | ${"-" * 10}%10s | ${"-" * 8}%8s |")
    
    results.foreach { r =>
      println(f"| ${r.rows}%10d | ${r.columns}%7d | ${r.fileSize/1024}%8d KB | ${r.parseTimeMs}%8d ms | ${r.rowsPerSecond}%8.0f | ${r.mbPerSecond}%6.2f |")
    }
  }
  
  private def saveResultsToCsv(results: Seq[BenchmarkResult], filename: String): Unit = {
    val writer = new PrintWriter(new FileWriter(filename))
    try {
      writer.println("rows,columns,fileSize,parseTimeMs,memoryUsedMB,rowsPerSecond,mbPerSecond")
      results.foreach { r =>
        writer.println(s"${r.rows},${r.columns},${r.fileSize},${r.parseTimeMs},${r.memoryUsedMB},${r.rowsPerSecond},${r.mbPerSecond}")
      }
    } finally {
      writer.close()
    }
  }
  
  private def generateMarkdownReport(results: Seq[BenchmarkResult], config: BenchmarkConfig, filename: String): Unit = {
    val writer = new PrintWriter(new FileWriter(filename))
    try {
      writer.println("# CSV Performance Benchmark Report")
      writer.println()
      writer.println("## Environment")
      writer.println(s"- Java: ${System.getProperty("java.version")}")
      writer.println(s"- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
      writer.println(s"- Available processors: ${Runtime.getRuntime().availableProcessors()}")
      writer.println(s"- Max memory: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
      writer.println()
      writer.println("## Configuration")
      writer.println(s"- Row sizes: ${config.rowSizes.mkString(", ")}")
      writer.println(s"- Column sizes: ${config.columnSizes.mkString(", ")}")
      writer.println(s"- Iterations: ${config.iterations}")
      writer.println(s"- Warmup iterations: ${config.warmupIterations}")
      writer.println()
      writer.println("## Results")
      writer.println()
      writer.println("### Raw Data")
      writer.println()
      writer.println("| Rows | Columns | File Size (KB) | Parse Time (ms) | Memory (MB) | Rows/s | MB/s |")
      writer.println("|------|---------|---------------|-----------------|-------------|--------|------|")
      
      results.foreach { r =>
        writer.println(f"| ${r.rows}%d | ${r.columns}%d | ${r.fileSize/1024}%.2f | ${r.parseTimeMs}%d | ${r.memoryUsedMB}%.2f | ${r.rowsPerSecond}%.0f | ${r.mbPerSecond}%.2f |")
      }
      
      writer.println()
      writer.println("### Visualizations")
      writer.println()
      writer.println("(Note: In a real implementation, this would include links to generated charts)")
      
      writer.println()
      writer.println("## Analysis")
      writer.println()
      
      // Time complexity analysis
      val smallFiles = results.filter(_.rows <= 1000)
      val largeFiles = results.filter(_.rows > 1000)
      
      if (smallFiles.nonEmpty && largeFiles.nonEmpty) {
        val smallAvgRate = smallFiles.map(_.rowsPerSecond).sum / smallFiles.size
        val largeAvgRate = largeFiles.map(_.rowsPerSecond).sum / largeFiles.size
        val ratio = largeAvgRate / smallAvgRate
        
        writer.println("### Time Complexity")
        writer.println()
        writer.println(f"- Small file parse rate (≤1000 rows): ${smallAvgRate}%.2f rows/second")
        writer.println(f"- Large file parse rate (>1000 rows): ${largeAvgRate}%.2f rows/second")
        writer.println(f"- Ratio (large:small): ${ratio}%.2f")
        writer.println()
        
        if (ratio < 0.7) {
          writer.println("**Finding**: Performance degrades with larger files, suggesting possible super-linear time complexity.")
        } else {
          writer.println("**Finding**: Performance scales well with file size, suggesting linear time complexity.")
        }
      }
      
      // Memory analysis
      val avgMemoryPerRow = results.map(r => r.memoryUsedMB / r.rows)
      val avgMemoryPerRowBytes = avgMemoryPerRow.sum / avgMemoryPerRow.length * 1024 * 1024
      
      writer.println()
      writer.println("### Memory Usage")
      writer.println()
      writer.println(f"- Average memory per row: ${avgMemoryPerRowBytes}%.2f bytes")
      
      // Max memory case
      val maxMemoryCase = results.maxBy(_.memoryUsedMB)
      writer.println(f"- Maximum memory usage: ${maxMemoryCase.memoryUsedMB}%.2f MB for ${maxMemoryCase.rows} rows × ${maxMemoryCase.columns} columns")
      
      if (maxMemoryCase.memoryUsedMB > 100) {
        writer.println("**Warning**: High memory usage detected for large files.")
      }
      
      // Throughput analysis by column count
      writer.println()
      writer.println("### Throughput by Column Count")
      writer.println()
      
      val throughputByColumns = config.columnSizes.map { cols =>
        val relevantResults = results.filter(_.columns == cols)
        if (relevantResults.nonEmpty) {
          val avgThroughput = relevantResults.map(_.rowsPerSecond).sum / relevantResults.size
          (cols, avgThroughput)
        } else {
          (cols, 0.0)
        }
      }
      
      writer.println("| Columns | Avg Rows/second |")
      writer.println("|---------|-----------------|")
      throughputByColumns.foreach { case (cols, throughput) =>
        writer.println(f"| $cols | $throughput%.0f |")
      }
      
      // Calculate throughput degradation with column count
      if (throughputByColumns.size >= 2) {
        val minCols = throughputByColumns.minBy(_._1)._1
        val maxCols = throughputByColumns.maxBy(_._1)._1
        val minColsThroughput = throughputByColumns.find(_._1 == minCols).get._2
        val maxColsThroughput = throughputByColumns.find(_._1 == maxCols).get._2
        
        if (minColsThroughput > 0 && maxColsThroughput > 0) {
          val degradation = (1 - maxColsThroughput / minColsThroughput) * 100
          writer.println()
          writer.println(f"Throughput degradation from $minCols to $maxCols columns: $degradation%.1f%%")
          
          if (degradation > 50) {
            writer.println("**Finding**: Significant throughput degradation with column count.")
          }
        }
      }
    } finally {
      writer.close()
    }
  }
  
  private def generateRecommendations(results: Seq[BenchmarkResult]): String = {
    val sb = new StringBuilder()
    sb.append("# Performance Recommendations\n\n")
    
    // Overall throughput
    val avgRowsPerSecond = results.map(_.rowsPerSecond).sum / results.size
    val maxRowsPerSecond = results.map(_.rowsPerSecond).max
    val minRowsPerSecond = results.filter(_.rowsPerSecond > 0).map(_.rowsPerSecond).min
    
    sb.append(f"## Overall Performance\n\n")
    sb.append(f"- Average parsing speed: ${avgRowsPerSecond}%.0f rows/second\n")
    sb.append(f"- Maximum parsing speed: ${maxRowsPerSecond}%.0f rows/second\n")
    sb.append(f"- Minimum parsing speed: ${minRowsPerSecond}%.0f rows/second\n")
    sb.append(f"- Max/min ratio: ${maxRowsPerSecond/minRowsPerSecond}%.1fx\n\n")
    
    // Memory usage
    val avgMemoryPerRow = results.map(r => r.memoryUsedMB * 1024 * 1024 / r.rows).sum / results.size
    sb.append("## Memory Usage\n\n")
    sb.append(f"- Average memory per row: ${avgMemoryPerRow}%.2f bytes\n")
    
    // Analyze scaling characteristics
    sb.append("\n## Scaling Characteristics\n\n")
    
    // Check row count scaling
    val smallRowsResults = results.filter(r => r.rows <= 1000).groupBy(_.columns)
    val largeRowsResults = results.filter(r => r.rows > 1000).groupBy(_.columns)
    
    if (smallRowsResults.nonEmpty && largeRowsResults.nonEmpty) {
      val commonColumnSizes = smallRowsResults.keySet.intersect(largeRowsResults.keySet)
      
      if (commonColumnSizes.nonEmpty) {
        val scalingData = commonColumnSizes.toSeq.sorted.map { cols =>
          val smallAvg = smallRowsResults(cols).map(_.rowsPerSecond).sum / smallRowsResults(cols).size
          val largeAvg = largeRowsResults(cols).map(_.rowsPerSecond).sum / largeRowsResults(cols).size
          val ratio = largeAvg / smallAvg
          (cols, smallAvg, largeAvg, ratio)
        }
        
        sb.append("Row count scaling (comparing ≤1000 rows vs >1000 rows):\n\n")
        sb.append("| Columns | Small Files (rows/s) | Large Files (rows/s) | Ratio |\n")
        sb.append("|---------|----------------------|----------------------|-------|\n")
        
        scalingData.foreach { case (cols, small, large, ratio) =>
          sb.append(f"| $cols | $small%.0f | $large%.0f | $ratio%.2f |\n")
        }
        
        val avgRatio = scalingData.map(_._4).sum / scalingData.size
        sb.append(f"\nAverage scaling ratio: $avgRatio%.2f\n")
        
        if (avgRatio < 0.7) {
          sb.append("\n**Finding**: Performance degrades with larger files. This suggests:\n\n")
          sb.append("1. The parser may have super-linear time complexity\n")
          sb.append("2. Memory pressure could be affecting performance\n")
          sb.append("3. Consider implementing chunked/streaming processing\n")
        } else {
          sb.append("\n**Finding**: Performance scales well with file size. The parser shows good linear scaling characteristics.\n")
        }
      }
    }
    
    // Check column count scaling
    val columnScalingData = results.groupBy(_.rows).filter(_._2.size >= 2).map { case (rows, rowResults) =>
      val minColResult = rowResults.minBy(_.columns)
      val maxColResult = rowResults.maxBy(_.columns)
      val ratio = maxColResult.rowsPerSecond / minColResult.rowsPerSecond
      (rows, minColResult.columns, maxColResult.columns, ratio)
    }.toSeq.sortBy(_._1)
    
    if (columnScalingData.nonEmpty) {
      sb.append("\n## Column Count Scaling\n\n")
      sb.append("| Rows | Min Columns | Max Columns | Performance Ratio |\n")
      sb.append("|------|-------------|-------------|-------------------|\n")
      
      columnScalingData.foreach { case (rows, minCols, maxCols, ratio) =>
        sb.append(f"| $rows | $minCols | $maxCols | $ratio%.2f |\n")
      }
      
      val avgColumnScaling = columnScalingData.map(_._4).sum / columnScalingData.size
      sb.append(f"\nAverage column scaling ratio: $avgColumnScaling%.2f\n")
      
      if (avgColumnScaling < 0.5) {
        sb.append("\n**Finding**: Performance degrades significantly with more columns. This suggests:\n\n")
        sb.append("1. The parser may have higher complexity with respect to column count\n")
        sb.append("2. Consider optimizing column parsing algorithm\n")
        sb.append("3. For many-column files, consider implementing column filtering before parsing\n")
      }
    }
    
    // Final recommendations
    sb.append("\n## Recommendations\n\n")
    
    // Calculate theoretical limits
    val maxFileSize = results.maxBy(r => r.rows * r.columns)
    val totalCells = maxFileSize.rows * maxFileSize.columns
    val theoreticalMaxRows = (totalCells * maxFileSize.rowsPerSecond / maxFileSize.rows).toInt
    
    sb.append(f"Based on the benchmark results, this parser can handle:\n\n")
    sb.append(f"- Up to ~$theoreticalMaxRows%.0f rows per second with ${maxFileSize.columns} columns\n")
    
    // Memory-based limits
    val bytesPerCell = results.map(r => r.memoryUsedMB * 1024 * 1024 / (r.rows * r.columns)).sum / results.size
    val availableMemory = Runtime.getRuntime().maxMemory() * 0.8 // 80% of max memory
    val maxCellsInMemory = availableMemory / bytesPerCell
    
    sb.append(f"- Approximately ${maxCellsInMemory.toInt}%,d total cells in memory\n\n")
    
    // Practical recommendations
    sb.append("### Practical Guidelines:\n\n")
    
    sb.append("1. **For large files** (>100,000 rows):\n")
    sb.append("   - Use streaming/chunked processing\n")
    sb.append("   - Consider implementing parallel parsing if order is not important\n")
    sb.append("   - Pre-filter columns if you don't need all of them\n\n")
    
    sb.append("2. **For many-column files** (>50 columns):\n")
    sb.append("   - Performance will degrade more than with many rows\n")
    sb.append("   - Consider vertical partitioning (processing subsets of columns)\n\n")
    
    sb.append("3. **For memory efficiency**:\n")
    sb.append("   - Implement lazily evaluated transformations\n")
    sb.append("   - Use specialized column-oriented storage for analytical workloads\n")
    
    sb.toString
  }
}

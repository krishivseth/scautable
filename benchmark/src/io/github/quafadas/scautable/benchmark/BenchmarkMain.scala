package io.github.quafadas.scautable.benchmark

/**
 * Entry point for running benchmarks
 */
object BenchmarkMain {
  def main(args: Array[String]): Unit = {
    println("Running CSV benchmarks...")
    
    // Delegate to the actual benchmark implementation
    CsvBenchmark.main(args)
  }
} 
# Scautable Performance Benchmarking

This module provides comprehensive benchmarking tools for evaluating the performance of the Scautable CSV parsing and data handling.

## Benchmarking Components

1. **Custom Benchmark Suite** (`CsvBenchmark.scala`)
   - Detailed performance analysis
   - Memory consumption tracking
   - Throughput metrics and scaling analysis
   - Generates reports and recommendations

2. **JMH Benchmarks** (`CsvJmhBenchmark.scala`)
   - Standard Java Microbenchmark Harness (JMH) suite
   - Precise timing measurements
   - Multiple benchmark modes
   - Comparison against alternative implementations

## Running Benchmarks

### Custom Benchmarks

```bash
./mill benchmark.runBenchmarks
```

These benchmarks:
- Generate CSV files of various sizes automatically
- Test with different row and column counts
- Measure parsing time and memory usage
- Generate detailed reports

### JMH Benchmarks

```bash
./mill benchmark.runJmhBenchmarks
```

These benchmarks:
- Provide standardized performance measurements
- Include warmup iterations for JVM stability
- Test different usage patterns (batch vs line-by-line)
- Compare against simple alternatives

## Benchmark Reports

Reports are saved to the `benchmark-reports` directory and include:

1. **CSV Results** - Raw data for further analysis
2. **Markdown Reports** - Formatted summary of results
3. **Performance Recommendations** - Suggestions based on results

## Benchmark Parameters

The benchmarks test:

- **File sizes**: Small (100 rows), Medium (1,000 rows), Large (10,000 rows), Very large (100,000 rows)
- **Column counts**: Few (5), Medium (10), Many (50), Very many (100)
- **Parsing patterns**: Whole-file, line-by-line, with processing

## Example Analysis

```
CSV Performance Benchmarks
=========================
| Rows      | Columns |  File Size |  Parse Time |    Rows/s |     MB/s |
| ---------- | ------- | ---------- | ---------- | ---------- | -------- |
|        100 |       5 |        3 KB |       11 ms |      9091 |    0.27 |
|       1000 |      10 |       58 KB |       42 ms |     23810 |    1.38 |
|      10000 |      10 |      561 KB |      276 ms |     36232 |    2.03 |
```

## Adding New Benchmarks

To add a new benchmark:

1. For custom benchmarks: Add new methods to `CsvBenchmark.scala`
2. For JMH benchmarks: Add new `@Benchmark` methods to `CsvJmhBenchmark.scala`

## Interpreting Results

The benchmark results help identify:

1. **Throughput limits** - Rows/second, MB/second
2. **Scaling characteristics** - How performance changes with file size
3. **Memory efficiency** - Memory usage per row/cell
4. **Bottlenecks** - Where performance degrades most significantly

This information is valuable for users who need to process large amounts of data and for ongoing optimization of the library. 
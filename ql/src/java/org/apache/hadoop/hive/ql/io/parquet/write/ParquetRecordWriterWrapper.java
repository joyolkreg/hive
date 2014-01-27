package org.apache.hadoop.hive.ql.io.parquet.write;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.FileSinkOperator.RecordWriter;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.util.Progressable;

import parquet.hadoop.ParquetOutputFormat;
import parquet.hadoop.util.ContextUtil;

public class ParquetRecordWriterWrapper implements RecordWriter {

  public static final Log LOG = LogFactory.getLog(ParquetRecordWriterWrapper.class);

  private final org.apache.hadoop.mapreduce.RecordWriter<Void, ArrayWritable> realWriter;
  private TaskAttemptContext taskContext;

  public ParquetRecordWriterWrapper(
      final OutputFormat<Void, ArrayWritable> realOutputFormat,
      final JobConf jobConf,
      final String name,
      final Progressable progress) throws IOException {
    try {
      // create a TaskInputOutputContext
      TaskAttemptID taskAttemptID = TaskAttemptID.forName(jobConf.get("mapred.task.id"));
      if (taskAttemptID == null) {
        taskAttemptID = new TaskAttemptID();
      }
      taskContext = ContextUtil.newTaskAttemptContext(jobConf, taskAttemptID);

      LOG.info("creating real writer to write at " + name);
      realWriter = (org.apache.hadoop.mapreduce.RecordWriter<Void, ArrayWritable>) ((ParquetOutputFormat) realOutputFormat)
          .getRecordWriter(taskContext, new Path(name));
      LOG.info("real writer: " + realWriter);
    } catch (final InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void close(final boolean abort) throws IOException {
    try {
      realWriter.close(null);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void write(final Writable w) throws IOException {
    try {
      realWriter.write(null, (ArrayWritable) w);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }
  }
}

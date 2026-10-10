package buildtool

import java.io.{BufferedReader, InputStream, InputStreamReader, OutputStream}
import java.nio.charset.StandardCharsets.UTF_8

/** Bridges a forked tool's output to the current build task's stream. */
object StreamPump:
  def copy(source: InputStream, destination: OutputStream): Unit =
    val reader = new BufferedReader(new InputStreamReader(source, UTF_8))
    try
      var line = reader.readLine()
      while line != null do
        destination.write((line.trim + "\n").getBytes(UTF_8))
        line = reader.readLine()
      destination.flush()
    finally
      reader.close()
      destination.close()
